package lingerloot.ruleengine

import com.elytradev.concrete.common.Either
import java.io.*
import java.util.*
import java.util.regex.Pattern

val defaultRules = """
# Hi!  I'm Nikky!  capitalthree kidnapped me and they won't let me go until I
# explain this stupid new config format to you.  capitalthree threw out the nice
# simple config format you're used to and replaced it with this confusing mess,
# so let's just get this over with so I can go back to working on modpacker tools.
#
# The first thing you need to know about is predicates.  If you don't know what
# those are, you shouldn't even be... eeep!  that tickles!  okay, okay, I'll be
# nice!  Predicates are the fundamental tests you can filter on in your rules.
#
# Predicates:
#  itemname
#   - This can be a vanilla itemname, or modname:itemname, itemname@damage, or both
#  ~oredictName
#  %tagname
#  @cause
#  &class
#  :modid
#
# Cause refers to the conditions that caused the item to drop, as in the classic
# lingering loot config. They are:
#  @playerDrop
#  @playerHarvest (playerLoot | playerMine)
#  @playerLoot
#  @playerMine
#  @mobDrop
#  @playerToss
#  @playerCaused (playerToss | playerHarvest | playerDrop)
#  @creativeGive
#
# Some of the causes are sets of certain other causes, provided for convenience.
# As if there's anything convenient about-  eeeep!  Okay, okay, I'll read it!
#
# Class refers to certain classes of items, which correspond to actual subclasses
# of Item in the game.
#  &armor
#  &block
#  &food
#  &tool
#
# Tag:
# tagname {predicateA  predB  predC
#    predD  predE}
# # tagname = (A & B & C) | (D & E)
# Note that commas can be used in place of (or in addition to) newlines because Falkreon.
#
#
# Rule:
# priority     predicate    (predicate...)   ->   result   (result...)
#
#
# Results:
# timer(t)     set despawn time to t seconds
# notimer      leave default despawn time and prevent matching a lower priority timer directive
# volatile     item will trigger special behavior when it would despawn
# novolatile   not volatile (to override a lower priority volatile result)
# convert(i)   transform into same-sized stack of item i (note that any predicate matching is still based on the original item)
# noconvert    prevent a lower priority convert result
#
#
#
#
# contradicting rules at the same priority level = unspecified behavior

crap [cobblestone, andesite, diorite
      granite, snowball]

0 snowball -> volatile
-1 @playerDrop -> timer(3600)
-3 @playerHarvest crap -> notimer
-5 @playerCaused -> timer(1800)
-7 crap -> notimer
-9 -> timer(900)
"""

val commentPattern = Pattern.compile("#.*")
val tagnamePattern = Pattern.compile("[A-Za-z]+")
val arrowPattern = Pattern.compile("->")

class Rules {
    val levels = mutableMapOf<Int, RulesLevel>()

    fun add(ctx: ParseContext, level: Int, rule: Scanner): String? =
            levels.computeIfAbsent(level, {RulesLevel()})
                    .add(ctx, rule)

    fun addTag(ctx: ParseContext, tagname: String, ln_: Int, line_: Scanner, lines: Iterator<IndexedValue<String>>): String? {
        if (!line_.hasNext() || line_.next() != "[") return errMessage(ln_, "Expected [ after tag name")
        if (ctx.tags.contains(tagname)) return errMessage(ln_, "Duplicate tag \"$tagname\"")
        var ln = ln_
        var line = line_
        val predicateses = mutableListOf(Predicates())

        while (true) {
            while (!line.hasNext()) {
                if (!lines.hasNext()) return errMessage(ln, "Unexpected EOF, unclosed block for tag \"$tagname\"")
                val lineIndexed = lines.next()
                ln = lineIndexed.index + 1
                line = Scanner(fluffSpecialDelimiters(lineIndexed.value))
                if (!predicateses.last().isEmpty()) predicateses.add(Predicates())
            }

            val token = line.next()
            when (token) {
                "," -> {
                    if (!predicateses.last().isEmpty()) predicateses.add(Predicates())
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

    class RulesLevel {
        val rules = mutableListOf<Rule>()

        fun add(ctx: ParseContext, s: Scanner): String? {
            val rule = Rule()
            rules.add(rule)

            while (!s.hasNext(arrowPattern)) {
                if (!s.hasNext()) return "No arrow (\"->\") in rule line"
                rule.addPredicate(ctx, s.next()) ?.let{return it}
            }
            s.next()

            while (s.hasNext())
                rule.addEffect(s.next()) ?.let{return it}
            if (rule.effects.isEmpty()) return "No effects for rule"

            return null
        }
    }

    class Rule {
        val predicates = Predicates()
        val effects = mutableListOf<Effect>()

        fun addPredicate(ctx: ParseContext, s: String): String? = predicates.add(ctx, s)

        fun addEffect(s: String): String? = effect(s).map(
                {
                    effects.add(it)
                    null
                },
                {it}
            )
    }
}

fun errMessage(ln: Int, m: String) = "Error on line $ln: $m"
fun errEither(s: String) = Either.right<Rules, String>(s)


class ParseContext {
    val tags = mutableMapOf<String, List<Predicates>>()
    val predicateCache = mutableMapOf<String, Predicate>()
    val tagPredicates = mutableListOf<TagPredicate>()
}

fun parseRules(fileInput: File): Either<Rules, String> {
    if (!fileInput.exists())
        fileInput.writeText(defaultRules)

    val rules = Rules()
    val ctx = ParseContext()

    fileInput.useLines {
        val lines = it.withIndex().iterator()

        while (lines.hasNext()) {
            val lineIndexed = lines.next()
            val line = Scanner(fluffSpecialDelimiters(lineIndexed.value))
            if (!line.hasNext(commentPattern) && line.hasNext()) {
                val lineNumber = lineIndexed.index + 1

                if (line.hasNextInt()) {
                    rules.add(ctx, line.nextInt(), line)
                            ?.let {return errEither(errMessage(lineNumber, it)) }
                } else {
                    if (line.hasNext(tagnamePattern)) {
                        rules.addTag(ctx, line.next(), lineNumber, line, lines)
                            ?.let {return errEither(it)}
                    } else {
                        return errEither(errMessage(lineNumber, "illegal tag name: ${line.next()} (tag name must be purely alphanumeric)"))
                    }
                }
            }
        }
    }

    ctx.tagPredicates.forEach({
        val tag = ctx.tags[it.name] ?: return errEither("Tag referenced but never defined: \"${it.name}\"")
        it.predicateses = tag
    })

    return Either.left(rules)
}

val specialDelimiters = Regex("[\\[\\],]") // these characters get automatically surrounded with spaces
private fun fluffSpecialDelimiters(s: String): String = s.replace(specialDelimiters,
        {match -> " ${match.value} "}
)

