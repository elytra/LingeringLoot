package lingerloot.volatility

import com.mojang.authlib.GameProfile
import lingerloot.extractAge
import lingerloot.ifAlive
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.NetHandlerPlayServer
import net.minecraft.network.NetworkManager
import net.minecraft.network.Packet
import net.minecraft.tileentity.TileEntitySign
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
        if (holding != null) {
            setPosition(holding.posX, holding.posY, holding.posZ)
            setHeldItem(EnumHand.MAIN_HAND, holding.item)
        }
        NetHandlerPlayServer(null, FakeNetworkManager, this)
        rotationYawHead = (rand.nextDouble() * 360).toFloat()
        rotationYaw = rotationYawHead
    }

    fun randomLook() {
        rotationPitch = -(rand.nextDouble() * 90).toFloat()
        rotationYawHead = (rand.nextDouble() * 360).toFloat()
        rotationYaw = rotationYawHead
    }

    fun lookDown() {rotationPitch = 90f}

    override fun setPositionAndUpdate(x: Double, y: Double, z: Double) {
        setPosition(x, y, z) // there shall be no update!
        holding.ifAlive()?.let {
            it.setPositionAndUpdate(x, y, z)
            it.lifespan = it.extractAge() + 20
        }
    }

    /**
     * the fakest player must be a sneaky player
     */
    override fun isSneaking(): Boolean {
        return true
    }

    val signs = arrayOf(
            "clean\nup\nafter\nyourself", "pick\nup\nyour\ntoys", "don't\nleave\nthings\nout",
            "\nI am\na sign\n", "go\nclean\nyour\nroom", "thank\ngod\nfor\nme", "there\nis\nno\nspoon",
            "meow meow meow\nmeow meow meow\nmeow meow meow\nmeow meow meow", "don't\ndig\nstraight\ndown",
            "lingering\nloot\nhardcore\nmode", "don't\nlook\nbehind\nyou", "\nI can\nblock lava\n",
            "who\nleft\nme\nout?", "\nSSSsSSSsSSSSssss...\n\n", "good\nthing\nI wasn't\nTNT!",
            "\nwho made\na mess?\n", "never\ngonna\ngive\nyou up"
    )

    override fun openEditSign(signTile: TileEntitySign) = signTile.signText
            .zip(signs[rand.nextInt(signs.size)].split('\n'))
            .forEach{it.first.appendText(it.second)}
}