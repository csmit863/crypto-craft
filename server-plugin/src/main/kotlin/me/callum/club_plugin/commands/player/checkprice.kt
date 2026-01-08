package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.WalletManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class CheckPriceCommand() : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        

        if (args.size != 1) {
            sender.sendMessage(Component.text("Usage: /price <item>").color(TextColor.color(255, 0, 0)))
            return true
        }

        val itemName = args[0]

        val material = Material.matchMaterial(itemName)

        if (material == null) {
            sender.sendMessage(Component.text("Unknown item type: $itemName").color(TextColor.color(255, 0, 0)))
            return true
        }

        // get the price of the item via the uniswap interface
        val price = 1 // default exchange of 1 blockcoin

        sender.sendMessage(Component.text("1 ${material.name.lowercase().replace("_", " ")} costs ${price} blockcoins.")
            .color(TextColor.color(0, 255, 0)))

        return true
    }
}
