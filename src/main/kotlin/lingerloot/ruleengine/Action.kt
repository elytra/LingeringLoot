package lingerloot.ruleengine

import capitalthree.ruleengine.Effect
import capitalthree.ruleengine.EvaluationContext
import lingerloot.volatility.despawnHandlerSetsByShort
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary

class EntityItemCTX(val item: EntityItem, val causeMask: Int): EvaluationContext() {
    val oreIds = OreDictionary.getOreIDs(item.item).toSet()
    val classMask = getClassMask(item.item.item)
}


val expectedEffectTypes = setOf(0, 1, 2, 3)

val NOVOL = VolatileEffect(null)
val NOTIMER = TimerEffect(null)
val NODELAY = PickupDelayEffect(null)
val NOTF = TransformEffect(null)

class TimerEffect(val timer: Int?): Effect<EntityItemCTX> {
    override fun getSlot() = 0
    override fun accept(ctx: EntityItemCTX) = timer?.let{
        ctx.item.lifespan = it
    }?:Unit
}

class PickupDelayEffect(val delay: Int?): Effect<EntityItemCTX> {
    override fun getSlot() = 1
    override fun accept(ctx: EntityItemCTX) = delay?.let{
        ctx.item.setPickupDelay(it)
    }?:Unit
}

class VolatileEffect(val handlerSet: Short?): Effect<EntityItemCTX> {
    override fun getSlot() = 2
    override fun accept(ctx: EntityItemCTX) {
        if (handlerSet != null) {
            ctx.item.getCapability(TOUCHED_CAP!!, null)?.despawnHandler = despawnHandlerSetsByShort[handlerSet]
        }
    }
}

class TransformEffect(val replace: ItemPredicate?): Effect<EntityItemCTX> {
    override fun getSlot() = 3
    override fun accept(ctx: EntityItemCTX) = replace?.let{
        val i = ctx.item
        i.item = ItemStack(replace.item, i.item.count, replace.damage?:0)
    }?:Unit
}