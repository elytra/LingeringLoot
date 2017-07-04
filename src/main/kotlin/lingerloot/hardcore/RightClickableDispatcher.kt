package lingerloot.hardcore

import lingerloot.blocksIntersectingSmallEntity
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.Item
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import net.minecraftforge.common.util.FakePlayer

fun attemptUseStack(world: WorldServer, entityItem: EntityItem, type: Item) {
    val fakePlayer = FakerPlayer(world)
    val thisLayer = blocksIntersectingSmallEntity(entityItem, true)

    (thisLayer + thisLayer.map{it.add(0, -1, 0)}).forEach {
        attemptUseStackOnBlock(world, entityItem, type, fakePlayer, it)
        if (entityItem.item.isEmpty) return
    }
}


fun attemptUseStackOnBlock(world: WorldServer, entityItem: EntityItem, type: Item, fakePlayer: FakePlayer, blockPos: BlockPos) {
    if (EnumActionResult.PASS != type.onItemUseFirst(fakePlayer, world, blockPos, EnumFacing.UP,
            0f, 0f, 0f, EnumHand.MAIN_HAND))
        return

    if (EnumActionResult.PASS != type.onItemRightClick(world, fakePlayer, EnumHand.MAIN_HAND).type)
        return

    if (EnumActionResult.PASS != entityItem.item.onItemUse(fakePlayer, world, blockPos, EnumHand.MAIN_HAND, EnumFacing.UP, 0f, 0f, 0f))
        return
}