package lingerloot.volatility.handlers

import lingerloot.LingeringLootConfig
import lingerloot.blockAreaOfEffectForEntityAirLast
import lingerloot.cfg
import lingerloot.volatility.FakerPlayer
import lingerloot.volatility.scatterRemainderToTheWinds
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemEgg
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.item.ItemExpireEvent

fun attemptUseStack(world: WorldServer, entityItem: EntityItem, type: Item, event: ItemExpireEvent): Boolean {
    val fakePlayer = FakerPlayer(world, entityItem)
    val initialCount = fakePlayer.heldItemMainhand.count

    var actionTaken = false

    for(pos in blockAreaOfEffectForEntityAirLast(world, entityItem, true)) {
        if (attemptUseStackOnBlock(world, fakePlayer, pos) == EnumActionResult.SUCCESS)
            actionTaken = true
        if (fakePlayer.heldItemMainhand.isEmpty ||
                fakePlayer.heldItemMainhand.itemDamage > fakePlayer.heldItemMainhand.maxDamage)
            return true
        if (fakePlayer.heldItemMainhand.count < initialCount)
            break
    }

    if (cfg!!.antilag && type is ItemEgg) entityItem.item = ItemStack(Items.SNOWBALL, entityItem.item.count)

    for (i in (1..3)) {
        fakePlayer.randomLook()
        if (EnumActionResult.SUCCESS == fakePlayer.interactionManager.processRightClick(
                fakePlayer, world, fakePlayer.heldItemMainhand, EnumHand.MAIN_HAND
        )) {
            actionTaken = true
            break
        }
    }

    if (!actionTaken) {
        fakePlayer.lookDown()
        if (EnumActionResult.SUCCESS == fakePlayer.interactionManager.processRightClick(
            fakePlayer, world, fakePlayer.heldItemMainhand, EnumHand.MAIN_HAND
        )) {
            actionTaken = true
        }
    }

    if (fakePlayer.heldItemMainhand == entityItem.item && fakePlayer.heldItemMainhand.count == initialCount)
        fakePlayer.heldItemMainhand.shrink(1)

    entityItem.item = fakePlayer.heldItemMainhand

    when(type) {
        Items.ENDER_PEARL -> {
            event.extraLife = 200
            event.isCanceled = true
        }
        else -> scatterRemainderToTheWinds(world, entityItem)
    }

    return entityItem.item.count != 0 || actionTaken
}

fun attemptUseStackOnBlock(world: WorldServer, fakePlayer: FakerPlayer, blockPos: BlockPos): EnumActionResult {
    return fakePlayer.interactionManager.processRightClickBlock(fakePlayer, world, fakePlayer.heldItemMainhand,
            EnumHand.MAIN_HAND, blockPos, EnumFacing.UP, 0f, 0f, 0f)
}