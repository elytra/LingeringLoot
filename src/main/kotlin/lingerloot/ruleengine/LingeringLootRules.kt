package lingerloot.ruleengine

import com.elytradev.concrete.common.Either
import lingerloot.DEFAULT_PICKUP_DELAY
import lingerloot.LegacyRules
import lingerloot.cfg
import java.io.*
import java.util.*
import java.util.regex.Pattern

val documentation = """
# Hi!  I'm Nikky!  capitalthree kidnapped me and they won't let me go until I explain this stupid new config format to
# you.  capitalthree threw out the nice simple config format you're used to and replaced it with this confusing mess,
# so let's just get this over with so I can go back to working on modpacker tools.

# The first thing you need to know about is predicates.  If you don't know what those are, you shouldn't even be...
# eeep!  that tickles!  okay, okay, I'll be nice!  Predicates are the fundamental tests you can filter on in your rules.

# Predicates:
#  itemname
#   - This can be a vanilla itemname, or modid:itemname, itemname@damage, etc
#  ${'$'}oredictName
#  %tagname
#  @cause
#  &class
#  :modid
#   - Note that these cannot be checked for correctness at rules load time
#  !negated

# Cause refers to the conditions that caused the item to drop, as in the classic lingering loot config. They are:
#  @playerDrop
#  @playerHarvest = @playerKill | @playerMine
#  @playerKill
#  @playerMine
#  @mobDrop
#  @playerToss
#  @playerCaused = @playerToss | @playerHarvest | @playerDrop
#  @creativeGive
#  @other

# Note that other works differently from in the old config.  Other only applies if no other causes apply, regardless of
# whether other rules were matched.  To provide a fallthrough value, just make a rule with fewer or no predicates at a
# lower priority level.

# Some of the causes are sets of certain other causes, provided for convenience.  As if there's anything convenient
# about-  eeeep!  Okay, okay, I'll read it! Class refers to certain classes of items, as follows:
#  &armor
#  &block
#  &food
#  &tool

# Tag predicates must match an actual tag you have... I mean duh... here's how you define a tag:
# | tagname [predicateA  predB  predC,
# |    predD  predE]

# The tag's value would be, stated mathematically, (A & B & C) | (D & E)
# Note that commas are equivalent to newlines (breaking up and groups) thanks to
# Falkreon's suggestions.  capitalthree was about to design something dumb otherwise.

# Now it wouldn't be much use to just define a bunch of tags, right?  Not that
# anyone would have been surprised if capitalthree pulled that stunt... yaaugh ok,
# ok, no tickling!  ...So that's why we have rules, which can have effects!
# | priority     (predicate...)   ->   effect   (effect...)
# Priority is an integer, obviously.  A rule with no predicates will always match.

# There are 4 categories of effects, and for each, the highest priority rule takes precedence, but if a rule provides
# some effects, lower priority rules can still match and provide different effects.  If two rules have the same
# priority, the first one comes first.

# To make a rule terminal, simply add "finalize" at the end of the effects, which adds all no-op effects.  Effects from
# the same rule are applied in-order, so for example any effect after "finalize" in a rule is ignored.

# And now surely by now you want to know what this overengineered pile of... yeeep!  Okayyy put away that feather!  So
# what can this mod actually make items do?  Well here are the actual effects:
# timer(t)       set despawn time to t seconds (float)
# pickupdelay(t) set pickup delay (time before item can be picked up) to t ticks (int)
# despawn(h)     item will trigger special behavior when it would despawn (handler options: H = hardcore)
# convert(i)     transform into same-sized stack of item i (note that any predicate matching is still based on the original item)
# any of the above but without a param: leaves vanilla behavior alone and prevents matching a lower priority rule
# finalize       no more effects.  shorthand for timer pickupdelay despawn convert

# One particular use case you might want to be aware of is ore deduplication at the time of mining.  This is the
# primary intended purpose of the convert effect (though you can use it for whatever you like).  To deduplicate an ore
# type, make a convert rule that matches on the oredict name and converts to your favorite example of that ore.  Like:
# | 0 ${'$'}ingotBronze -> convert(embers:ingot_bronze)

# Maybe you loved lingering loot hardcore mode but hated the silverfish?  Now you can just change your hardcore mode
# rule to exclude foods!
# | 0 !&food -> despawn(H)

# Want a quick and dirty way to let players convert an item into another?  Make a rule for when players toss it, and
# set the pickup delay to 0 so they get the result instantly!
# | 1 @playerToss someitem -> convert(otheritem) pickupdelay(0) finalize

# There.  What more could you want from me?  Make yourself a wacky fun lingering loot ruleset today!  I'm freeeeeeeeeee!

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
}

fun generateDefaultRules(legacyRules: LegacyRules): String {
    val builder = StringBuilder(documentation)
    val shitModsFiltered = legacyRules.shitTierMods.filter{it.length > 0}
    val crap = legacyRules.shitTier.isNotEmpty() || shitModsFiltered.isNotEmpty()
    if (crap) {
        builder.append("crap [")
        if (legacyRules.shitTier.isNotEmpty()) {
            builder.appendln(legacyRules.shitTier.map { it.registryName.toString() }.joinToString(", "))
        }
        if (legacyRules.shitTierMods.isNotEmpty()) {
            builder.append(shitModsFiltered.map { ":$it" }.joinToString(", "))
        }
        builder.appendln("]\n")
    }

    builder.append("1 @creativeGive -> ")
    if (legacyRules.despawns.creative >= 0) builder.append("timer(${legacyRules.despawns.creative}) ")
    builder.appendln("finalize")

    builder.append("0 ")
    if (!legacyRules.hardcore) builder.append("snowball ")
    builder.appendln("-> despawn(H)")

    if (legacyRules.despawns.playerDrop >= 0) {
        builder.appendln("-1 @playerDrop -> timer(${legacyRules.despawns.playerDrop})")
    }

    if (crap) {
        builder.appendln("-2 @playerHarvest %crap -> timer(${legacyRules.despawns.shitTier})")
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
        builder.appendln("-6 %crap -> timer(${legacyRules.despawns.shitTier})")
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

fun parseRules(fileInput: File): Either<Rules, String> {
    if (!fileInput.exists())
        fileInput.writeText(generateDefaultRules(cfg!!.legacyRules))

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

    ctx.predicateCache.values.filterIsInstance<TagPredicate>().forEach({
        val tag = ctx.tags[it.name] ?: return errEither("Tag referenced but never defined: \"${it.name}\"")
        it.predicateses = tag
    })

    return Either.left(rules.getRules())
}

val specialDelimiters = Regex("[\\[\\],]") // these characters get automatically surrounded with spaces
private fun fluffSpecialDelimiters(s: String): String = s.replace(specialDelimiters,
        {match -> " ${match.value} "}
)

