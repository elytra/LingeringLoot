package lingerloot.ruleengine

import com.elytradev.concrete.common.Either

class Effect {

}

fun effect(s: String): Either<Effect, String> {
    // TODO
    return Either.right("ohfuck")
}