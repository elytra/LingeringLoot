package lingerloot

import lingerloot.ruleengine.CausePredicates
import lingerloot.ruleengine.EvaluationContext
import lingerloot.volatility.EntityItemExploding
import lingerloot.ruleengine.registerCapabilities
import lingerloot.volatility.DespawnDispatcher
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.item.ItemExpireEvent
import net.minecraftforge.event.entity.item.ItemTossEvent
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.registry.EntityRegistry
import org.apache.logging.log4j.Logger
import java.util.*

val MINECRAFT_LIFESPAN = EntityItem(null).lifespan // must match minecraft's default
val FAKE_DEFAULT_LIFESPAN = MINECRAFT_LIFESPAN + 1 // for preventing further substitutions

val CREATIVE_GIVE_DESPAWN_TICK = {val e = EntityItem(null); e.setAgeToCreativeDespawnTime(); e.extractAge() + 1}()
val CREATIVE_GIVE_DISAMBIGUATE = CREATIVE_GIVE_DESPAWN_TICK - 1
val CREATIVE_LIFESPAN = MINECRAFT_LIFESPAN - CREATIVE_GIVE_DESPAWN_TICK

val INFINITE_PICKUP_DELAY = {val e = EntityItem(null); e.setInfinitePickupDelay(); e.getPickupDelay()}()
val DEFAULT_PICKUP_DELAY = {val e = EntityItem(null); e.setDefaultPickupDelay(); e.getPickupDelay()}()

val ID_ENTITYITEMEXPLODING = 0

val jitteringItems = Collections.newSetFromMap(WeakHashMap<EntityItem, Boolean>())

val JITTER_TIME = 300

val rand = Random()
const val MODID = "lingeringloot"

var logger: Logger? = null

@SidedProxy(clientSide = "lingerloot.ClientProxy", serverSide = "lingerloot.ServerProxy") var proxy: CommonProxy? = null

@Mod(modid = MODID, version = "3.0", acceptableRemoteVersions="*")
class LingeringLoot {
    @Mod.EventHandler
    fun preInit (event: FMLPreInitializationEvent) {
        logger = event.modLog

        MinecraftForge.EVENT_BUS.register(EventHandler(LingeringLootConfig(event.modConfigurationDirectory)))

        EntityRegistry.registerModEntity(ResourceLocation(MODID, "EntityItemExploding"), EntityItemExploding::class.java, "Exploding Item",
                ID_ENTITYITEMEXPLODING, this, 64, 15, true)

        registerCapabilities()

        proxy?.preInit(event)
    }
}

private fun fallThrough(vararg vals: Int) = vals.firstOrNull{it >= 0} ?: FAKE_DEFAULT_LIFESPAN

//class DespawnTimes(playerDrop: Int, playerKill: Int, playerMine: Int, mobDrop: Int, playerThrow: Int,
//                               playerCaused: Int, other: Int, creative: Int, val shitTier: Int) {
//    val playerDrop  = fallThrough(playerDrop, playerCaused, mobDrop, other)
//    val playerKill  = fallThrough(playerKill, playerCaused, mobDrop, other)
//    val playerMine  = fallThrough(playerMine, playerCaused, other)
//    val mobDrop     = fallThrough(mobDrop, other)
//    val playerThrow = fallThrough(playerThrow, playerCaused, other)
//    val other       = fallThrough(other)
//    val creative    = fallThrough(creative, CREATIVE_LIFESPAN)
//}

/*

            prescreen.add(i)
 */

val prescreen = mutableMapOf<EntityItem, Int>()

class EventHandler(val cfg: LingeringLootConfig) {
    val jitterSluice by lazy { JitterNotificationQueue() }

    fun applyRules(item: EntityItem, causeMask: Int) = cfg.rules.mapLeft {
        EvaluationContext(it, item, causeMask).act()
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerTossItem(event: ItemTossEvent) {
        if (! event.entityItem.entityWorld.isRemote)
            prescreen.putIfAbsent(event.entityItem, CausePredicates.PLAYERTOSS.mask)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDropsEvent(event: LivingDropsEvent) {
        if (event.entity.entityWorld.isRemote) return

        val target = if (event.entityLiving is EntityPlayer)
                CausePredicates.PLAYERDROP
            else if (event.source.immediateSource is EntityPlayer || event.source.trueSource is EntityPlayer)
                CausePredicates.PLAYERLOOT else CausePredicates.MOBDROP

        for (drop in event.drops) prescreen.putIfAbsent(drop, target.mask)
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
                if (cfg.minedPickupDelay != DEFAULT_PICKUP_DELAY) entity.setPickupDelay(cfg.minedPickupDelay)
                CausePredicates.PLAYERMINE
            } else {
                CausePredicates.OTHER
            }

            prescreen.putIfAbsent(entity, target.mask)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onItemDespawn(event: ItemExpireEvent) {
        if (!event.entity.entityWorld.isRemote)
            DespawnDispatcher.dispatch(cfg, event)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.START) {
            prescreen.forEach { item, causemask ->
                val causemaskCreative = if (detectCreativeGiveSecondTick(item))
                    CausePredicates.CREATIVEGIVE.mask
                else
                    causemask

                applyRules(item, causemaskCreative)
                jitterSluice.prepareToDie(item)
            }
            prescreen.clear()
            jitterSluice.tick()
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) {jitteringItems.filterInPlace{it?.ifAlive() != null}}
    }
}