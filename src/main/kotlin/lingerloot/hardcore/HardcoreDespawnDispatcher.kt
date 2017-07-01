package lingerloot.hardcore

import lingerloot.*
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraftforge.event.entity.item.ItemExpireEvent

object HardcoreDespawnDispatcher {
    fun dispatch(event: ItemExpireEvent) {
        val entityItem = event.entityItem
        if (correctForCreativeGive(entityItem) && entityItem.extractAge() < entityItem.lifespan) {
            event.isCanceled = true
            return
        }

        prescreen.remove(entityItem)

        if (entityItem.getPickupDelay() == INFINITE_PICKUP_DELAY)
            return // ignore cosmetic fake item

        val type = entityItem.entityItem.item

        when (type) {
            is ItemBlock -> placeAndSplitBlock(entityItem, type)
        }
    }

    fun placeAndSplitBlock(entityItem: EntityItem, type: ItemBlock) {
        val world = entityItem.entityWorld
        val pos = entityItem.position
        val blockState = world.getBlockState(pos)
        val drops = blockState.block.getDrops(world, pos, blockState, 0)
        world.setBlockState(pos, type.block.blockState.baseState)

        splitNumberEvenlyIsh(entityItem.entityItem.count - 1, 3)
            .map{EntityItemExploding(world, entityItem.posX, entityItem.posY, entityItem.posZ, ItemStack(type, it))}
            .forEach {
                it.jumpAround()
                it.lifespan = 20 + rand.nextInt(3*20)
                world.spawnEntity(it)
            }

        drops.map{EntityItem(world, entityItem.posX, entityItem.posY, entityItem.posZ, it)}
            .forEach{
                it.jumpAround()
                world.spawnEntity(it)
            }
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
