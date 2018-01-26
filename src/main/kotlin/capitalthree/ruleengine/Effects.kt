package capitalthree.ruleengine

import java.util.function.Consumer

interface Effect<X>: Consumer<X> {
    fun getSlot(): Int
}

class EffectBuffer<X>(private val expectedSlots: Set<Int>): Effect<X> {
    override fun getSlot() = -1
    private val effects = mutableMapOf<Int, Effect<X>>()

    fun caresAbout(newEffects: List<Effect<X>>) = newEffects.any{it.getSlot() in expectedSlots && it.getSlot() !in effects}

    fun update(newEffects: List<Effect<X>>) = newEffects.forEach{
        if (it.getSlot() in expectedSlots)
            effects.putIfAbsent(it.getSlot(), it)
    }

    fun full() = expectedSlots.all{it in effects}

    override fun accept(target: X) = effects.values.forEach{it.accept(target)}
}