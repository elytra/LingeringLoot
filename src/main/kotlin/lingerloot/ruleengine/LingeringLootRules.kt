package lingerloot.ruleengine

import capitalthree.ruleengine.Effect
import capitalthree.ruleengine.RulesEngine
import capitalthree.ruleengine.Predicate
import com.elytradev.concrete.common.Either
import lingerloot.DEFAULT_PICKUP_DELAY
import lingerloot.LegacyRules
import lingerloot.lookupItem
import lingerloot.volatility.despawnHandlerSetsByShort
import net.minecraftforge.oredict.OreDictionary

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


val _domainPredicates = mapOf<Char, (String) -> Either<Predicate<EntityItemCTX>, String>>(
        '$' to { oredictName ->
            if (OreDictionary.doesOreNameExist(oredictName))
                Either.left(OredictPredicate(OreDictionary.getOreID(oredictName)))
            else Either.right("Invalid oredict name: \"$oredictName\"")
        },
        '&' to { className ->
            val pred = classPredicatesByName[className.toUpperCase()]
            if (pred != null) Either.left(pred)
            else Either.right("Unknown class: \"$className\"")
        },
        ':' to { modId ->
            if (modId.isNotEmpty()) Either.left(ModIdPredicate(modId))
            else Either.right("Empty mod id")
        },
        '@' to { causeName ->
            val pred = causePredicatesByName[causeName.toUpperCase()]
            if (pred != null) Either.left(pred)
            else Either.right("Unknown cause: \"$causeName\"")
        }
)

object LingerRulesEngine : RulesEngine<EntityItemCTX>() {
    override fun getEffectSlots() = expectedEffectTypes

    override fun getDomainPredicates() = _domainPredicates
    override fun unprefixedPredicate(s: String) = lookupItem(s)

    override fun interestingNumberList() = listOf("x", "y", "z", "light", "dim")
    override fun genInterestingNumbers(from: EntityItemCTX): DoubleArray { val item = from.item
        return doubleArrayOf(item.posX, item.posY, item.posZ, item.brightness.toDouble(), item.dimension.toDouble())
    }

    override fun effect(s: String): Either<Iterable<out Effect<EntityItemCTX>>, String> {
        val word = s.takeWhile{it != '('}
        val param = if (word.length < s.length)
            if (s.last() == ')')
                s.substring(word.length+1, s.length-1)
            else
                return Either.right("Effect \"$s\" has '(' but doesn't end with ')'")
        else
            null

        return Either.left<Iterable<out Effect<EntityItemCTX>>, String>(when (word) {
            "timer" -> {listOf(if (param == null) {
                NOTIMER
            } else {
                val seconds = param.toDoubleOrNull() ?: return Either.right("Invalid double: \"$param\"")
                val ticks = (seconds * 20).toInt()
                TimerEffect(ticks)
            })}

            "convert" -> {listOf(if (param == null) {
                NOTF
            } else {
                val lookup = lookupItem(param)
                TransformEffect(
                        if (lookup.isLeft) lookup.leftNullable
                        else return Either.right(lookup.rightNullable)
                )
            })}

            "pickupdelay" -> {listOf(if (param == null) {
                NODELAY
            } else {
                PickupDelayEffect(param.toIntOrNull()?: return Either.right("Invalid int: \"$param\""))
            })}

            "despawn" -> {listOf(if (param == null) {
                NOVOL
            } else {
                if (param.length != 1) return Either.right("Despawn handler specifier must be one character")
                val handlerCode = param[0].toShort()
                if (handlerCode !in despawnHandlerSetsByShort) return Either.right("Invalid despawn handler code: $param")
                VolatileEffect(handlerCode)
            })}
            "finalize" -> {listOf(NOTIMER, NOVOL, NODELAY, NOTF)}
            else -> return Either.right("Invalid effect keyword: \"$word\"")
        })
    }
}
