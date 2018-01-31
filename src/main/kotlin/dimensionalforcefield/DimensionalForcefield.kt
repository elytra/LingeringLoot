package dimensionalforcefield

import com.elytradev.concrete.rulesengine.Effect
import com.elytradev.concrete.rulesengine.EvaluationContext
import com.elytradev.concrete.rulesengine.RulesEngine
import com.elytradev.concrete.common.Either
import lingerloot.logger
import net.minecraft.entity.Entity
import net.minecraft.util.ResourceLocation
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent
import net.minecraftforge.fml.common.registry.ForgeRegistries
import java.util.function.Predicate

fun handleEvent(e: EntityTravelToDimensionEvent) {
    ForcefieldRules.act(TeleportCTX(e))?.let{logger.error("Error in DimensionalForcefield rules eval: $it")}
}

object ForcefieldRules: RulesEngine<TeleportCTX>() {
    override fun getDomainPredicates(): Map<Char, (String) -> Either<Predicate<TeleportCTX>, String>> = mapOf()

    override fun defaultPredicate(s: String): Either<out Predicate<TeleportCTX>, String> {
        val entEntr = ForgeRegistries.ENTITIES.getValue(ResourceLocation(s))?: return Either.right("Entity not found: \"$s\"")
        return Either.left(EntityClassPredicate(entEntr.entityClass))
    }

    override fun getEffectSlots(): Set<Int> = setOf(0)
    override fun parseEffect(s: String): Either<Iterable<Effect<TeleportCTX>>, String> = when (s) {
        "y" -> Either.left(listOf(CanTeleportEffect.YES))
        "n" -> Either.left(listOf(CanTeleportEffect.NO))
        else -> Either.right("Only y or n allowed")
    }

    private val interestingNumberList = listOf("to", "from", "x", "y", "z")
    override fun interestingNumberList() = interestingNumberList
    override fun genInterestingNumbers(from: TeleportCTX) = doubleArrayOf(from.e.dimension.toDouble(), from.e.entity.dimension.toDouble(),
            from.e.entity.posX, from.e.entity.posY, from.e.entity.posZ)

    override fun genDefaultRules() = "# Effects: y, n.  Predicates: entity resource names.  Variables: to/from (dim ids), x/y/z"
}

class TeleportCTX(val e: EntityTravelToDimensionEvent): EvaluationContext()

class EntityClassPredicate(val clazz: Class<out Entity>): Predicate<TeleportCTX> {
    override fun test(ctx: TeleportCTX) = clazz.isInstance(ctx.e.entity)
}

enum class CanTeleportEffect(val can: Boolean): Effect<TeleportCTX> {
    YES(true), NO(false);

    override fun getSlot(): Int = 0
    override fun accept(ctx: TeleportCTX) {
        if (!can) {
            ctx.e.isCanceled = true
        }
    }
}