package lingerloot

import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import net.minecraftforge.fml.relauncher.ReflectionHelper

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

val pickupDelayField by lazy { ReflectionHelper.findField(EntityItem::class.java, "delayBeforeCanPickup", "field_145804_b") }
fun EntityItem.getPickupDelay(): Int { return pickupDelayField.get(this) as Int }

fun splitNumberEvenlyIsh(number: Int, maxSplits: Int): Collection<Int> {
    val baseSplit = number / maxSplits
    val remainder = number % maxSplits

    return (1..maxSplits)
        .map{if (it <= remainder) baseSplit + 1 else baseSplit}
        .filter{it > 0}
}

fun spam(pre: String, entityItem: EntityItem) {
    System.out.println(pre+ " " + "type: " + entityItem.javaClass.typeName + entityItem.item + " with lifespan: "+entityItem.lifespan+", age: "+entityItem.extractAge() + " and pickup delay " + entityItem.getPickupDelay())

}

fun detectCreativeGiveSecondTick(item: EntityItem) =
        item.extractAge() == CREATIVE_GIVE_DESPAWN_TICK && item.getPickupDelay() == 39

fun square(x: Double) = x*x
fun square(x: Float) = x*x

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