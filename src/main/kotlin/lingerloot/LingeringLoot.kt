package lingerloot

import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraft.nbt.NBTTagByte
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.item.ItemTossEvent
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

val MINECRAFT_LIFESPAN = EntityItem(null).lifespan // must match minecraft's default
val FAKE_DEFAULT_LIFESPAN = MINECRAFT_LIFESPAN + 1 // for preventing further substitutions
val PLAYER_MINED_TAG = "PlayerMinedThisItem"
val PLAYER_MINED_V: Byte = 1
val B0: Byte = 0

@Mod(modid = "LingeringLoot", version = "1.0")
class LingeringLoot {
    @Mod.EventHandler
    fun preInit (event: FMLPreInitializationEvent) {
        val c = LingeringLootConfig(event.modConfigurationDirectory.resolve("lingeringloot.cfg"))
        MinecraftForge.EVENT_BUS.register(EventHandler(c.despawns, c.shitTier, c.shitTierMods))
    }
}

private fun fallThrough(vararg vals: Int): Int {
    for (i in vals) if (i >= 0) return i
    return FAKE_DEFAULT_LIFESPAN
}

class DespawnTimes private constructor(
        val playerDrop: Int,  val playerKill: Int,
        val playerMine: Int,  val mobDrop: Int,
        val playerThrow: Int, val other: Int,
        val shitTier: Int
) {
    constructor(playerDrop: Int, playerKill: Int, playerMine: Int, mobDrop: Int, playerThrow: Int,
                playerCaused: Int, other: Int, shitTier: Int): this(
            fallThrough(playerDrop, playerCaused, mobDrop, other), fallThrough(playerKill, playerCaused, mobDrop, other),
            fallThrough(playerMine, playerCaused, other),          fallThrough(mobDrop, other),
            fallThrough(playerThrow, playerCaused, other),         fallThrough(other),
            shitTier
    )
}

class EventHandler(val despawnTimes: DespawnTimes, val shitTier: Set<Item>, val shitTierMods: Set<String>) {
    private fun adjustDespawn(itemDrop: EntityItem, target: Int) {
        if (itemDrop.lifespan == MINECRAFT_LIFESPAN) {
            val item = itemDrop.entityItem.item
            itemDrop.lifespan =
                    if (despawnTimes.shitTier >= 0 &&
                            (item in shitTier || ResourceLocation(item.registryName).resourceDomain in shitTierMods))
                        despawnTimes.shitTier
                    else
                        target
        }

    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerTossItem(event: ItemTossEvent) {
        adjustDespawn(event.entityItem, despawnTimes.playerThrow)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDropsEvent(event: LivingDropsEvent) {
        val target = if (event.entityLiving is EntityPlayer)
                despawnTimes.playerDrop
            else if (event.source.entity is EntityPlayer)
                despawnTimes.playerKill else despawnTimes.mobDrop

        for (drop in event.drops) adjustDespawn(drop, target)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onHarvestDrops(event: HarvestDropsEvent) {
        if (event.harvester != null) // if player-harvested, inject NBT tag for EntityJoinWorldEvent
            for (drop in event.drops) drop.setTagInfo(PLAYER_MINED_TAG, NBTTagByte(PLAYER_MINED_V))
    }

    // highest priority so we minimize the chance of other code seeing our injected NBT
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onEntitySpawn(event: EntityJoinWorldEvent) {
        val entity = event.entity
        if (entity is EntityItem) {
            val compound = entity.entityItem.tagCompound
            val target = if (compound?.getByte(PLAYER_MINED_TAG)?:B0 == PLAYER_MINED_V) {
                compound.removeTag(PLAYER_MINED_TAG)
                if (compound.hasNoTags())
                    entity.entityItem.tagCompound = null
                despawnTimes.playerMine
            } else
                despawnTimes.other

            adjustDespawn(entity, target)
        }
    }
}
