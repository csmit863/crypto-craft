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

        if (args.size != 2) {
            sender.sendMessage(Component.text("Usage: /send <player|address> <amount>").color(TextColor.color(255, 0, 0)))
            return true
        }

        val target = args[0]
        val amount = args[1].toDoubleOrNull()
        if (amount == null || amount <= 0) {
            sender.sendMessage(Component.text("Invalid amount specified!").color(TextColor.color(255, 0, 0)))
            return true
        }

        // If sending to an Ethereum address, restrict to operators
        if (target.startsWith("0x") && target.length == 42 && !sender.isOp) {
            sender.sendMessage(Component.text("Only operators can send directly to an Ethereum address!").color(TextColor.color(255, 0, 0)))
            return true
        }

        val senderUUID = sender.uniqueId

        walletManager.sendTokens(senderUUID, target, amount).thenAccept { success ->
            if (success) {
                if (target.startsWith("0x") && target.length == 42) {
                    sender.sendMessage(
                        Component.text("Successfully sent $amount tokens to Ethereum address $target").color(TextColor.color(0, 255, 0))
                    )
                } else {
                    val targetPlayer = Bukkit.getPlayer(target)
                    if (targetPlayer != null) {
                        sender.sendMessage(
                            Component.text("Successfully sent $amount tokens to ${targetPlayer.name}.").color(TextColor.color(0, 255, 0))
                        )
                        targetPlayer.sendMessage(
                            Component.text("You received $amount tokens from ${sender.name}.").color(TextColor.color(0, 255, 0))
                        )
                    } else {
                        // If the target is a UUID string, just confirm
                        sender.sendMessage(
                            Component.text("Successfully sent $amount tokens.").color(TextColor.color(0, 255, 0))
                        )
                    }
                }
            } else {
                sender.sendMessage(Component.text("Transaction failed: insufficient balance or invalid target.").color(TextColor.color(255, 0, 0)))
            }
        }.exceptionally { ex ->
            sender.sendMessage(Component.text("Transaction failed: ${ex.message}").color(TextColor.color(255, 0, 0)))
            null
        }

        return true
    }
}
