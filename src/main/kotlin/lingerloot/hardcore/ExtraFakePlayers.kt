package lingerloot.hardcore

import com.mojang.authlib.GameProfile
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

fun makeFakerPlayer(world: WorldServer): FakePlayer {
    val player = FakePlayer(world, DROPS_PROFILE)
    NetHandlerPlayServer(null, FakeNetworkManager, player)
    return player
}