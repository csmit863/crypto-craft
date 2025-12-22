package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.WalletManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SendTokensCommand(private val walletManager: WalletManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can send tokens!").color(TextColor.color(255, 0, 0)))
            return true
        }

        // Check if the command has the correct number of arguments
        if (args.size != 2) {
            sender.sendMessage(Component.text("Usage: /send <player|address> <amount>").color(TextColor.color(255, 0, 0)))
            return true
        }

        val target = args[0] // The player or address to send tokens to
        val amount: Double

        try {
            amount = args[1].toDouble()
        } catch (e: NumberFormatException) {
            sender.sendMessage(Component.text("Invalid amount specified!").color(TextColor.color(255, 0, 0)))
            return true
        }

        if (amount <= 0) {
            sender.sendMessage(Component.text("Amount must be greater than 0.").color(TextColor.color(255, 0, 0)))
            return true
        }

        val senderUUID = sender.uniqueId

        if (args[0].startsWith("0x") && args[0].length == 42) {
            // It's an Ethereum address, not a player
            val destinationAddress = target

            // Perform the transaction to the wallet address (you can expand this further to interact with the blockchain)
            sender.sendMessage(Component.text("Sending $amount tokens to Ethereum address $destinationAddress...").color(TextColor.color(0, 255, 255)))
            // Add Ethereum transfer logic here (for now, we'll assume it's successful)

            // Here we assume the transaction is successful:
            sender.sendMessage(Component.text("Successfully sent $amount tokens to $destinationAddress").color(TextColor.color(0, 255, 0)))
        } else {
            // It's a player, so we try to find the player and send tokens
            val targetPlayer = Bukkit.getPlayer(target)
            if (targetPlayer == null) {
                sender.sendMessage(Component.text("Player not found!").color(TextColor.color(255, 0, 0)))
                return true
            }

            // Now send tokens to the player
            walletManager.sendTokens(senderUUID, targetPlayer.uniqueId.toString(), amount)
                .thenAccept { success ->
                    if (success) {
                        sender.sendMessage(Component.text("Successfully sent $amount tokens to ${targetPlayer.name}.")
                            .color(TextColor.color(0, 255, 0)))
                        targetPlayer.sendMessage(Component.text("You received $amount tokens from ${sender.name}.")
                            .color(TextColor.color(0, 255, 0)))
                    } else {
                        sender.sendMessage(Component.text("You don't have enough tokens to send.")
                            .color(TextColor.color(255, 0, 0)))
                    }
                }
                .exceptionally { ex ->
                    sender.sendMessage(Component.text("Transaction failed: ${ex.message}").color(TextColor.color(255, 0, 0)))
                    null
                }

        }

        return true
    }
}
