package lingerloot.volatility

import lingerloot.*
import lingerloot.ruleengine.TOUCHED_CAP
import lingerloot.volatility.handlers.*
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.SoundEvents
import net.minecraft.item.*
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.item.ItemExpireEvent

object DespawnDispatcher {
    fun dispatch(event: ItemExpireEvent) {
        val entityItem = event.entityItem
        val world = entityItem.entityWorld as? WorldServer ?: return
        val item = entityItem.item

        prescreen.remove(entityItem)

        if (item.count <= 0) return

        val handler = entityItem.getCapability(TOUCHED_CAP!!, null)?.despawnHandler
        if (handler == null || !handler.handle(world, entityItem, item.item, event)) {
            entityItem.playSound(SoundEvents.ENTITY_CHICKEN_EGG, 1.0f, (rand.nextFloat() - rand.nextFloat()) * 0.2f + 1.0f)
        }
    }
}

enum class DespawnHandlerSet(val code: Char) {
    HARDCORE('H') {
        override fun handle(world: WorldServer, entityItem: EntityItem, type: Item, event: ItemExpireEvent): Boolean =
            when (type) {
                is ItemArrow -> spamArrows(world, entityItem, type)
                is ItemBow -> fireBow(world, entityItem, type, event)
                is ItemFood -> spontaneousGeneration(world, entityItem, type)
                is ItemBlock -> placeAndSplitBlock(world, entityItem, type)
                is ItemTool -> toolTime(world, entityItem, type, event)
                else -> attemptUseStack(world, entityItem, type, event)
            }
    };

    abstract fun handle(world: WorldServer, entityItem: EntityItem, type: Item, event: ItemExpireEvent): Boolean
}
val despawnHandlerSetsByShort = DespawnHandlerSet.values().map{Pair(it.code.toShort(), it)}.toMap()


fun EntityItem.jumpAround() {
    this.motionX = (rand.nextDouble()-.5)/2
    this.motionY =  rand.nextDouble()    /3
    this.motionZ = (rand.nextDouble()-.5)/2
    this.posX += 3*this.motionX
    this.posY += 3*this.motionY
    this.posZ += 3*this.motionZ
}


/**
 * split stack into up to 3 stacks and send them jumping
 * (never call this if you cancel the despawn event)
 */
fun scatterRemainderToTheWinds(world: WorldServer, entityItem: EntityItem) {
    goldenSplit(entityItem.item.count)
        .map{EntityItemExploding(world, entityItem.posX, entityItem.posY, entityItem.posZ,
            {val stack = entityItem.item.copy(); stack.count = it; stack}()
        )}
        .forEach {
            it.jumpAround()
            it.lifespan = 20 + rand.nextInt(3*20) // 1-4 seconds
            it.getCapability(TOUCHED_CAP!!, null)?.despawnHandler = DespawnHandlerSet.HARDCORE
            world.spawnEntity(it)
        }
}