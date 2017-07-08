package lingerloot.hardcore.handlers

import lingerloot.hardcore.FakerPlayer
import lingerloot.hardcore.jumpAround
import lingerloot.rand
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Enchantments
import net.minecraft.init.SoundEvents
import net.minecraft.item.ItemArrow
import net.minecraft.item.ItemBow
import net.minecraft.util.SoundCategory
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.item.ItemExpireEvent

fun spamArrows(world: WorldServer, entityItem: EntityItem, type: ItemArrow) {
    val fakePlayer = FakerPlayer(world, entityItem)
    for (i in 1..entityItem.item.count) {
        val arrow = type.createArrow(world, entityItem.item, fakePlayer)
        fakePlayer.randomLook()
        val vel = rand.nextFloat()*4
        arrow.setAim(entityItem, fakePlayer.rotationPitch, fakePlayer.rotationYaw, 0.0f, vel, 1.0f)
        arrow.isCritical = vel >= 3
        if (rand.nextFloat() < .05) arrow.setKnockbackStrength(rand.nextInt(3) + 1)
        if (rand.nextFloat() < .01) arrow.setFire(100)
        world.spawnEntity(arrow)
        world.playSound(null, entityItem.posX, entityItem.posY, entityItem.posZ,SoundEvents.ENTITY_ARROW_SHOOT,
                SoundCategory.PLAYERS, 1.0f, 1.0f / (rand.nextFloat() * 0.4f + 1.2f) + vel/1.5f)
    }
}

fun fireBow(world: WorldServer, entityItem: EntityItem, type: ItemBow, event: ItemExpireEvent) {
    val fakePlayer = FakerPlayer(world, entityItem)
    fakePlayer.randomLook()
    if (EnchantmentHelper.getEnchantmentLevel(Enchantments.INFINITY, entityItem.item) <= 0) return
    entityItem.item.onPlayerStoppedUsing(world, fakePlayer, 1)
    entityItem.jumpAround()
    event.extraLife = 20
    event.isCanceled = true
}