package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.config.AdminCommand
import me.callum.club_plugin.config.ServerConfig
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
// UNISWAP factory
class SetFactoryCommand : AdminCommand() {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (!ensureOp(sender)) return true

        if (args.size != 1 || !isEthAddress(args[0])) {
            sender.sendMessage("Usage: /setfactory <0x...address>")
            return true
        }

        ServerConfig.setUniswapFactory(args[0])

        sender.sendMessage("Uniswap factory address updated.")
        return true
    }
}
