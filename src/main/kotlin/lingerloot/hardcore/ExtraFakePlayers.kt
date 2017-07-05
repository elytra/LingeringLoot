package lingerloot.hardcore

import com.mojang.authlib.GameProfile
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.NetHandlerPlayServer
import net.minecraft.network.NetworkManager
import net.minecraft.network.Packet
import net.minecraft.util.EnumHand
import net.minecraft.world.WorldServer
import net.minecraftforge.common.util.FakePlayer
import java.util.*

/**
 * Here we do some extra fake bullshit to keep minecraft from crapping itself on FakePlayers
 */
object FakeNetworkManager: NetworkManager(EnumPacketDirection.CLIENTBOUND) {
    override fun sendPacket(lolWhatPacket: Packet<*>?) {}
}

val DROPS_PROFILE = GameProfile(UUID.randomUUID(), "The Drops")

class FakerPlayer(world: WorldServer): FakePlayer(world, DROPS_PROFILE) {
    var fakeHeldItem: ItemStack? = null

    constructor(world: WorldServer, holding: EntityItem): this(world) {
        fakeHeldItem = holding.item
        setPosition(holding.posX, holding.posY, holding.posZ)
    }

    init {
        NetHandlerPlayServer(null, FakeNetworkManager, this)
    }

    override fun getHeldItem(hand: EnumHand?) = when(hand) {
        EnumHand.MAIN_HAND -> heldItemMainhand
        else -> super.getHeldItem(hand)
    }

    override fun getHeldItemMainhand() = fakeHeldItem ?: super.getHeldItemMainhand()

    override fun setHeldItem(hand: EnumHand, stack: ItemStack) = when (hand) {
        EnumHand.MAIN_HAND -> fakeHeldItem = stack
        else -> super.setHeldItem(hand, stack)
    }

    fun randomLook() {
        rotationPitch = -(rand.nextDouble() * 90).toFloat()
        rotationYawHead = (rand.nextDouble() * 360).toFloat()
        rotationYaw = rotationYawHead
    }
}