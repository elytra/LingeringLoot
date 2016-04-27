package lingerloot

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import cpw.mods.fml.common.eventhandler.EventPriority
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraft.nbt.NBTTagByte
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.item.ItemTossEvent
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent

private val MINECRAFT_LIFESPAN = EntityItem(null).lifespan // must match minecraft's default
private val FAKE_DEFAULT_LIFESPAN = MINECRAFT_LIFESPAN + 1 // for preventing further substitutions
private val PLAYER_MINED_TAG = "PlayerMinedThisItem"
private val PLAYER_MINED_V: Byte = 1

@Mod(modid = "LingeringLoot", version = "1.0")
class LingeringLoot {
    @Mod.EventHandler
    fun preInit (event: FMLPreInitializationEvent) {
        val config = Configuration(event.modConfigurationDirectory.resolve("lingeringloot.cfg"))

        val timeCategory = "despawn times"
        val shitTierCategory = "shit tier"
        config.setCategoryComment(timeCategory,
                "Despawn times are in seconds.  Minecraft's default is 300.  Use -1 to defer to less granular settings\n" +
                "eg: player drops and player-killed mob drops are both types of mob drops, and player-caused drops.\n" +
                "The order of precedence is: player drops, player-killed mob drops or player-mined items or player-thrown\n" +
                "items, player-caused drops, mob drops, and finally other.")

        fun configOptionSecs(category: String, name: String, default: Int): Int {
            val r = 20 * config.get(category, name, default).getInt(default)
            return if (r == MINECRAFT_LIFESPAN) FAKE_DEFAULT_LIFESPAN else r  // important to differentiate 6000 from -1
        }

        val despawns = DespawnTimes(
                configOptionSecs(timeCategory, "player drops", 3600),
                configOptionSecs(timeCategory, "player-killed mob drops", -1),
                configOptionSecs(timeCategory, "player-mined items", -1),
                configOptionSecs(timeCategory, "mob drops", -1),
                configOptionSecs(timeCategory, "player-thrown items", -1),
                configOptionSecs(timeCategory, "player-caused drops", 1800),
                configOptionSecs(timeCategory, "other", 900),
                configOptionSecs(shitTierCategory, "shit despawn time", 300)
        )

        config.setCategoryComment(shitTierCategory, "The despawn time for shit-tier items, if set, overrides all other settings.")
        val shitTier = config.get(shitTierCategory, "shit tier items", "cobblestone,snowball").string.split(",").
                map{b -> Item.itemRegistry.getObject(b) as? Item}.filterNotNull().
                toSet()

        if (config.hasChanged()) config.save()

        MinecraftForge.EVENT_BUS.register(EventHandler(despawns, shitTier))
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

class EventHandler(val despawnTimes: DespawnTimes, val shitTier: Set<Item>) {
    private fun adjustDespawn(item: EntityItem, target: Int) {
        if (item.lifespan == MINECRAFT_LIFESPAN)
            item.lifespan = if (despawnTimes.shitTier >= 0 && item.entityItem.item in shitTier)
                despawnTimes.shitTier else target
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
            val target = if (compound?.getByte(PLAYER_MINED_TAG)?:0 == PLAYER_MINED_V) {
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
