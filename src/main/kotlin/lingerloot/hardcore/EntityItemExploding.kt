package lingerloot.hardcore

import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraft.world.World

class EntityItemExploding: EntityItem {
    constructor(worldIn: World?, x: Double, y: Double, z: Double, stack: ItemStack?): super(worldIn, x, y, z, stack)
    constructor(worldIn: World?): super(worldIn)
    override fun combineItems(other: EntityItem?) = false
}