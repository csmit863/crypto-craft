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

/**
 * To sell items, some contracts have to exist:
 * 1. The token contracts for Blockcoin and the specified item
 * 2. The pair factory contract
 * 3. The pair contract for Blockcoin and the tokenized item
 * If these contracts do not exist, they must be created.
 */

class BuyItemsCommand(private val walletManager: WalletManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can buy items!").color(TextColor.color(255, 0, 0)))
            return true
        }

        if (args.size != 2) {
            sender.sendMessage(Component.text("Usage: /buy <item> <amount>").color(TextColor.color(255, 0, 0)))
            return true
        }

        val itemName = args[0]
        val amount = args[1].toIntOrNull()

        val material = Material.matchMaterial(itemName)

        if (material == null) {
            sender.sendMessage(Component.text("Unknown item type: $itemName").color(TextColor.color(255, 0, 0)))
            return true
        }

        if (amount == null || amount <= 0) {
            sender.sendMessage(Component.text("Invalid amount: must be a positive number.").color(TextColor.color(255, 0, 0)))
            return true
        }

        val itemToAdd = ItemStack(material, amount)
        sender.inventory.addItem(itemToAdd)

        sender.sendMessage(Component.text("You received $amount ${material.name.lowercase().replace("_", " ")}.")
            .color(TextColor.color(0, 255, 0)))

        return true
    }
}
