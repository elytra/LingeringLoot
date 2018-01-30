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
import java.util.function.Predicate


internal class Predicates<X: EvaluationContext> {
    val predicates = mutableListOf<Predicate<X>>()

    fun add(ctx: ParseContext<X>, s: String): String? = ctx.predicate(s).map(
            {
                predicates.add(it)
                null
            },
            {it}
    )

    fun isEmpty() = predicates.isEmpty()

    fun test(ctx: X) = predicates.all{it.test(ctx)}
}


private val DUMMY_TAG = listOf<Nothing>()
internal class TagPredicate<X: EvaluationContext>(val name: String): Predicate<X> {
    internal var predicateses: List<Predicates<X>> = DUMMY_TAG // should always be reassigned

    override fun test(ctx: X): Boolean = ctx.tagCache.computeIfAbsent(name, {
        if (ctx.tagRecursionStack.contains(name)) throw Exception("Tag evaluation aborted due to circular reference: " +
                ctx.tagRecursionStack.joinToString(", "))
        ctx.tagRecursionStack += name
        val tagResult = predicateses.any({it.test(ctx)})
        ctx.tagRecursionStack -= name
        tagResult
    })
}

internal class NegatedPredicate<X>(val neg: Predicate<X>): Predicate<X> {
    override fun test(ctx: X) = !neg.test(ctx)
}

internal fun <X: EvaluationContext> mathPredicate(engine: RulesEngine<X>, s: String): Either<Predicate<X>, String> {
    compsByPattern.forEach {
        val matcher = it.first.matcher(s)
        if (matcher.matches()) {
            val varName = matcher.group(1)
            val variable = engine.varnameToIndex[varName] ?: return Either.right("Unknown variable: \"$varName\"")
            val constName = matcher.group(2)
            val constant = constName.toDoubleOrNull() ?: return Either.right("Invalid double: \"$constName\"")
            return Either.left(it.second.predFrom(variable, constant))
        }
    }

    return Either.right("No comparison operator found in math: \"$s\"")
}

private enum class NumericComparison(val symbol: String, val inequal: Boolean, val greater: Boolean, val equal: Boolean) {
    GE(">=", true, true, true),
    LE("<=", true, false, true),
    NE("!=", false, false, false),
    G(">", true, true, false),
    L("<", true, false, false),
    E("=", false, false, true);

    fun <X: EvaluationContext>predFrom(variable: Int, constant: Double) =
            if (inequal)
                VarComparePredicate<X>(variable, constant, greater, equal)
            else
                VarEqualPredicate<X>(variable, constant, !equal)
}

private val compsByPattern = NumericComparison.values().map { "(.+)${it.symbol}(.+)".toPattern() to it }


private class VarEqualPredicate<X: EvaluationContext>(val variable: Int, val constant: Double, val not: Boolean): Predicate<X> {
    override fun test(ctx: X) = (constant == ctx.interestingNumbers[variable]) != not
}

private class VarComparePredicate<X: EvaluationContext>(val variable: Int, val constant: Double, val greater: Boolean, val equal: Boolean): Predicate<X> {
    override fun test(ctx: X): Boolean {
        val value = ctx.interestingNumbers[variable]
        return (equal && (value == constant))
                || (greater == (value > constant))
    }
}
