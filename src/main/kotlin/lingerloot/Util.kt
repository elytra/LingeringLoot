package lingerloot

import net.minecraft.entity.item.EntityItem
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

/**
 * attempts to detect /give and creative-mode-dropped items to restore the expected 1 minute despawn timer
 * @return whether change was required
 */
fun correctForCreativeGive(item: EntityItem): Boolean {
    if (item.extractAge() == CREATIVE_GIVE_DESPAWN_TICK && item.getPickupDelay() == 39) {
        item.lifespan = FAKE_DEFAULT_LIFESPAN
        return true
    }

    return false
}
