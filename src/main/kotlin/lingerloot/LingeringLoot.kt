package lingerloot

import com.unascribed.lambdanetwork.DataType
import com.unascribed.lambdanetwork.LambdaNetwork
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagByte
import net.minecraft.util.MathHelper
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
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.Side
import java.lang.ref.WeakReference
import java.util.*

val MINECRAFT_LIFESPAN = EntityItem(null).lifespan // must match minecraft's default
val FAKE_DEFAULT_LIFESPAN = MINECRAFT_LIFESPAN + 1 // for preventing further substitutions
val PLAYER_MINED_TAG = "PlayerMinedThisItem"
val PLAYER_MINED_V: Byte = 1
val B0: Byte = 0

val jitteringItems = LinkedList<WeakReference<EntityItem>>()

val GONNA_DESPAWN = "G"
val LAMBDA_NETWORK = LambdaNetwork.builder().channel("LingeringLoot").
        packet(GONNA_DESPAWN).boundTo(Side.CLIENT).with(DataType.INT, "id").
            handledOnMainThreadBy { entityPlayer, token ->
                (entityPlayer.worldObj.getEntityByID(token.getInt("id")) as? EntityItem).ifAlive()?.let {
                    it.lifespan = it.age + JITTER_TIME
                    jitteringItems += WeakReference(it)
                }
            }.
        build()

val JITTER_TIME = 200

val rand = Random()

@Mod(modid = "LingeringLoot", version = "1.0", acceptableRemoteVersions="*")
class LingeringLoot {
    @Mod.EventHandler
    fun preInit (event: FMLPreInitializationEvent) {
        MinecraftForge.EVENT_BUS.register(EventHandler(LingeringLootConfig(event.modConfigurationDirectory.resolve("lingeringloot.cfg"))))
    }
}

private fun fallThrough(vararg vals: Int): Int {
    for (i in vals) if (i >= 0) return i
    return FAKE_DEFAULT_LIFESPAN
}

class DespawnTimes(playerDrop: Int, playerKill: Int, playerMine: Int, mobDrop: Int, playerThrow: Int,
                               playerCaused: Int, other: Int, val shitTier: Int) {
    val playerDrop  = fallThrough(playerDrop, playerCaused, mobDrop, other)
    val playerKill  = fallThrough(playerKill, playerCaused, mobDrop, other)
    val playerMine  = fallThrough(playerMine, playerCaused, other)
    val mobDrop     = fallThrough(mobDrop, other)
    val playerThrow = fallThrough(playerThrow, playerCaused, other)
    val other       = fallThrough(other)
}

class EventHandler(config: LingeringLootConfig) {
    val despawnTimes = config.despawns
    val shitTier= config.shitTier
    val shitTierMods = config.shitTierMods
    val jitterSluice by lazy { JitterNotificationQueue() }

    private fun adjustDespawn(itemDrop: EntityItem, target: Int) {
        if (itemDrop.lifespan == MINECRAFT_LIFESPAN) {
            val item = itemDrop.entityItem.item
            itemDrop.lifespan =
                    if (despawnTimes.shitTier >= 0 &&
                            (item in shitTier || ResourceLocation(item.registryName).resourceDomain in shitTierMods))
                        despawnTimes.shitTier
                    else
                        target

            jitterSluice.prepareToDie(itemDrop)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerTossItem(event: ItemTossEvent) {
        if (! event.entityItem.worldObj.isRemote)
            adjustDespawn(event.entityItem, despawnTimes.playerThrow)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDropsEvent(event: LivingDropsEvent) {
        if (event.entity.worldObj.isRemote) return

        val target = if (event.entityLiving is EntityPlayer)
                despawnTimes.playerDrop
            else if (event.source.entity is EntityPlayer)
                despawnTimes.playerKill else despawnTimes.mobDrop

        for (drop in event.drops) adjustDespawn(drop, target)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onHarvestDrops(event: HarvestDropsEvent) {
        if (! (event.harvester?.worldObj?.isRemote?:true))
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

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.START) {
            jitterSluice.tick()
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) {
            jitteringItems.filterInPlace {
                it.get().ifAlive()?.let { entity ->
                    val ttl = Math.max(1, MathHelper.sqrt_double((entity.lifespan - entity.age).toDouble()).toInt())
                    if (rand.nextInt(ttl) == 0)
                        entity.hoverStart = (rand.nextDouble() * Math.PI * 2.0).toFloat();
                    true
                } ?: false
            }
        }
    }
}
