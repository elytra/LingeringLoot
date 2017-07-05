package lingerloot

import com.unascribed.lambdanetwork.DataType
import com.unascribed.lambdanetwork.LambdaNetwork
import lingerloot.hardcore.EntityItemExploding
import lingerloot.hardcore.HardcoreDespawnDispatcher
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.MathHelper
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.item.ItemExpireEvent
import net.minecraftforge.event.entity.item.ItemTossEvent
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.registry.EntityRegistry
import net.minecraftforge.fml.relauncher.Side
import java.lang.ref.WeakReference
import java.util.*

val MINECRAFT_LIFESPAN = EntityItem(null).lifespan // must match minecraft's default
val FAKE_DEFAULT_LIFESPAN = MINECRAFT_LIFESPAN + 1 // for preventing further substitutions

val CREATIVE_GIVE_DESPAWN_TICK = {val e = EntityItem(null); e.setAgeToCreativeDespawnTime(); e.extractAge() + 1}()
val CREATIVE_GIVE_DISAMBIGUATE = CREATIVE_GIVE_DESPAWN_TICK - 1
val CREATIVE_LIFESPAN = MINECRAFT_LIFESPAN - CREATIVE_GIVE_DESPAWN_TICK

val INFINITE_PICKUP_DELAY = {val e = EntityItem(null); e.setInfinitePickupDelay(); e.getPickupDelay()}()
val DEFAULT_PICKUP_DELAY = {val e = EntityItem(null); e.setDefaultPickupDelay(); e.getPickupDelay()}()

val ID_ENTITYITEMEXPLODING = 0

val jitteringItems = HashSet<WeakReference<EntityItem>>()

val GONNA_DESPAWN = "G"
val LAMBDA_NETWORK = LambdaNetwork.builder().channel("LingeringLoot").
        packet(GONNA_DESPAWN).boundTo(Side.CLIENT).with(DataType.INT, "id").
            handledOnMainThreadBy { entityPlayer, token ->
                (entityPlayer.entityWorld.getEntityByID(token.getInt("id")) as? EntityItem).ifAlive()?.let {
                    it.lifespan = it.age + JITTER_TIME
                    jitteringItems += WeakReference(it)
                }
            }.
        build()

val JITTER_TIME = 300

val rand = Random()
const val MODID = "lingeringloot"

@Mod(modid = MODID, version = "2.5", acceptableRemoteVersions="*")
class LingeringLoot {
    @Mod.EventHandler
    fun preInit (event: FMLPreInitializationEvent) {
        MinecraftForge.EVENT_BUS.register(EventHandler(LingeringLootConfig(event.modConfigurationDirectory.resolve("lingeringloot.cfg"))))
        EntityRegistry.registerModEntity(ResourceLocation(MODID, "EntityItemExploding"), EntityItemExploding::class.java, "Exploding Item",
                ID_ENTITYITEMEXPLODING, this, 64, 15, true)
    }
}

private fun fallThrough(vararg vals: Int): Int {
    for (i in vals) if (i >= 0) return i
    return FAKE_DEFAULT_LIFESPAN
}

class DespawnTimes(playerDrop: Int, playerKill: Int, playerMine: Int, mobDrop: Int, playerThrow: Int,
                               playerCaused: Int, other: Int, creative: Int, val shitTier: Int) {
    val playerDrop  = fallThrough(playerDrop, playerCaused, mobDrop, other)
    val playerKill  = fallThrough(playerKill, playerCaused, mobDrop, other)
    val playerMine  = fallThrough(playerMine, playerCaused, other)
    val mobDrop     = fallThrough(mobDrop, other)
    val playerThrow = fallThrough(playerThrow, playerCaused, other)
    val other       = fallThrough(other)
    val creative    = fallThrough(creative, CREATIVE_LIFESPAN)
}

val prescreen = HashSet<EntityItem>()

class EventHandler(config: LingeringLootConfig) {
    val despawnTimes = config.despawns
    val shitTier= config.shitTier
    val shitTierMods = config.shitTierMods
    val hardcore = config.hardcore
    val minedPickupDelay = config.minedPickupDelay
    val jitterSluice by lazy { JitterNotificationQueue() }

    private fun adjustDespawn(itemDrop: EntityItem, target: Int) {
        if (itemDrop.lifespan == MINECRAFT_LIFESPAN) {
            val item = itemDrop.item.item
            itemDrop.lifespan =
                    if (despawnTimes.shitTier >= 0 &&
                            (item in shitTier || item.registryName?.resourceDomain in shitTierMods))
                        despawnTimes.shitTier
                    else
                        target
        }

        prescreen.add(itemDrop)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerTossItem(event: ItemTossEvent) {
        if (! event.entityItem.entityWorld.isRemote)
            adjustDespawn(event.entityItem, despawnTimes.playerThrow)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDropsEvent(event: LivingDropsEvent) {
        if (event.entity.entityWorld.isRemote) return

        val target = if (event.entityLiving is EntityPlayer)
                despawnTimes.playerDrop
            else if (event.source.immediateSource is EntityPlayer || event.source.trueSource is EntityPlayer)
                despawnTimes.playerKill else despawnTimes.mobDrop

        for (drop in event.drops) adjustDespawn(drop, target)
    }

    var playerHarvested = mutableSetOf<ItemStack>()

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onHarvestDrops(event: HarvestDropsEvent) {
        if (! (event.harvester?.entityWorld?.isRemote?:true))
            if (event.harvester != null && event.harvester !is FakePlayer)
                playerHarvested = event.drops.toMutableSet()
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onEntitySpawn(event: EntityJoinWorldEvent) {
        if (event.entity.entityWorld.isRemote) return

        val entity = event.entity
        if (entity is EntityItem) {
            val target = if (playerHarvested.remove(entity.item)) {
                if (minedPickupDelay != DEFAULT_PICKUP_DELAY) entity.setPickupDelay(minedPickupDelay)
                despawnTimes.playerMine
            } else {
                despawnTimes.other
            }

            adjustDespawn(entity, target)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onItemDespawn(event: ItemExpireEvent) {
        if (hardcore && !event.entity.entityWorld.isRemote)
            HardcoreDespawnDispatcher.dispatch(event)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.START) {
            prescreen.removeAll {
                correctForCreativeGive(it)
                jitterSluice.prepareToDie(it)
                true
            }
            jitterSluice.tick()
        }
    }

    /**
     * attempts to detect /give and creative-mode-dropped items to restore the expected 1 minute despawn timer
     * @return whether change was required
     */
    fun correctForCreativeGive(item: EntityItem) {
        if (detectCreativeGiveSecondTick(item))
            item.lifespan = CREATIVE_GIVE_DESPAWN_TICK + despawnTimes.creative
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) {
            jitteringItems.filterInPlace {
                it.get().ifAlive()?.let { entity ->
                    val ttl = Math.max(1, MathHelper.sqrt((entity.lifespan - entity.age).toFloat()).toInt())
                    if (rand.nextInt(ttl) == 0)
                        entity.hoverStart = (rand.nextDouble() * Math.PI * 2.0).toFloat()
                    true
                } ?: false
            }
        }
    }
}