/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017:
 * 	Una Thompson (unascribed),
 * 	Isaac Ellingson (Falkreon),
 * 	Jamie Mansfield (jamierocks),
 * 	and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.elytradev.concrete.rulesengine


import com.elytradev.concrete.common.Either
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerStartingEvent

import java.util.function.Predicate


private val ADDNUM_SLOT_ID = 0

internal class ExampleRulesEngine : RulesEngine<ExampleRulesEngine.ExampleContext>() {
    /**
     * domainPredicates should return a map of prefix characters to the corresponding predicate parsers for your
     * predicates.  Predicate parsers take the remainder of the string (after removing the prefix char) and return
     * either a predicate, or a string error message.
     *
     * @return the map
     */
    override fun getDomainPredicates() = domainPredicates
    private val domainPredicates = mapOf<Char, (String) -> Either<Predicate<ExampleContext>, String>>(
        '/' to {s: String ->
//     This is an example of a predicate parser.  Note that the predicate parser receives a string with the prefix
//     character already removed.
    s.toIntOrNull()
                ?.let { Either.left<Predicate<ExampleContext>, String>(DivisibilityPredicate(it)) }
                ?:  Either.right("Invalid integer: \"$s\"")
        }
    )


    /**
     * This is the structure of the default predicate parser, which, if defined, is used for any predicates that do not
     * have a recognized prefix character.  Note the type signature is the same as individual predicate parsers.
     *
     * @param str predicate string
     * @return Either predicate, or string error
     */
    override fun defaultPredicate(str: String): Either<Predicate<ExampleContext>, String> =
        Either.right("This rules language doesn't support un-prefixed predicates, but it can if you change this function!")


    /**
     * Set of slot ids for effects.  Whenever rules are evaluated, only the first matching effect will be used for each
     * of these slots.
     *
     * @return set
     */
    override fun getEffectSlots() = effectSlots
    private val effectSlots = setOf(ADDNUM_SLOT_ID)

    /**
     * Parser for effects.  Note that you are responsible for the entire effect parsing, so if you want a prefix-based
     * approach that's on you.  The parser can return multiple effects, the most obvious example being a "finalize"
     * effect to fill all remaining effect slots with no-ops (as lingering loot has).  The parser can return no effects,
     * but that probably wouldn't make sense.
     *
     * @param s string to parse for effects
     * @return Either any number of effects, or a String error
     */
    override fun parseEffect(s: String): Either<Iterable<Effect<ExampleContext>>, String> = s.toIntOrNull()
            ?.let{Either.left<Iterable<Effect<ExampleContext>>, String>(setOf(AddNumberEffect(Integer.parseInt(s))))}
            ?:Either.right("Invalid integer: \"$s\"")

    /**
     * The evaluation context is the type parameter for the abstract rules engine as well as all effects and predicates.
     * Populate it with any references that your predicates will need to evaluate on, and that your effects will need to
     * do their thing.  A given instance should only be used once.  Write in the context, pass it to act, and then read
     * out any results you need, but do not call act twice with the same instance.
     * Note as well that all predicate evaluation happens before rules are applied, so it is ok to predicate on the same
     * state that your effects affect.
     */
    internal class ExampleContext(var theInt: Int) : EvaluationContext()

    /**
     * Our predicate, which tests divisibility by a given number
     */
    internal class DivisibilityPredicate(private val test: Int) : Predicate<ExampleContext> {
        override fun test(ctx: ExampleContext) = ctx.theInt % test == 0
    }

    /**
     * Our effect, which adds a given number
     */
    internal class AddNumberEffect(val i: Int) : Effect<ExampleContext> {
        /**
         * @return 0 to match the slot id provided in getEffectSlots above
         */
        override fun getSlot() = ADDNUM_SLOT_ID

        override fun accept(ctx: ExampleContext) {ctx.theInt += i}
    }

    /**
     * @return A list of any variable names for interesting numbers, which can be referenced in the inequality
     * comparison operator.  Any collection type is allowed but iteration order must correspond to the interesting
     * numbers array.
     */
    override fun interestingNumberList() = interestingNumbers
    private val interestingNumbers = setOf("it")

    /**
     * @param from some context
     * @return populated array of interesting numbers, corresponding positionally to the variable names in
     * interestingNumberList.
     */
    override fun genInterestingNumbers(from: ExampleRulesEngine.ExampleContext): DoubleArray {
        return doubleArrayOf(from.theInt.toDouble())
    }
}

internal class ExampleMod {
    val engine = ExampleRulesEngine()

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) =
        // This will save the rules file path, do an initial load of the rules, and log the result
        engine.loadRulesFile(
                event.modConfigurationDirectory.resolve("simplemath.rules"),
                event.modLog
        )

    @Mod.EventHandler
    fun start(event: FMLServerStartingEvent) =
        // This will generate and register a simple server command that reloads our rules file and sends the resulting success/error message to whoever ran the command
        engine.registerReloadCommand(event, "mathreload")

    fun superFakeEventHandler() {
        val startingInt = 3
        // build and populate context with input data
        val ctx = ExampleRulesEngine.ExampleContext(startingInt)
        // resolves enough predicates to fully determine what effects should be run, then runs them
        engine.act(ctx)
        // if desired, retrieve any information from the used context generated by your Effect implementations
        val postModification = ctx.theInt
    }
}