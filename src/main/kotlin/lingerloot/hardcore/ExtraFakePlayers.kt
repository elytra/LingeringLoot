package lingerloot.hardcore

import com.mojang.authlib.GameProfile
import net.minecraft.item.ItemStack
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.NetHandlerPlayServer
import net.minecraft.network.NetworkManager
import net.minecraft.network.Packet
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

    constructor(world: WorldServer, held: ItemStack): this(world) {
        fakeHeldItem = held
    }

    init {
        NetHandlerPlayServer(null, FakeNetworkManager, this)
    }

    override fun getHeldItemMainhand() = fakeHeldItem ?: super.getHeldItemMainhand()
}