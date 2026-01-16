package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.config.AdminCommand
import me.callum.club_plugin.config.ServerConfig
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class SetRouterCommand : AdminCommand() {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (!ensureOp(sender)) return true

        if (args.size != 1 || !isEthAddress(args[0])) {
            sender.sendMessage("Usage: /setrouter <0x...address>")
            return true
        }

        ServerConfig.setUniswapRouter(args[0])

        sender.sendMessage("Uniswap router address updated.")
        return true
    }
}
