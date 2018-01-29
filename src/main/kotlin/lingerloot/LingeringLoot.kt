package lingerloot

import lingerloot.ruleengine.*
import lingerloot.volatility.EntityItemExploding
import lingerloot.volatility.DespawnDispatcher
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.item.ItemExpireEvent
import net.minecraftforge.event.entity.item.ItemTossEvent
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerStartingEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.registry.EntityRegistry
import org.apache.logging.log4j.Logger
import java.util.*

val MINECRAFT_LIFESPAN = EntityItem(null).lifespan // must match minecraft's default

val CREATIVE_GIVE_DESPAWN_TICK = {val e = EntityItem(null); e.setAgeToCreativeDespawnTime(); e.extractAge() + 1}()

val INFINITE_PICKUP_DELAY = {val e = EntityItem(null); e.setInfinitePickupDelay(); e.extractPickupDelay()}()
val DEFAULT_PICKUP_DELAY = {val e = EntityItem(null); e.setDefaultPickupDelay(); e.extractPickupDelay()}()

val jitteringItems = Collections.newSetFromMap(WeakHashMap<EntityItem, Boolean>())

val JITTER_TIME = 300

val rand = Random()
const val MODID = "lingeringloot"

var logger: Logger? = null

@SidedProxy(clientSide = "lingerloot.ClientProxy", serverSide = "lingerloot.ServerProxy") var proxy: CommonProxy? = null

@Mod(modid = MODID, version = "4.0", acceptableRemoteVersions="*")
class LingeringLoot {
    @Mod.EventHandler
    fun preInit (event: FMLPreInitializationEvent) {
        logger = event.modLog

        LingeringLootConfig(event.modConfigurationDirectory)
        MinecraftForge.EVENT_BUS.register(EventHandler)

        EntityRegistry.registerModEntity(ResourceLocation(MODID, "EntityItemExploding"), EntityItemExploding::class.java, "Exploding Item",
                0, this, 64, 15, true)

        registerCapabilities()
        initMessageContexts()

        proxy?.preInit(event)
    }

    @Mod.EventHandler
    fun start(e: FMLServerStartingEvent) = e.registerServerCommand(ReloadRulesCommand)
}


val prescreen = mutableMapOf<EntityItem, Int>()

object EventHandler {
    private val jitterSluice by lazy { JitterNotificationQueue() }

    fun applyRules(item: EntityItem, causeMask: Int) {
        if (item !is EntityItemExploding && item.extractPickupDelay() != INFINITE_PICKUP_DELAY && !item.item.isEmpty) // ignore cosmetic fake item or empty item
            LingerRulesEngine.act(EntityItemCTX(item, causeMask))?.let{logger?.error(it)}
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
                CausePredicates.PLAYERKILL else CausePredicates.MOBDROP

        for (drop in event.drops) prescreen.putIfAbsent(drop, target.mask)
    }

    private var playerHarvested = mutableSetOf<ItemStack>()

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
            DespawnDispatcher.dispatch(event)
    }

    @SubscribeEvent
    fun onCapabilityAttachEntity(e: AttachCapabilitiesEvent<Entity>) {
        if (e.`object` is EntityItem) {
            e.addCapability(ResourceLocation(MODID, "touched"), TouchedByLingeringLewd())
        }
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