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
    val fakePlayer = FakerPlayer(world, entityItem)
    val thisLayer = blocksIntersectingSmallEntity(entityItem, true)
    val initialCount = fakePlayer.heldItemMainhand.count

    var actionTaken = false

    for(pos in (thisLayer + thisLayer.map{it.down()})) {
        if (attemptUseStackOnBlock(world, fakePlayer, pos) == EnumActionResult.SUCCESS)
            actionTaken = true
        if (fakePlayer.heldItemMainhand.isEmpty ||
                fakePlayer.heldItemMainhand.itemDamage > fakePlayer.heldItemMainhand.maxDamage)
            return
        if (fakePlayer.heldItemMainhand.count < initialCount)
            break
    }

    if (!actionTaken) {
        fakePlayer.randomLook()
        fakePlayer.interactionManager.processRightClick(fakePlayer, world, fakePlayer.heldItemMainhand, EnumHand.MAIN_HAND)
    }

    if (fakePlayer.heldItemMainhand == entityItem.item && fakePlayer.heldItemMainhand.count == initialCount)
        fakePlayer.heldItemMainhand.shrink(1)
    entityItem.item = fakePlayer.heldItemMainhand
    scatterRemainderToTheWinds(world, entityItem)
}

fun attemptUseStackOnBlock(world: WorldServer, fakePlayer: FakerPlayer, blockPos: BlockPos): EnumActionResult {
    return fakePlayer.interactionManager.processRightClickBlock(fakePlayer, world, fakePlayer.heldItemMainhand,
            EnumHand.MAIN_HAND, blockPos, EnumFacing.UP, 0f, 0f, 0f)
}