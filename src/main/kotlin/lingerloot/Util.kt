package lingerloot

import net.minecraft.block.BlockPistonBase
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
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

// TODO: see why this reflection isn't working
//val pistonDoMoveMethod by lazy { ReflectionHelper.findMethod(BlockPistonBase::class.java, Blocks.PISTON,
//        arrayOf("doMove", "func_176319_a"),
//        World::class.java, BlockPos::class.java, EnumFacing::class.java, Boolean::class.java)
//}
//fun pistonDoMove(worldIn : World, pos : BlockPos, direction : EnumFacing, extending : Boolean): Boolean =
//        pistonDoMoveMethod.invoke(worldIn, pos, direction, extending) as Boolean

// requires AT
fun pistonDoMove(worldIn : World, pos : BlockPos, direction : EnumFacing, extending : Boolean): Boolean =
        Blocks.PISTON.doMove(worldIn, pos, direction, extending)

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
