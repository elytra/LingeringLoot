package lingerloot.hardcore

import lingerloot.blocksIntersectingSmallEntity
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.Item
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer

fun attemptUseStack(world: WorldServer, entityItem: EntityItem, type: Item) {
    val fakePlayer = FakerPlayer(world, entityItem.item)
    val thisLayer = blocksIntersectingSmallEntity(entityItem, true)
    val initialCount = fakePlayer.heldItemMainhand.count

    (thisLayer + thisLayer.map{it.down()}).forEach {
        attemptUseStackOnBlock(world, type, fakePlayer, it)
        if (fakePlayer.heldItemMainhand.isEmpty ||
                fakePlayer.heldItemMainhand.itemDamage > fakePlayer.heldItemMainhand.maxDamage)
            return
        if (fakePlayer.heldItemMainhand.count < initialCount)
            return@forEach
    }

    if (fakePlayer.heldItemMainhand.count == initialCount)
        entityItem.item.shrink(1)
    scatterRemainderToTheWinds(world, entityItem)
}

fun attemptUseStackOnBlock(world: WorldServer, type: Item,
                           fakePlayer: FakerPlayer, blockPos: BlockPos): EnumActionResult {
    return fakePlayer.interactionManager.processRightClickBlock(fakePlayer, world, fakePlayer.heldItemMainhand, EnumHand.MAIN_HAND,
            blockPos, EnumFacing.UP, 0f, 0f, 0f)
}