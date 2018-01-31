package com.elytradev.concrete.rulesengine

import com.elytradev.concrete.common.Either
import java.util.function.Function
import java.util.function.Predicate

abstract class RulesEngineJava<X : EvaluationContext> : RulesEngine<X>() {
    override fun getDomainPredicates(): Map<Char, Function1<String, Either<Predicate<X>, String>>> {
        return jDomainPredicates().mapValues { it.value::apply } // yes intellij very suspicious
    }

    abstract fun jDomainPredicates(): Map<Char, Function<String, Either<Predicate<X>, String>>>
}
