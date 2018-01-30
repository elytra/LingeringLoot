package lingerloot.ruleengine

import com.elytradev.concrete.rulesengine.Predicate
import com.elytradev.concrete.common.Either
import net.minecraft.item.*
import net.minecraftforge.oredict.OreDictionary

val lingerlootPredicates = mapOf<Char, (String) -> Either<Predicate<EntityItemCTX>, String>>(
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

enum class CausePredicates(val mask: Int): Predicate<EntityItemCTX> {
    PLAYERDROP(1), PLAYERKILL(2), PLAYERMINE(4), MOBDROP(8),
    PLAYERTOSS(16), CREATIVEGIVE(32), OTHER(64),
    PLAYERHARVEST(2+4), PLAYERCAUSED(1+2+4+16);
    override fun resolve(ctx: EntityItemCTX) = ctx.causeMask and mask != 0
}
val causePredicatesByName = CausePredicates.values().map{Pair(it.name, it)}.toMap()

enum class ClassPredicates: Predicate<EntityItemCTX> {
    ARMOR, BLOCK, FOOD, TOOL;
    override fun resolve(ctx: EntityItemCTX) = ctx.classMask and mask() != 0
    fun mask() = 1 shl ordinal
}
val classPredicatesByName = ClassPredicates.values().map{Pair(it.name, it)}.toMap()

fun getClassMask(item: Item) =
        (if (item is ItemArmor) ClassPredicates.ARMOR.mask() else 0) +
        (if (item is ItemBlock) ClassPredicates.BLOCK.mask() else 0) +
        (if (item is ItemFood)  ClassPredicates.FOOD .mask() else 0) +
        (if (item is ItemTool)  ClassPredicates.TOOL .mask() else 0)

class OredictPredicate(val oreid: Int): Predicate<EntityItemCTX> {
    override fun resolve(ctx: EntityItemCTX) = ctx.oreIds.contains(oreid)
}

class ItemPredicate(val item: Item, val damage: Int?): Predicate<EntityItemCTX> {
    override fun resolve(ctx: EntityItemCTX) = ctx.item.item.item == item &&
            (damage == null || damage == ctx.item.item.itemDamage)
}

class ModIdPredicate(val mod: String): Predicate<EntityItemCTX> {
    override fun resolve(ctx: EntityItemCTX) = ctx.item.item.item.registryName?.resourceDomain == mod
}

