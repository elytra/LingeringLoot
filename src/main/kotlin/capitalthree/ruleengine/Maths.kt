package capitalthree.ruleengine

import com.elytradev.concrete.common.Either

fun <X: EvaluationContext> mathPredicate(engine: RulesEngine<X>, s: String): Either<Predicate<X>, String> {
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

enum class NumericComparison(val symbol: String, val inequal: Boolean, val greater: Boolean, val equal: Boolean) {
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

val compsByPattern = NumericComparison.values().map { "(.+)${it.symbol}(.+)".toPattern() to it }


class VarEqualPredicate<X: EvaluationContext>(val variable: Int, val constant: Double, val not: Boolean): Predicate<X> {
    override fun resolve(ctx: X) = (constant == ctx.interestingNumbers[variable]) != not
}

class VarComparePredicate<X: EvaluationContext>(val variable: Int, val constant: Double, val greater: Boolean, val equal: Boolean): Predicate<X> {
    override fun resolve(ctx: X): Boolean {
        val value = ctx.interestingNumbers[variable]
        return (equal && (value == constant))
            || (greater == (value > constant))
    }
}
