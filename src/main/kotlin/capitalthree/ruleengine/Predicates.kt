package capitalthree.ruleengine

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

    fun resolve(ctx: X) = predicates.all{it.resolve(ctx)}
}

interface Predicate<X> {
    fun resolve(ctx: X): Boolean
}

private val DUMMY_TAG = listOf<Nothing>()
class TagPredicate<X: EvaluationContext>(val name: String): Predicate<X> {
    internal var predicateses: List<Predicates<X>> = DUMMY_TAG // should always be reassigned

    override fun resolve(ctx: X): Boolean = ctx.tagCache.computeIfAbsent(name, {
        if (ctx.tagRecursionStack.contains(name)) throw Exception("Tag evaluation aborted due to circular reference: " +
                ctx.tagRecursionStack.joinToString(", "))
        ctx.tagRecursionStack += name
        val tagResult = predicateses.any({it.resolve(ctx)})
        ctx.tagRecursionStack -= name
        tagResult
    })
}

class NegatedPredicate<X>(val neg: Predicate<X>): Predicate<X> {
    override fun resolve(ctx: X) = !neg.resolve(ctx)
}

