package lingerloot

import com.elytradev.concrete.network.Message
import com.elytradev.concrete.network.NetworkContext
import com.elytradev.concrete.network.annotation.field.MarshalledAs
import com.elytradev.concrete.network.annotation.type.ReceivedOn
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.fml.relauncher.Side

val CONTEXT = NetworkContext.forChannel("lingerloot")
fun initMessageContexts() {
    CONTEXT.register(TriggerJitterMessage::class.java)
}

@ReceivedOn(Side.CLIENT)
class TriggerJitterMessage(): Message(CONTEXT) {
    @MarshalledAs("i32")
    var id: Int = -1

    constructor(id: Int): this() {this.id = id}

    override fun handle(entityPlayer: EntityPlayer) {
        (entityPlayer.entityWorld.getEntityByID(id) as? EntityItem).ifAlive()?.let {
            it.lifespan = it.age + JITTER_TIME
            jitteringItems += it
        }
    }
}