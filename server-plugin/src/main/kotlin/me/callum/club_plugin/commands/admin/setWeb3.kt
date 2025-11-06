package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.economy.BlockcoinManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SetWeb3Command(private val blockcoin: BlockcoinManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.isOp) {
            sender.sendMessage("You do not have permission to use this command.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("Usage: /setWeb3 <url>")
            return true
        }

        val newRpcUrl = args[0]
        blockcoin.setWeb3(newRpcUrl)
        Bukkit.getLogger().info("Web3 updated to: $newRpcUrl")
        sender.sendMessage("Web3 updated successfully.")

        return true
    }
}
