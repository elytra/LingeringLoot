package lingerloot.ruleengine

import com.elytradev.concrete.common.Either
import lingerloot.DEFAULT_PICKUP_DELAY
import lingerloot.LegacyRules
import java.io.*
import java.util.*
import java.util.regex.Pattern

val documentation = """
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
#  ${'$'}oredictName
#  %tagname
#  @cause
#  &class
#  :modid
#  !not
#
#
# Cause refers to the conditions that caused the item to drop, as in the classic
# lingering loot config. They are:
#  @playerDrop
#  @playerHarvest (playerKill | playerMine)
#  @playerKill
#  @playerMine
#  @mobDrop
#  @playerToss
#  @playerCaused (playerToss | playerHarvest | playerDrop)
#  @creativeGive
#  @other
#
# Note that other works differently from in the old config.  Other only applies if no
# other causes apply, regardless of whether other rules were matched.  To provide a
# fallthrough value, just make a rule with fewer or no predicates at a lower priority level.
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
# tagname [predicateA  predB  predC,
#    predD  predE]
# # tag value = (A & B & C) | (D & E)
# Note that commas are equivalent to newlines (breaking up and groups) thanks
# to Falkreon's suggestions.  capitalthree was about to design something dumb.
#
#
# Rule:
# priority     predicate    (predicate...)   ->   result   (result...)
#
#
# Results:
# timer(t)       set despawn time to t seconds (float)
# pickupdelay(t) set pickup delay (time before item can be picked up) to t ticks (int)
# volatile(h)    item will trigger special behavior when it would despawn (handler options: H = hardcore)
# convert(i)     transform into same-sized stack of item i (note that any predicate matching is still based on the original item)
# nofoo          leave vanilla behavior and prevent matching a lower priority foo rule
# nothing        no more effects.  equivalent to notimer nopickupdelay novolatile noconvert
#
# contradicting rules at the same priority level = unspecified behavior!
# priority levels are your only way to guarantee rule ordering.  All rules and tags
# can be defined in any order.
#
# Effects for the same rule are always taken in order, so eg, you can do "timer(60) nothing" to
# set a 1 minute timer and prevent further effects.  "nothing timer(60)" would always just do nothing.
# Don't be a nothing.  Make yourself a wacky fun lingering loot ruleset today!

"""

val commentPattern = Pattern.compile("#.*")
val tagnamePattern = Pattern.compile("[A-Za-z]+")
val arrowPattern = Pattern.compile("->")

class RulesAggregator {
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

    fun getRules() = levels.entries.sortedByDescending{it.key}.flatMap{it.value.rules}
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

typealias Rules = Iterable<Rule>

class Rule {
    val predicates = Predicates()
    val effects = mutableListOf<Effect>()

    fun addPredicate(ctx: ParseContext, s: String): String? = predicates.add(ctx, s)

    fun addEffect(s: String): String? = effect(s).map(
            {
                effects.addAll(it)
                null
            },
            {it}
    )
}

fun errMessage(ln: Int, m: String) = "Error on line $ln: $m"
fun errEither(s: String) = Either.right<Rules, String>(s)


class ParseContext {
    val tags = mutableMapOf<String, List<Predicates>>()
    val predicateCache = mutableMapOf<String, Predicate>()
    val tagPredicates = mutableListOf<TagPredicate>()
}

fun generateDefaultRules(legacyRules: LegacyRules): String {
    val builder = StringBuilder(documentation)
    val crap = legacyRules.shitTier.isNotEmpty() || legacyRules.shitTierMods.isNotEmpty()
    if (crap) {
        builder.append("crap [")
        if (legacyRules.shitTier.isNotEmpty()) {
            builder.appendln(legacyRules.shitTier.map { it.registryName.toString() }.joinToString(", "))
        }
        if (legacyRules.shitTierMods.isNotEmpty()) {
            builder.append(legacyRules.shitTierMods.map { ":$it" }.joinToString(", "))
        }
        builder.appendln("]\n")
    }

    builder.append("1 @creativeGive -> ")
    if (legacyRules.despawns.creative >= 0) builder.append("timer(${legacyRules.despawns.creative}) ")
    builder.appendln("nothing")

    builder.append("0 ")
    if (!legacyRules.hardcore) builder.append("snowball ")
    builder.appendln("-> volatile(H)")

    if (legacyRules.despawns.playerDrop >= 0) {
        builder.appendln("-1 @playerDrop -> timer(${legacyRules.despawns.playerDrop})")
    }

    if (crap) {
        builder.appendln("-2 @playerHarvest crap -> timer(${legacyRules.despawns.shitTier})")
    }

    if (legacyRules.despawns.playerToss >= 0) {
        builder.appendln("-3 @playerToss -> timer(${legacyRules.despawns.playerToss})")
    }

    if (legacyRules.despawns.playerMine >= 0) {
        builder.appendln("-3 @playerMine -> timer(${legacyRules.despawns.playerMine})")
    }

    if (legacyRules.despawns.playerKill >= 0) {
        builder.appendln("-3 @playerKill -> timer(${legacyRules.despawns.playerKill})")
    }

    if (legacyRules.despawns.playerCaused >= 0) {
        builder.appendln("-5 @playerCaused -> timer(${legacyRules.despawns.playerCaused})")
    }

    if (crap) {
        builder.appendln("-6 crap -> timer(${legacyRules.despawns.shitTier})")
    }

    if (legacyRules.despawns.mobDrop >= 0) {
        builder.appendln("-7 @mobDrop -> timer(${legacyRules.despawns.mobDrop})")
    }

    if (legacyRules.despawns.other >= 0) {
        builder.appendln("-8 -> timer(${legacyRules.despawns.other})")
    }

    if (legacyRules.minedPickupDelay != DEFAULT_PICKUP_DELAY) {
        builder.appendln("-9 @playerMine -> pickupdelay(${legacyRules.minedPickupDelay})")
    }

    return builder.toString()
}

fun parseRules(fileInput: File, legacyRules: () -> LegacyRules): Either<Rules, String> {
    if (!fileInput.exists())
        fileInput.writeText(generateDefaultRules(legacyRules()))

    val rules = RulesAggregator()
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

    return Either.left(rules.getRules())
}

val specialDelimiters = Regex("[\\[\\],]") // these characters get automatically surrounded with spaces
private fun fluffSpecialDelimiters(s: String): String = s.replace(specialDelimiters,
        {match -> " ${match.value} "}
)

