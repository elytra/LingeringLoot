package lingerloot.hardcore

import com.mojang.authlib.GameProfile
import lingerloot.ifAlive
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

    override fun isChannelOpen(): Boolean = true
}

val DROPS_PROFILE = GameProfile(UUID.randomUUID(), "The Drops")

class FakerPlayer(world: WorldServer, val holding: EntityItem?): FakePlayer(world, DROPS_PROFILE) {
    init {
        if (holding != null)
            setPosition(holding.posX, holding.posY, holding.posZ)
        NetHandlerPlayServer(null, FakeNetworkManager, this)
    }

    var holdingNoEntity: ItemStack? = null

    override fun getHeldItem(hand: EnumHand?): ItemStack = when(hand) {
        EnumHand.MAIN_HAND -> heldItemMainhand
        else -> super.getHeldItem(hand)
    }

    override fun getHeldItemMainhand(): ItemStack = holding?.item
            ?: holdingNoEntity
            ?: super.getHeldItemMainhand()

    override fun setHeldItem(hand: EnumHand, stack: ItemStack) = when (hand) {
        EnumHand.MAIN_HAND ->
            if (holding == null)
                holdingNoEntity = stack
            else
                holding.item = stack
        else -> super.setHeldItem(hand, stack)
    }

    fun randomLook() {
        rotationPitch = -(rand.nextDouble() * 90).toFloat()
        rotationYawHead = (rand.nextDouble() * 360).toFloat()
        rotationYaw = rotationYawHead
    }

    fun lookDown() {
        rotationPitch = 90f
    }

    override fun setPositionAndUpdate(x: Double, y: Double, z: Double) {
        setPosition(x, y, z) // there shall be no update!
        holding.ifAlive()?.setPositionAndUpdate(x, y, z)
    }

    /**
     * the fakest player must be a sneaky player
     */
    override fun isSneaking(): Boolean {
        return true
    }
}