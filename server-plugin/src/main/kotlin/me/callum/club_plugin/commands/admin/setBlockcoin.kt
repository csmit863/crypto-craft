package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.config.AdminCommand
import me.callum.club_plugin.config.ServerConfig
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class SetBlockcoinCommand : AdminCommand() {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (!ensureOp(sender)) return true

        if (args.size != 1 || !args[0].startsWith("0x") || args[0].length != 42) {
            sender.sendMessage("Usage: /setblockcoin <0x...address>")
            return true
        }

        ServerConfig.setBlockcoin(args[0])

        sender.sendMessage("Blockcoin address updated.")
        return true
    }
}
