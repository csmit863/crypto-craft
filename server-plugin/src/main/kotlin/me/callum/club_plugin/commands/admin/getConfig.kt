package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.economy.BlockcoinManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class GetConfigCommand(private val blockcoin: BlockcoinManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.isOp) {
            sender.sendMessage("You do not have permission to use this command.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("Usage: /getConfig")
            return true
        }

        var factory = blockcoin.factoryAddress
        var blockcoin = blockcoin.blockcoinAddress
        Bukkit.getLogger().info("Factory address $factory")
        Bukkit.getLogger().info("Blockcoin address $blockcoin")

        sender.sendMessage("Factory address $factory")
        sender.sendMessage("Blockcoin address $blockcoin")

        return true
    }
}
