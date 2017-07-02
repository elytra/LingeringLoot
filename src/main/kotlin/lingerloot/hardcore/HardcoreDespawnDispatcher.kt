package lingerloot.hardcore

import com.mojang.authlib.GameProfile
import lingerloot.*
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.entity.item.ItemExpireEvent
import java.util.*

val DROPS_PROFILE = GameProfile(UUID.randomUUID(), "The Drops")

object HardcoreDespawnDispatcher {
    fun dispatch(event: ItemExpireEvent) {
        val entityItem = event.entityItem
        if (entityItem.item.count <= 0) return
        val world = entityItem.entityWorld as? WorldServer ?: return

        if (correctForCreativeGive(entityItem) && entityItem.extractAge() < entityItem.lifespan) {
            event.isCanceled = true
            return
        }

        prescreen.remove(entityItem)

        if (entityItem.getPickupDelay() == INFINITE_PICKUP_DELAY)
            return // ignore cosmetic fake item

        val type = entityItem.item.item

        when (type) {
            is ItemBlock -> placeAndSplitBlock(world, entityItem, type)
        }
    }

    fun placeAndSplitBlock(world: WorldServer, entityItem: EntityItem, type: ItemBlock) {
        val pos = entityItem.position
        val blockState = world.getBlockState(pos)
        val drops = blockState.block.getDrops(world, pos, blockState, 0)

        placeBlock(world, pos, type, entityItem.item)
        entityItem.item.shrink(1) // first block place attempt counts even if it was unplaceable

        val pushDirections = EnumFacing.values().toMutableList()
        Collections.shuffle(pushDirections)

        while (entityItem.item.count > 0 && pushDirections.isNotEmpty()) {
            val pushDirection = pushDirections.removeAt(0)
            if (Blocks.PISTON.doMove(world, pos, pushDirection, true)) {
                val pushInto = pos.offset(pushDirection)
                world.setBlockToAir(pushInto)
                placeBlock(world, pushInto, type, entityItem.item)
            }
        }

        world.getBlockState(pos).block

        splitNumberEvenlyIsh(entityItem.item.count, 3)
            .map{EntityItemExploding(world, entityItem.posX, entityItem.posY, entityItem.posZ,
                {val stack = entityItem.item.copy(); stack.count = it; stack}()
            )}
            .forEach {
                it.jumpAround()
                it.lifespan = 20 + rand.nextInt(3*20) // 1-4 seconds
                world.spawnEntity(it)
            }

        drops.map{EntityItem(world, entityItem.posX, entityItem.posY, entityItem.posZ, it)}
            .forEach{
                it.jumpAround()
                it.lifespan = 10*20 + rand.nextInt(30*20) // 10-40 seconds
                world.spawnEntity(it)
            }
    }

    fun placeBlock(world: WorldServer, pos: BlockPos, type: ItemBlock, item: ItemStack): Boolean {
        val player = FakePlayer(world, DROPS_PROFILE)

        if (pos.y < 1 || pos.y >= world.minecraftServer?.buildLimit?:-25565)
            return false

        if (!type.block.canPlaceBlockAt(world, pos)) return false

        if (type.placeBlockAt(item, FakePlayer(world, DROPS_PROFILE), world, pos, EnumFacing.UP,0f, 0f, 0f,
                type.block.getStateForPlacement(world, pos, EnumFacing.UP, 0f, 0f, 0f,
                    item.item.getMetadata(item.metadata), player, EnumHand.MAIN_HAND))) {
            item.shrink(1)
            return true
        }
        return false
    }
}

fun EntityItem.jumpAround() {
    this.motionX = (rand.nextDouble()-.5)/3
    this.motionY =  rand.nextDouble()    /3
    this.motionZ = (rand.nextDouble()-.5)/3
    this.posX += 3*this.motionX
    this.posY += 3*this.motionY
    this.posZ += 3*this.motionZ
}
