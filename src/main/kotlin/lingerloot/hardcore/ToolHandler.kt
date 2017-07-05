package lingerloot.hardcore

import lingerloot.blocksIntersectingSmallEntity
import net.minecraft.block.Block.NULL_AABB
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemTool
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.entity.item.ItemExpireEvent

fun toolTime(world: WorldServer, entityItem: EntityItem, type: ItemTool, event: ItemExpireEvent) {
    val fakePlayer = FakerPlayer(world, entityItem)
    val thisLayer = blocksIntersectingSmallEntity(entityItem, false) // Are entity items supposed to have a
                // cylindrical bounding box?  Using cylindrical math resulted in getting stuck on corner edges

    (thisLayer + thisLayer.map{it.down()}).forEach {
        if (world.getBlockState(it).getCollisionBoundingBox(world, it) != NULL_AABB &&
                attemptToolUse(world, entityItem, type, fakePlayer, it)) {
            if (entityItem.item.count > 1) {
                scatterRemainderToTheWinds(world, entityItem)
            } else {
                extendToolTime(event)
                entityItem.jumpAround()
            }
            return
        }
    }

    attemptUseStack(world, entityItem, type) // one last chance to do something interesting, for some mod tools
}

fun extendToolTime(event: ItemExpireEvent) {
    event.extraLife = 30
    event.isCanceled = true
}

fun attemptToolUse(world: WorldServer, entityItem: EntityItem, type: ItemTool, fakePlayer: FakePlayer, blockPos: BlockPos): Boolean {
    val blockState = world.getBlockState(blockPos)

    if (type.getToolClasses(entityItem.item).any {blockState.block.isToolEffective(it, blockState)}) {
        if (fakePlayer.interactionManager.tryHarvestBlock(blockPos)) {
            return true
        }
    }

    return false
}
