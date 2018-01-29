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
    override fun getName() = "llreload"
    override fun getUsage(sender: ICommandSender?) = "/llreload"

    override fun execute(server: MinecraftServer?, sender: ICommandSender?, args: Array<out String>?) {
        sender?.sendMessage(TextComponentString(cfg!!.reloadRules().map({ it }, { it })))
        sender?.sendMessage(TextComponentString(cfg!!.reloadDimRules().map({ it }, { it })))
    }
}