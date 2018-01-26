package capitalthree.ruleengine

import com.elytradev.concrete.common.Either

fun <X: EvaluationContext> mathPredicate(engine: RulesEngine<X>, s: String): Either<Predicate<X>, String> {
    engine.interestingNumberList()

    return Either.right("butts")
}



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
