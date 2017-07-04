package lingerloot.hardcore

import lingerloot.rand
import lingerloot.splitNumberEvenlyIsh
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


fun placeAndSplitBlock(world: WorldServer, entityItem: EntityItem, type: ItemBlock) {
    if (rand.nextFloat() < .04) entityItem.item.shrink(1) // slight loss to prevent infinite scenarios
    if (entityItem.item.count <= 0) return

    val fakePlayer = FakerPlayer(world)
    val pos = entityItem.position
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

    splitNumberEvenlyIsh(entityItem.item.count, 3)
            .map{EntityItemExploding(world, entityItem.posX, entityItem.posY, entityItem.posZ,
                    {val stack = entityItem.item.copy(); stack.count = it; stack}()
            )}
            .forEach {
                it.jumpAround()
                it.lifespan = 20 + rand.nextInt(3*20) // 1-4 seconds
                world.spawnEntity(it)
            }
}

fun placeBlock(world: WorldServer, pos: BlockPos, type: ItemBlock, item: ItemStack): Boolean {
    val player = FakePlayer(world, DROPS_PROFILE)

    if (pos.y < 1 || pos.y >= world.minecraftServer?.buildLimit?:-25565)
        return false

    if (!type.block.canPlaceBlockAt(world, pos)) return false

    if (type.placeBlockAt(item, FakePlayer(world, DROPS_PROFILE), world, pos, EnumFacing.UP,0f, 0f, 0f,
            getBlockForPlacement(world, pos, type, item, player))) {
        item.shrink(1)
        return true
    }

    return false
}

fun getBlockForPlacement(world: WorldServer, pos: BlockPos, type: ItemBlock, item: ItemStack, player: FakePlayer): IBlockState {
    return type.block.getStateForPlacement(world, pos, EnumFacing.UP, 0f, 0f, 0f,
            item.item.getMetadata(item.metadata), player, EnumHand.MAIN_HAND)
}