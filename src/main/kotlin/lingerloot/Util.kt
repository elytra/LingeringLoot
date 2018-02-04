package lingerloot

import com.elytradev.concrete.common.Either
import lingerloot.ruleengine.ItemPredicate
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.Item
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import net.minecraftforge.fml.relauncher.ReflectionHelper
import java.util.*

inline fun <T> MutableIterable<T>.filterInPlace(filter: (T)->Boolean) {
    val it = iterator()
    while (it.hasNext())
        if (!filter(it.next()))
            it.remove()
}

fun EntityItem?.ifAlive(): EntityItem? {
    return if (this != null && !this.isDead) this else null
}

val ageField by lazy { ReflectionHelper.findField(EntityItem::class.java, "age", "field_70292_b") }
fun EntityItem.extractAge(): Int { return ageField.get(this) as Int }

val pickupDelayField by lazy { ReflectionHelper.findField(EntityItem::class.java, "pickupDelay", "field_145804_b") }
fun EntityItem.extractPickupDelay(): Int { return pickupDelayField.get(this) as Int }

fun goldenSplit(number: Int): Collection<Int> {
    val major = number/2
    val minor = (.618*(number-major)).toInt()
    return listOf(major, minor, number-major-minor)
        .filter{it > 0}
}

fun detectCreativeGiveSecondTick(item: EntityItem) =
        item.extractAge() == CREATIVE_GIVE_DESPAWN_TICK && item.extractPickupDelay() == 39

fun square(x: Double) = x*x

/**
 * Simplified entity-block collision box checking for entities that are within their block vertically,
 * and no more than one meter wide
 */
fun blocksIntersectingSmallEntity(entity: Entity, cylinder: Boolean): List<BlockPos> {
    val offX = entity.posX - (entity.position.x + .5) // offsets from center of closest block
    val offZ = entity.posZ - (entity.position.z + .5)
    val absOffX = Math.abs(offX)                          // 0 - .5
    val absOffZ = Math.abs(offZ)
    val sigOffX = Math.signum(offX).toInt()               // who got drunk and made signum return double?
    val sigOffZ = Math.signum(offZ).toInt()
    val farCorner = if (cylinder)
        Math.sqrt(square(.5-absOffX) + square(.5-absOffZ)) <= entity.width/2
    else
        .5 - absOffX <= entity.width/2 && .5 - absOffZ <= entity.width/2
    return listOf(
            entity.position,
            if (.5 - absOffX <= entity.width/2) entity.position.add(sigOffX, 0, 0) else null,
            if (.5 - absOffZ <= entity.width/2) entity.position.add(0, 0, sigOffZ) else null,
            if (farCorner) entity.position.add(sigOffX, 0, sigOffZ) else null
    ).filterNotNull()
    .sortedBy {square(entity.posX - (it.x + .5)) + square(entity.posZ - (it.z + .5))}
}

/**
 * returns a list containing intersecting blocks on this level followed by one level below,
 * but with any AIR sorted to the end
 */
fun blockAreaOfEffectForEntityAirLast(world: WorldServer, entity: Entity, cylinder: Boolean): List<BlockPos> {
    val topLayer = blocksIntersectingSmallEntity(entity, cylinder)
    val filtered = (topLayer + topLayer.map{it.down()}).partition{world.isAirBlock(it)}
    return filtered.second + filtered.first
}

fun lookupItem(s: String): Either<ItemPredicate, String> {
    val itemName = s.takeWhile{it != '@'}

    val damage = if (itemName.length == s.length)
        null
    else
        s.substring(itemName.length + 1).toIntOrNull()
                ?: return Either.right("Invalid damage value for item \"$s\"")

    val item = Item.REGISTRY.getObject(ResourceLocation(itemName)) ?: return Either.right("Item not found: \"$s\"")
    return Either.left(ItemPredicate(item, damage))
}

val rand = Random()