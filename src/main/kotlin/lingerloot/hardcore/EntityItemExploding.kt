package lingerloot.hardcore

import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraft.world.World

class EntityItemExploding(worldIn: World?, x: Double, y: Double, z: Double, stack: ItemStack?)
        : EntityItem(worldIn, x, y, z, stack) {
    override fun combineItems(other: EntityItem?) = false
}