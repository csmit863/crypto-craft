package me.callum.club_plugin.commands.admin

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class GetConfigCommand() : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.isOp) {
            sender.sendMessage("You do not have permission to use this command.")
            return true
        }

        var factory = "<factory address>"//blockcoin.factoryAddress
        var blockcoin = "<blockcoin address>"//blockcoin.blockcoinAddress
        Bukkit.getLogger().info("Factory address $factory")
        Bukkit.getLogger().info("Blockcoin address $blockcoin")

        sender.sendMessage("Factory address $factory")
        sender.sendMessage("Blockcoin address $blockcoin")

        return true
    }
}
