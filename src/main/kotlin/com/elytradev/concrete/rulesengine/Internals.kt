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

import java.util.*
import java.util.function.Predicate
import java.util.regex.Pattern


internal typealias Rules<X> = Iterable<Rule<X>>
internal val commentPattern = Pattern.compile("#.*")
private val specialDelimiters = Regex("[\\[\\],]") // these characters get automatically surrounded with spaces
internal fun fluffSpecialDelimiters(s: String): String = s.replace(specialDelimiters, {match -> " ${match.value} "})
internal val tagnamePattern = Pattern.compile("[A-Za-z][A-Za-z0-9]+")
internal fun errMessage(ln: Int, m: String) = "Error on line $ln: $m"



internal class Rule<X: EvaluationContext> {
    val predicates = Predicates<X>()
    val effects = mutableListOf<Effect<X>>()
}



internal class ParseContext<X: EvaluationContext>(val engine: RulesEngine<X>) {
    val tags = mutableMapOf<String, List<Predicates<X>>>()
    val predicateCache = mutableMapOf<String, Predicate<X>>()
    fun predicate(s: String) = engine.predicate(this, s)
}



internal class EffectBuffer<X>(private val expectedSlots: Set<Int>): Effect<X> {
    override fun getSlot() = Integer.MIN_VALUE
    private val effects = mutableMapOf<Int, Effect<X>>()

    fun caresAbout(newEffects: List<Effect<X>>) = newEffects.any{it.getSlot() in expectedSlots && it.getSlot() !in effects}

    fun update(newEffects: List<Effect<X>>) = newEffects.forEach{
        if (it.getSlot() in expectedSlots)
            effects.putIfAbsent(it.getSlot(), it)
    }

    fun full() = expectedSlots.all{it in effects}

    override fun accept(target: X) = effects.values.forEach{it.accept(target)}
}



private val arrowPattern = Pattern.compile("->")
internal class RulesLevel<X: EvaluationContext>(private val engine: RulesEngine<X>) {
    val rules = mutableListOf<Rule<X>>()

    fun add(ctx: ParseContext<X>, s: Scanner): String? {
        val rule = Rule<X>()
        rules.add(rule)

        while (!s.hasNext(arrowPattern)) {
            if (!s.hasNext()) return "No arrow (\"->\") in rule line"
            rule.predicates.add(ctx, s.next()) ?.let{return it}
        }
        s.next()

        while (s.hasNext())
            engine.effect(s.next()).mapLeft { rule.effects.addAll(it) }
                    .rightNullable?.let { return it }

        if (rule.effects.isEmpty()) return "No effects for rule"

        return null
    }
}






internal class RulesAggregator<X: EvaluationContext>(private val engine: RulesEngine<X>) {
    val levels = mutableMapOf<Int, RulesLevel<X>>()

    fun add(ctx: ParseContext<X>, level: Int, rule: Scanner): String? =
            levels.computeIfAbsent(level, {RulesLevel(engine)})
                    .add(ctx, rule)

    fun addTag(ctx: ParseContext<X>, tagname: String, ln_: Int, line_: Scanner, lines: Iterator<IndexedValue<String>>): String? {
        if (!line_.hasNext() || line_.next() != "[") return errMessage(ln_, "Expected [ after tag name")
        if (ctx.tags.contains(tagname)) return errMessage(ln_, "Duplicate tag \"$tagname\"")
        var ln = ln_
        var line = line_
        val predicateses = mutableListOf(Predicates<X>())

        while (true) {
            while (!line.hasNext()) {
                if (!lines.hasNext()) return errMessage(ln, "Unexpected EOF, unclosed block for tag \"$tagname\"")
                val lineIndexed = lines.next()
                ln = lineIndexed.index + 1
                line = Scanner(fluffSpecialDelimiters(lineIndexed.value))
                if (!predicateses.last().isEmpty()) predicateses.add(Predicates<X>())
            }

            val token = line.next()
            when (token) {
                "," -> {
                    if (!predicateses.last().isEmpty()) predicateses.add(Predicates<X>())
                }
                "]" -> {
                    if (line.hasNext()) return errMessage(ln, "\"]\" must appear at the end of a line")
                    if (predicateses.last().isEmpty()) predicateses.removeAt(predicateses.size-1)
                    if (predicateses.isEmpty()) return errMessage(ln, "Tag \"$tagname\" has no predicates")
                    ctx.tags[tagname] = predicateses
                    return null
                }
                else -> {
                    predicateses.last().add(ctx, token) ?.let{return errMessage(ln, it)}
                }
            }
        }
    }


    fun getRules() = levels.entries.sortedByDescending{it.key}.flatMap{it.value.rules}
}
