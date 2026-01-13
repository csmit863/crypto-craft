package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.economy.Blockcoin
import me.callum.club_plugin.economy.Uniswap
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

        var factory = Uniswap.v2factoryAddress
        var router = Uniswap.v2routerAddress
        var blockcoin = Blockcoin.address
        Bukkit.getLogger().info("Factory address $factory")
        Bukkit.getLogger().info("Router address $router")
        Bukkit.getLogger().info("Blockcoin address $blockcoin")

        sender.sendMessage("Factory address $factory")
        sender.sendMessage("Router address $router")
        sender.sendMessage("Blockcoin address $blockcoin")

        return true
    }
}
