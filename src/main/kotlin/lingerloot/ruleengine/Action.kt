package lingerloot.ruleengine

import com.elytradev.concrete.rulesengine.Effect
import com.elytradev.concrete.rulesengine.EvaluationContext
import lingerloot.volatility.despawnHandlerSetsByShort
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary
import kotlin.math.max

class EntityItemCTX(val item: EntityItem, val causeMask: Int): EvaluationContext() {
    val oreIds = OreDictionary.getOreIDs(item.item).toSet()
    val classMask = getClassMask(item.item.item)
}

val SLOT_TIMER = 0
val SLOT_DELAY = 1
val SLOT_VOL = 2
val SLOT_TF = 3

val expectedEffectTypes = setOf(SLOT_TIMER, SLOT_DELAY, SLOT_VOL, SLOT_TF)

val NOTIMER = NoopEffect(SLOT_TIMER)
val NODELAY = NoopEffect(SLOT_DELAY)
val NOVOL = NoopEffect(SLOT_VOL)
val NOTF = NoopEffect(SLOT_TF)

class NoopEffect(private val slot: Int): Effect<EntityItemCTX> {
    override fun getSlot() = slot
    override fun accept(ctx: EntityItemCTX) {}
}

class TimerEffect(val timer: Int): Effect<EntityItemCTX> {
    override fun getSlot() = SLOT_TIMER
    override fun accept(ctx: EntityItemCTX) {ctx.item.lifespan = timer}
}

class PickupDelayEffect(delay_in: Int): Effect<EntityItemCTX> {
    val delay = max(delay_in - 1, 0)
    override fun getSlot() = SLOT_DELAY
    override fun accept(ctx: EntityItemCTX) {ctx.item.setPickupDelay(delay)}
}

class VolatileEffect(val handlerSet: Short): Effect<EntityItemCTX> {
    override fun getSlot() = SLOT_VOL
    override fun accept(ctx: EntityItemCTX) {
        ctx.item.getCapability(TOUCHED_CAP!!, null)?.despawnHandler = despawnHandlerSetsByShort[handlerSet]
    }
}

class TransformEffect(val replace: ItemPredicate): Effect<EntityItemCTX> {
    override fun getSlot() = SLOT_TF
    override fun accept(ctx: EntityItemCTX) {
        val i = ctx.item
        i.item = ItemStack(replace.item, i.item.count, replace.damage?:0)
    }
}