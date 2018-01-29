package capitalthree.ruleengine

import com.elytradev.concrete.common.Either
import java.io.File
import java.util.*
import java.util.regex.Pattern

private val commentPattern = Pattern.compile("#.*")
private val tagnamePattern = Pattern.compile("[A-Za-z][A-Za-z0-9]+")
private val specialDelimiters = Regex("[\\[\\],]") // these characters get automatically surrounded with spaces
private fun fluffSpecialDelimiters(s: String): String = s.replace(specialDelimiters,
        {match -> " ${match.value} "}
)

internal class ParseContext<X: EvaluationContext>(val engine: RulesEngine<X>) {
    val tags = mutableMapOf<String, List<Predicates<X>>>()
    val predicateCache = mutableMapOf<String, Predicate<X>>()
    fun predicate(s: String) = engine.predicate(this, s)
}

abstract class RulesEngine<X: EvaluationContext> {
    abstract fun getDomainPredicates(): Map<Char, (String) -> Either<Predicate<X>, String>>
    open fun unprefixedPredicate(s: String): Either<out Predicate<X>, String> =
            Either.right("Unprefixed predicates unsupported: \"$s\"")

    abstract fun getEffectSlots(): Set<Int>
    abstract fun effect(s: String): Either<Iterable<out Effect<X>>, String>

    open fun interestingNumberList(): Iterable<String> = listOf()
    open fun genInterestingNumbers(from: X): DoubleArray = doubleArrayOf()
    val varnameToIndex by lazy { interestingNumberList().withIndex().map { it.value to it.index }.toMap() }


    private var rules: Rules<X>? = null
    fun loadRules(fileInput: File, defaultRules: () -> String) =
        parseRules(fileInput, defaultRules).mapLeft { rules = it }.rightNullable
    fun count(): Int = rules?.count()?:0

    private fun parseRules(fileInput: File, defaultRules: () -> String): Either<Rules<X>, String> {
        if (!fileInput.exists())
            fileInput.writeText(defaultRules())

        val rules = RulesAggregator(this)
        val ctx = ParseContext<X>(this)

        fileInput.useLines {
            val lines = it.withIndex().iterator()

            while (lines.hasNext()) {
                val lineIndexed = lines.next()
                val line = Scanner(fluffSpecialDelimiters(lineIndexed.value))
                if (!line.hasNext(commentPattern) && line.hasNext()) {
                    val lineNumber = lineIndexed.index + 1

                    if (line.hasNextInt()) {
                        rules.add(ctx, line.nextInt(), line)
                                ?.let { return Either.right(errMessage(lineNumber, it)) }
                    } else {
                        if (line.hasNext(tagnamePattern)) {
                            rules.addTag(ctx, line.next(), lineNumber, line, lines)
                                    ?.let { return Either.right(it) }
                        } else {
                            return Either.right(errMessage(lineNumber, "illegal tag name: ${line.next()} (tag name must be purely alphanumeric)"))
                        }
                    }
                }
            }
        }

        ctx.predicateCache.values.filterIsInstance<TagPredicate<X>>().forEach({
            val tag = ctx.tags[it.name] ?: return Either.right("Tag referenced but never defined: \"${it.name}\"")
            it.predicateses = tag
        })

        return Either.left(rules.getRules())
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
            return Either.right("Error in Lingering Loot rules engine: ${e.message}")
        }
        return Either.left(buf)
    }

    fun act(substrate: X): String? = evaluate(substrate).mapLeft { it.accept(substrate) }.rightNullable
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
                    ctx.tags.put(tagname, predicateses)
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


private val arrowPattern = Pattern.compile("->")
internal class RulesLevel<X: EvaluationContext>(private val engine: RulesEngine<X>) {
    val rules = mutableListOf<Rule<X>>()

    fun add(ctx: ParseContext<X>, s: Scanner): String? {
        val rule = Rule<X>()
        rules.add(rule)

        while (!s.hasNext(arrowPattern)) {
            if (!s.hasNext()) return "No arrow (\"->\") in rule line"
            rule.addPredicate(ctx, s.next()) ?.let{return it}
        }
        s.next()

        while (s.hasNext())
            engine.effect(s.next()).mapLeft { rule.effects.addAll(it) }
                    .rightNullable?.let { return it }

        if (rule.effects.isEmpty()) return "No effects for rule"

        return null
    }
}

fun errMessage(ln: Int, m: String) = "Error on line $ln: $m"


internal typealias Rules<X> = Iterable<Rule<X>>

internal class Rule<X: EvaluationContext> {
    val predicates = Predicates<X>()
    val effects = mutableListOf<Effect<X>>()
    fun addPredicate(ctx: ParseContext<X>, s: String): String? = predicates.add(ctx, s)
}

abstract class EvaluationContext {
    internal val tagCache = mutableMapOf<String, Boolean>()
    internal val tagRecursionStack = mutableSetOf<String>()
    internal var interestingNumbers = doubleArrayOf()
}








