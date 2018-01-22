package lingerloot.ruleengine

import com.elytradev.concrete.common.Either
import lingerloot.*
import lingerloot.volatility.despawnHandlerSetsByShort
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary
import java.util.function.Consumer
import kotlin.reflect.KClass

class EvaluationContext(val rules: Rules, val item: EntityItem, val causeMask: Int) {
    val oreIds = OreDictionary.getOreIDs(item.item).toSet()
    val classMask = getClassMask(item.item.item)
    val tagCache = mutableMapOf<String, Boolean>()
    val tagRecursionStack = mutableSetOf<String>()

    private fun evaluate(): EffectBuffer {
        val buf = EffectBuffer()
        try {
            rules.forEach{
                if (buf.caresAbout(it.effects) && it.predicates.resolve(this)) {
                    buf.update(it.effects)
                    if (buf.full()) return buf
                }
            }
        } catch (e: Exception) {
            logger?.error("Error in Lingering Loot rules engine: ${e.message}")
        }
        return buf
    }

    fun act() = evaluate().accept(item)
}

val expectedEffectTypes = setOf(TimerEffect::class, VolatileEffect::class,
        TransformEffect::class, PickupDelayEffect::class)
class EffectBuffer: Effect {
    private val effects = mutableMapOf<KClass<out Effect>, Effect>()

    fun caresAbout(newEffects: List<Effect>) = newEffects.any{!effects.containsKey(it::class)}

    fun update(newEffects: List<Effect>) = newEffects.forEach{
        effects.putIfAbsent(it::class, it)
    }

    fun full() = expectedEffectTypes.all{it in effects}

    override fun accept(target: EntityItem) = effects.values.forEach{it.accept(target)}
}

fun effect(s: String): Either<Iterable<Effect>, String> {
    val word = s.takeWhile{it != '('}
    val param = if (word.length < s.length)
        if (s.last() == ')')
            s.substring(word.length+1, s.length-1)
        else
            return Either.right("Effect \"$s\" has '(' but doesn't end with ')'")
    else
        null

    return Either.left<Iterable<Effect>, String>(when (word) {
        "timer" -> {listOf(if (param == null) {
            NOTIMER
        } else {
            val seconds = param.toDoubleOrNull() ?: return Either.right("Invalid double: \"$param\"")
            val ticks = (seconds * 20).toInt()
            TimerEffect(
                when (ticks) {
                    MINECRAFT_LIFESPAN -> FAKE_DEFAULT_LIFESPAN  // important to differentiate 6000 from -1
                    CREATIVE_GIVE_DESPAWN_TICK -> CREATIVE_GIVE_DISAMBIGUATE // differentiate /give fakeitems
                    else -> ticks
                }
            )
        })}

        "convert" -> {listOf(if (param == null) {
            NOTF
        } else {
            val lookup = lookupItem(param)
            TransformEffect(
                if (lookup.isLeft) lookup.leftNullable
                else return Either.right(lookup.rightNullable)
            )
        })}

        "pickupdelay" -> {listOf(if (param == null) {
            NODELAY
        } else {
            PickupDelayEffect(param.toIntOrNull()?: return Either.right("Invalid int: \"$param\""))
        })}

        "despawn" -> {listOf(if (param == null) {
            NOVOL
        } else {
            if (param.length != 1) return Either.right("Despawn handler specifier must be one character")
            val handlerCode = param[0].toShort()
            if (handlerCode !in despawnHandlerSetsByShort) return Either.right("Invalid despawn handler code: $param")
            VolatileEffect(handlerCode)
        })}
        "nothing" -> {listOf(NOTIMER, NOVOL, NODELAY, NOTF)}
        else -> return Either.right("Invalid effect keyword: \"$word\"")
    })
}


val NOVOL = VolatileEffect(null)
val NOTIMER = TimerEffect(null)
val NODELAY = PickupDelayEffect(null)
val NOTF = TransformEffect(null)


typealias Effect = Consumer<EntityItem>

class TimerEffect(val timer: Int?): Effect {
    override fun accept(i: EntityItem) = timer?.let{
        i.lifespan = it
    }?:Unit
}

class PickupDelayEffect(val delay: Int?): Effect {
    override fun accept(i: EntityItem) = delay?.let{
        i.setPickupDelay(it)
    }?:Unit
}

class VolatileEffect(val handlerSet: Short?): Effect {
    override fun accept(i: EntityItem) {
        if (handlerSet != null) {
            i.getCapability(TOUCHED_CAP!!, null)?.despawnHandler = despawnHandlerSetsByShort[handlerSet]
        }
    }
}

class TransformEffect(val replace: ItemPredicate?): Effect {
    override fun accept(i: EntityItem) = replace?.let{
        i.item = ItemStack(replace.item, i.item.count, replace.damage?:0)
    }?:Unit
}