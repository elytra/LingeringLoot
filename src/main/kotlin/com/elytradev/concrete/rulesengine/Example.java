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

package com.elytradev.concrete.rulesengine;

import com.elytradev.concrete.common.Either;
import com.google.common.collect.ImmutableMap;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

class ExampleRulesEngineJava extends RulesEngineJava<ExampleRulesEngineJava.ExampleContext> {
    static final int ADDNUM_SLOT_ID = 0;

    /**
     * jDomainPredicates should return a map of prefix characters to the corresponding predicate parsers for your
     * predicates.  Predicate parsers take the remainder of the string (after removing the prefix char) and return
     * either a predicate, or a string error message.
     *
     * @return the map
     */
    @NotNull
    @Override
    public Map<Character, Function<String, Either<Predicate<ExampleContext>, String>>> jDomainPredicates() {
        return ImmutableMap.of('/', ExampleRulesEngineJava::parseDivisibility);
    }

    /**
     * This is an example of a predicate parser.  Note that the predicate parser receives a string with the prefix
     * character already removed.
     *
     * @param str representation of an integer
     * @return Either predicate to test divisibility by that integer, or string error
     */
    private static Either<Predicate<ExampleContext>, String> parseDivisibility(String str) {
        try {
            return Either.left(new DivisibilityPredicate(Integer.parseInt(str)));
        } catch(NumberFormatException e) {
            return Either.right("Invalid integer: " + str);
        }
    }

    /**
     * This is the structure of the default predicate parser, which, if defined, is used for any predicates that do not
     * have a recognized prefix character.  Note the type signature is the same as individual predicate parsers.
     *
     * @param str predicate string
     * @return Either predicate, or string error
     */
    @NotNull
    @Override
    public Either<Predicate<ExampleContext>, String> defaultPredicate(String str) {
        return Either.right("This rules language doesn't support un-prefixed predicates, but it can if you change this function!");
    }

    /**
     * Set of slot ids for effects.  Whenever rules are evaluated, only the first matching effect will be used for each
     * of these slots.
     *
     * @return set
     */
    @NotNull
    @Override
    public Set<Integer> getEffectSlots() {
        return Collections.singleton(ADDNUM_SLOT_ID);
    }

    /**
     * Parser for effects.  Note that you are responsible for the entire effect parsing, so if you want a prefix-based
     * approach that's on you.  The parser can return multiple effects, the most obvious example being a "finalize"
     * effect to fill all remaining effect slots with no-ops (as lingering loot has).  The parser can return no effects,
     * but that probably wouldn't make sense.
     *
     * @param s string to parse for effects
     * @return Either any number of effects, or a String error
     */
    @NotNull
    @Override
    public Either<Iterable<Effect<ExampleContext>>, String> parseEffect(@NotNull String s) {
        try {
            return Either.left(Collections.singleton(new AddNumberEffect(Integer.parseInt(s))));
        } catch (NumberFormatException e) {
            return Either.right("Invalid integer: \"" + s + "\"");
        }
    }

    /**
     * The evaluation context is the type parameter for the abstract rules engine as well as all effects and predicates.
     * Populate it with any references that your predicates will need to evaluate on, and that your effects will need to
     * do their thing.  A given instance should only be used once.  Write in the context, pass it to act, and then read
     * out any results you need, but do not call act twice with the same instance.
     * Note as well that all predicate evaluation happens before rules are applied, so it is ok to predicate on the same
     * state that your effects affect.
     */
    static class ExampleContext extends EvaluationContext {
        public int theInt;
        ExampleContext(int theInt) {this.theInt = theInt;}
    }

    /**
     * Our predicate, which tests divisibility by a given number
     */
    static class DivisibilityPredicate implements Predicate<ExampleContext> {
        private final int test;
        DivisibilityPredicate(int test) {this.test = test;}

        @Override
        public boolean test(ExampleContext ctx) {
            return ctx.theInt % test == 0;
        }
    }

    /**
     * Our effect, which adds a given number
     */
    static class AddNumberEffect implements Effect<ExampleContext> {
        final int i;
        public AddNumberEffect(int i) {this.i = i;}

        /**
         * @return 0 to match the slot id provided in getEffectSlots above
         */
        @Override
        public int getSlot() {return ADDNUM_SLOT_ID;}

        @Override
        public void accept(ExampleContext ctx) {
            ctx.theInt += i;
        }
    }

    /**
     * @return A list of any variable names for interesting numbers, which can be referenced in the inequality
     * comparison operator.  Any collection type is allowed but iteration order must correspond to the interesting
     * numbers array.
     */
    @NotNull
    @Override
    public Iterable<String> interestingNumberList() {
        return Collections.singleton("it");
    }

    /**
     * @param from some context
     * @return populated array of interesting numbers, corresponding positionally to the variable names in
     * interestingNumberList.
     */
    @NotNull
    @Override
    public double[] genInterestingNumbers(@NotNull ExampleRulesEngineJava.ExampleContext from) {
        return new double[]{from.theInt};
    }
}

class ExampleModJava {
    ExampleRulesEngineJava engine;

    @Mod.EventHandler
    void preInit(FMLPreInitializationEvent event) {
        engine = new ExampleRulesEngineJava();
        // This will save the rules file path, do an initial load of the rules, and log the result
        engine.loadRulesFile(
            event.getModConfigurationDirectory().toPath().resolve("simplemath.rules").toFile(),
            event.getModLog()
        );
    }

    @Mod.EventHandler
    void start(FMLServerStartingEvent event) {
        // This will generate and register a simple server command that reloads our rules file and sends the resulting success/error message to whoever ran the command
        engine.registerReloadCommand(event, "mathreload");
    }

    void superFakeEventHandler() {
        int startingInt = 3;
        // build and populate context with input data
        ExampleRulesEngineJava.ExampleContext ctx = new ExampleRulesEngineJava.ExampleContext(startingInt);
        // resolves enough predicates to fully determine what effects should be run, then runs them
        engine.act(ctx);
        // if desired, retrieve any information from the used context generated by your Effect implementations
        int postModification = ctx.theInt;
    }
}
