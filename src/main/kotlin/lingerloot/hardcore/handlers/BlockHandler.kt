package lingerloot.hardcore.handlers

import lingerloot.LingeringLootConfig
import lingerloot.hardcore.DROPS_PROFILE
import lingerloot.hardcore.FakerPlayer
import lingerloot.hardcore.scatterRemainderToTheWinds
import lingerloot.rand
import net.minecraft.block.BlockFalling
import net.minecraft.block.material.EnumPushReaction
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import net.minecraftforge.common.util.FakePlayer
import java.util.*


fun placeAndSplitBlock(cfg: LingeringLootConfig, world: WorldServer, entityItem: EntityItem, type: ItemBlock) {
    if (!cfg.shiva && rand.nextFloat() < .04) entityItem.item.shrink(1) // slight loss to prevent infinite scenarios
    if (entityItem.item.count <= 0) return

    val fakePlayer = FakerPlayer(world, entityItem)
    val pos = if (type.block is BlockFalling) {
        (1..64).map{entityItem.position.add(0, it, 0)}
            .firstOrNull{it.y>= world.minecraftServer!!.buildLimit || !world.isAirBlock(it)}
            ?.add(0, -1, 0)
            ?: entityItem.position.add(0, 64, 0)
    } else {
        entityItem.position
    }
    fakePlayer.interactionManager.tryHarvestBlock(pos)

    val canPushBlocks =
        when (getBlockForPlacement(world, pos, type, entityItem.item, fakePlayer).mobilityFlag) {
            EnumPushReaction.NORMAL, EnumPushReaction.BLOCK -> true
            else -> false
        }

    placeBlock(world, pos, type, entityItem.item)
    entityItem.item.shrink(1) // first block place attempt counts even if it was unplaceable

    val pushDirections = EnumFacing.values().toMutableList()
    Collections.shuffle(pushDirections)

    while (entityItem.item.count > 0 && pushDirections.isNotEmpty()) {
        val pushDirection = pushDirections.removeAt(0)
        val pushInto = pos.offset(pushDirection)

        if (world.getBlockState(pushInto).block == Blocks.AIR)
            placeBlock(world, pushInto, type, entityItem.item)
        else if (canPushBlocks && Blocks.PISTON.doMove(world, pos, pushDirection, true)) {
            world.setBlockToAir(pushInto) // eliminate piston arm
            placeBlock(world, pushInto, type, entityItem.item)
        }
    }

    scatterRemainderToTheWinds(world, entityItem)
}

fun placeBlock(world: WorldServer, pos: BlockPos, type: ItemBlock, item: ItemStack) {
    val player = FakePlayer(world, DROPS_PROFILE)

    if (pos.y < 1 || pos.y >= world.minecraftServer?.buildLimit?:-25565)
        return

    if (!type.block.canPlaceBlockAt(world, pos)) return

    if (type.placeBlockAt(item, FakePlayer(world, DROPS_PROFILE), world, pos, EnumFacing.UP,0f, 0f, 0f,
            getBlockForPlacement(world, pos, type, item, player)))
        item.shrink(1)
        return
}

fun getBlockForPlacement(world: WorldServer, pos: BlockPos, type: ItemBlock, item: ItemStack, player: FakePlayer): IBlockState {
    return type.block.getStateForPlacement(world, pos, EnumFacing.UP, 0f, 0f, 0f,
            item.item.getMetadata(item.metadata), player, EnumHand.MAIN_HAND)
}