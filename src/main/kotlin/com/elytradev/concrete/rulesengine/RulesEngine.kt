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
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.event.FMLServerStartingEvent
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.*
import java.util.function.Consumer


abstract class RulesEngine<X: EvaluationContext> {
    abstract fun getDomainPredicates(): Map<Char, (String) -> Either<Predicate<X>, String>>
    open fun unprefixedPredicate(s: String): Either<out Predicate<X>, String> =
            Either.right("Unprefixed predicates unsupported: \"$s\"")

    abstract fun getEffectSlots(): Set<Int>
    abstract fun effect(s: String): Either<Iterable<out Effect<X>>, String>

    open fun interestingNumberList(): Iterable<String> = listOf()
    open fun genInterestingNumbers(from: X): DoubleArray = doubleArrayOf()

    open fun genDefaultRules(): String = ""


    val varnameToIndex by lazy { interestingNumberList().withIndex().map { it.value to it.index }.toMap() }

    private var rules: Rules<X>? = null
    private var rulesFile: File? = null


    /**
     * Loads a specified rules file and save it for later reloading
     *
     * @return null if success, or else String error message
     */
    fun loadRulesFile(file: File, logger: Logger): String? {
        rulesFile = file
        val err = reloadRules()
        if (err == null) {
            logger.info("${file.name}: Loaded ${count()} rules.")
        } else {
            logger.error("${file.name}: $err")
        }
        return err
    }

    /**
     * Registers a server command with the provided string to reload rules file.
     */
    fun registerReloadCommand(e: FMLServerStartingEvent, commandStr: String) {
        e.registerServerCommand(object: CommandBase() {
            override fun getName() = commandStr
            override fun getUsage(sender: ICommandSender?) = "/$commandStr"
            override fun execute(server: MinecraftServer?, sender: ICommandSender?, args: Array<out String>?) {
                sender?.sendMessage(TextComponentString(reloadRules() ?: "Loaded ${count()} rules."))
            }
        })
    }

    /**
     * Reloads a rules file previously loaded by loadRulesFile
     *
     * @return null if success, or else String error message
     */
    fun reloadRules(): String? {
        return loadOrErr(parseRules(rulesFile ?: return "No rules file has been set to load."))
    }

    /**
     * Loads rules from specified raw string, bypassing file i/o and default rules generation
     *
     * @return null if success, or else String error message
     */
    fun loadRulesRaw(inputRules: String): String? = loadOrErr(parseRuleLines(inputRules.lineSequence()))

    /**
     * @return Number of loaded rules
     */
    fun count(): Int = rules?.count()?:0


    private fun loadOrErr(rulesie: Either<Rules<X>, String>): String? = rulesie.mapLeft { rules = it }.rightNullable

    private fun parseRules(fileInput: File): Either<Rules<X>, String> {
        if (!fileInput.exists())
            fileInput.writeText(genDefaultRules())

        return fileInput.useLines(block=parseRuleLines)
    }

    private val parseRuleLines = fun(input: Sequence<String>): Either<Rules<X>, String> {
        val rulesAggregator = RulesAggregator(this)
        val ctx = ParseContext(this)
        val lines = input.withIndex().iterator()

        while (lines.hasNext()) {
            val lineIndexed = lines.next()
            val line = Scanner(fluffSpecialDelimiters(lineIndexed.value))
            if (!line.hasNext(commentPattern) && line.hasNext()) {
                val lineNumber = lineIndexed.index + 1

                if (line.hasNextInt()) {
                    rulesAggregator.add(ctx, line.nextInt(), line)
                            ?.let { return Either.right(errMessage(lineNumber, it)) }
                } else {
                    if (line.hasNext(tagnamePattern)) {
                        rulesAggregator.addTag(ctx, line.next(), lineNumber, line, lines)
                                ?.let { return Either.right(it) }
                    } else {
                        return Either.right(errMessage(lineNumber, "illegal tag name: ${line.next()} (tag name must be purely alphanumeric)"))
                    }
                }
            }
        }

        ctx.predicateCache.values.filterIsInstance<TagPredicate<X>>().forEach({
            val tag = ctx.tags[it.name] ?: return Either.right("Tag referenced but never defined: \"${it.name}\"")
            it.predicateses = tag
        })

        return Either.left(rulesAggregator.getRules())
    }

    private fun genPredicate(ctx: ParseContext<X>, s: String): Either<out Predicate<X>, String> = when (s[0]) {
        '!' -> {
            if (s.length < 2) Either.right("Empty subpredicate")
            else predicate(ctx, s.substring(1)).mapLeft{NegatedPredicate<X>(it)}
        }
        '%' -> {
            if (s.length > 1) Either.left(TagPredicate<X>(s.substring(1)))
            else Either.right("Empty tagname")
        }
        '(' -> {
            mathPredicate(this, if (s.endsWith(')')) s.substring(1, s.length - 1) else s.substring(1))
        }
        else -> {
            getDomainPredicates()[s[0]]
                    ?.let { it(s.substring(1)) }
                    ?: unprefixedPredicate(s)
        }
    }

    internal fun predicate(ctx: ParseContext<X>, s: String): Either<out Predicate<X>, String> = ctx.predicateCache[s]
            ?.let{Either.left<Predicate<X>, String>(it)}
            ?: genPredicate(ctx, s).mapLeft{ctx.predicateCache[s] = it; it}



    private fun evaluate(substrate: X): Either<EffectBuffer<X>, String> {
        substrate.interestingNumbers = genInterestingNumbers(substrate)
        val buf = EffectBuffer<X>(getEffectSlots())
        try {
            rules?.forEach{
                if (buf.caresAbout(it.effects) && it.predicates.resolve(substrate)) {
                    buf.update(it.effects)
                    if (buf.full()) return Either.left(buf)
                }
            }
        } catch (e: Exception) {
            return Either.right("Error in rules engine: ${e.message}")
        }
        return Either.left(buf)
    }

    /**
     * act applies your currently loaded ruleset to a context.
     * Semantically, the ruleset is evaluated independently for each effect slot.  The highest priority rule providing
     * an effect with a given slot will take precedence.  Predicate and tag caching behavior is unspecified and
     * predicates should not have side effects.
     * All predicates are evaluated before any effects are executed.
     * Should only be used once per context, after you are done populating the context.
     * You can keep your context afterwards for any data populated by Effects.
     */
    fun act(substrate: X): String? = evaluate(substrate).mapLeft { it.accept(substrate) }.rightNullable
}


abstract class EvaluationContext {
    internal val tagCache = mutableMapOf<String, Boolean>()
    internal val tagRecursionStack = mutableSetOf<String>()
    internal var interestingNumbers = doubleArrayOf()
}


interface Effect<X>: Consumer<X> {
    fun getSlot(): Int
}


interface Predicate<X> {
    fun resolve(ctx: X): Boolean
}





