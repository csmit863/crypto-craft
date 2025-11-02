package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.economy.Blockcoin
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SetTokenCommand(private val blockcoin: Blockcoin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.isOp) {
            sender.sendMessage("You do not have permission to use this command.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("Usage: /setTokenAddress <address>")
            return true
        }

        val newAddress = args[0]
        blockcoin.contractAddress = newAddress
        Bukkit.getLogger().info("Token address updated to: $newAddress")
        sender.sendMessage("Token address updated successfully.")

        return true
    }
}
