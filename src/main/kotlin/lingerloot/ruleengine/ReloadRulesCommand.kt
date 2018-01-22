package lingerloot.ruleengine

import lingerloot.cfg
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.network.rcon.RConConsoleSource
import net.minecraft.server.MinecraftServer
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.util.text.TextComponentString

object ReloadRulesCommand: CommandBase() {
    override fun getName() = "reloadll"
    override fun getUsage(sender: ICommandSender?) = "/reloadll"

    override fun execute(server: MinecraftServer?, sender: ICommandSender?, args: Array<out String>?) = sender?.let{
        if (when (it) {
            is EntityPlayerMP -> {server!!.playerList.oppedPlayers.getEntry(it.gameProfile)?.permissionLevel?:0 >= server.opPermissionLevel}
            is RConConsoleSource -> true
            is DedicatedServer -> true
            else -> false
        }) {
            it.sendMessage(TextComponentString(cfg!!.reloadRules().map({it}, {it})))
        }
    }?:Unit
}