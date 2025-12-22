package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.WalletManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class Bal(private val walletManager: WalletManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can check balances!").color(TextColor.color(255, 0, 0)))
            return true
        }

        val target: Player = if (args.isNotEmpty()) {
            sender.server.getPlayer(args[0]) ?: run {
                sender.sendMessage(Component.text("Player not found!").color(TextColor.color(255, 0, 0)))
                return true
            }
        } else sender

        val wallet = walletManager.getWallet(target.uniqueId)
        if (wallet == null) {
            sender.sendMessage(Component.text("${target.name} does not have a wallet!").color(TextColor.color(255, 0, 0)))
            return true
        }

        // Clickable & Copyable Wallet Address
        val walletComponent = Component.text("Wallet Address: ")
            .color(TextColor.color(0, 255, 255))
            .append(
                Component.text(wallet)
                    .color(TextColor.color(0, 255, 127))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy!").color(TextColor.color(255, 255, 0))))
                    .clickEvent(ClickEvent.copyToClipboard(wallet))
            )

        sender.sendMessage(walletComponent)

        // Fetch and display balance asynchronously
        walletManager.getBalance(target.uniqueId).thenAccept { balance ->
            val roundedBalance = String.format("%.5f", balance)
            sender.sendMessage(
                Component.text("${target.name}'s Balance: $roundedBalance Blockcoins")
                    .color(TextColor.color(0, 255, 0))
            )
        }.exceptionally { e ->
            sender.sendMessage(Component.text("Error fetching balance: ${e.message}").color(TextColor.color(255, 0, 0)))
            null
        }

        return true
    }
}
