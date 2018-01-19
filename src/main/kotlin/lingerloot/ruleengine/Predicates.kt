package lingerloot.ruleengine

import com.elytradev.concrete.common.Either
import net.minecraft.item.*
import net.minecraft.util.ResourceLocation
import net.minecraftforge.oredict.OreDictionary


class Predicates {
    val predicates = mutableListOf<Predicate>()

    fun add(ctx: ParseContext, s: String): String? = predicate(ctx, s).map(
            {
                predicates.add(it)
                null
            },
            {it}
        )

    fun isEmpty() = predicates.isEmpty()

    fun resolve(ctx: EvaluationContext) = predicates.all{it.resolve(ctx)}
}

fun predicate(ctx: ParseContext, s: String): Either<Predicate, String> = ctx.predicateCache[s]
            ?.let{Either.left<Predicate, String>(it)}
            ?: genPredicate(ctx, s).mapLeft{ctx.predicateCache[s] = it; it}

private fun genPredicate(ctx: ParseContext, s: String): Either<Predicate, String> {
    when (s[0]) {
        '~' -> { // TODO: ~ as negation, come up with a new symbol
            val oredictName = s.substring(1)
            if (!OreDictionary.doesOreNameExist(oredictName))
                return Either.right("Invalid oredict name: \"$oredictName\"")
            return Either.left(OredictPredicate(OreDictionary.getOreID(oredictName)))
        }
        '&' -> {
            val className = s.substring(1)
            val pred = classPredicatesByName[className.toUpperCase()]
            if (pred != null) return Either.left(pred)
            return Either.right("Unknown class: \"$className\"")
        }
        '%' -> {
            if (s.length < 2) return Either.right("Empty tagname")
            return Either.left(TagPredicate(s.substring(1)))
        }
        ':' -> {
            if (s.length < 2) return Either.right("Empty mod id")
            return Either.left(ModIdPredicate(s.substring(1)))
        }
        '@' -> {
            val causeName = s.substring(1)
            val pred = causePredicatesByName[causeName.toUpperCase()]
            if (pred != null) return Either.left(pred)
            return Either.right("Unknown cause: \"$causeName\"")
        }
        else -> {
            val itemName = s.takeWhile{it != '@'}

            val damage = if (itemName.length == s.length)
                null
            else
                s.substring(itemName.length + 1).toIntOrNull()
                        ?: return Either.right("Invalid damage value for item \"$s\"")

            val item = Item.REGISTRY.getObject(ResourceLocation(itemName)) ?: return Either.right("Item not found: \"$s\"")
            return Either.left(ItemPredicate(item, damage))
        }
    }
}

interface Predicate {
    fun resolve(ctx: EvaluationContext): Boolean
}

val DUMMY_TAG = listOf<Predicates>()
class TagPredicate(val name: String): Predicate {
    var predicateses = DUMMY_TAG // should always be reassigned

    override fun resolve(ctx: EvaluationContext): Boolean = ctx.tagCache.computeIfAbsent(name, {
        if (ctx.tagRecursionStack.contains(name)) throw Exception("Tag evaluation aborted due to circular reference: " +
                ctx.tagRecursionStack.joinToString(", "))
        ctx.tagRecursionStack += name
        val tagResult = predicateses.any({it.resolve(ctx)})
        ctx.tagRecursionStack -= name
        tagResult
    })
}


enum class CausePredicates(val mask: Int): Predicate {
    PLAYERDROP(1), PLAYERLOOT(2), PLAYERMINE(4), MOBDROP(8),
    PLAYERTOSS(16), CREATIVEGIVE(32),
    PLAYERHARVEST(2+4), PLAYERCAUSED(1+2+4+16);
    override fun resolve(ctx: EvaluationContext) = ctx.causeMask and mask != 0
}
val causePredicatesByName = CausePredicates.values().map{Pair(it.name, it)}.toMap()

enum class ClassPredicates: Predicate {
    ARMOR, BLOCK, FOOD, TOOL;
    override fun resolve(ctx: EvaluationContext) = ctx.classMask and mask() != 0
    fun mask() = 1 shl ordinal
}
val classPredicatesByName = ClassPredicates.values().map{Pair(it.name, it)}.toMap()

fun getClassMask(item: Item) =
        (if (item is ItemArmor) ClassPredicates.ARMOR.mask() else 0) +
        (if (item is ItemBlock) ClassPredicates.BLOCK.mask() else 0) +
        (if (item is ItemFood)  ClassPredicates.FOOD .mask() else 0) +
        (if (item is ItemTool)  ClassPredicates.TOOL .mask() else 0)

class OredictPredicate(val oreid: Int): Predicate {
    override fun resolve(ctx: EvaluationContext) = ctx.oreIds.contains(oreid)
}

class ItemPredicate(val item: Item, val damage: Int?): Predicate {
    override fun resolve(ctx: EvaluationContext) = ctx.item.item.item == item &&
            (damage == null || damage == ctx.item.item.itemDamage)
}

class ModIdPredicate(val mod: String): Predicate {
    override fun resolve(ctx: EvaluationContext) = ctx.item.item.item.registryName?.resourceDomain == mod
}