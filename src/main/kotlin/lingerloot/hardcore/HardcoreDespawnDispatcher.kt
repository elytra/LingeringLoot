package lingerloot.hardcore

import lingerloot.*
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemBlock
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.item.ItemExpireEvent

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
}

fun EntityItem.jumpAround() {
    this.motionX = (rand.nextDouble()-.5)/3
    this.motionY =  rand.nextDouble()    /3
    this.motionZ = (rand.nextDouble()-.5)/3
    this.posX += 3*this.motionX
    this.posY += 3*this.motionY
    this.posZ += 3*this.motionZ
}
