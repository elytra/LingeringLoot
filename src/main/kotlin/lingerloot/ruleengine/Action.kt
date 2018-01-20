package lingerloot.ruleengine

import com.elytradev.concrete.common.Either
import lingerloot.logger
import lingerloot.lookupItem
import net.minecraft.entity.item.EntityItem
import net.minecraftforge.oredict.OreDictionary
import kotlin.reflect.KClass

class EvaluationContext(val rules: List<Rule>, val item: EntityItem, val causeMask: Int) {
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

    fun act() = evaluate().applyTo(item)
}

val expectedEffectTypes = setOf(TimerEffect::class, VolatileEffect::class, TransformEffect::class)
class EffectBuffer {
    private val effects = mutableMapOf<KClass<out Effect>, Effect>()

    fun caresAbout(newEffects: List<Effect>) = newEffects.any{caresAbout(it)}
    fun caresAbout(effect: Effect) = !effects.containsKey(effect::class)

    fun update(newEffects: List<Effect>) = newEffects.forEach{
        effects.putIfAbsent(it::class, it)
    }

    fun full() = expectedEffectTypes.all{it in effects}

    fun applyTo(target: EntityItem) = effects.values.forEach{it.applyTo(target)}
}

fun effect(s: String): Either<Effect, String> {
    val word = s.takeWhile{it != '('}
    val param = if (word.length <= s.length)
        if (s.last() == ')')
            s.substring(word.length+1, s.length-1)
        else
            return Either.right("Effect \"$s\" has '(' but doesn't end with ')'")
    else
        null

    return Either.left<Effect, String>(when (word) {
        "timer" -> {TimerEffect(param?.let{
            it.toIntOrNull()?: return Either.right("Invalid integer: \"$param\"")
        })}
        "convert" -> {TransformEffect(param?.let{
            val lookup = lookupItem(it)
            if (lookup.isLeft) lookup.leftNullable
            else return Either.right(lookup.rightNullable)
        })}
        "volatile" -> {VOL}
        "novolatile" -> {NOVOL}
        else -> return Either.right("Invalid effect keyword: \"$word\"")
    })
}

interface Effect {
    fun applyTo(i: EntityItem)
}

class TimerEffect(val timer: Int?): Effect {
    override fun applyTo(i: EntityItem) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class VolatileEffect(val volatile: Boolean): Effect {
    override fun applyTo(i: EntityItem) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
val VOL = VolatileEffect(true)
val NOVOL = VolatileEffect(false)

class TransformEffect(val result: ItemPredicate?): Effect {
    override fun applyTo(i: EntityItem) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}