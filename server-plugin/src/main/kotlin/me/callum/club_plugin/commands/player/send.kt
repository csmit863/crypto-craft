package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.WalletManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * MAIN THREAD
 * - validate
 * - resolve target
 * - tell player "processing..."
 *
 * ASYNC
 * - ALL blockchain logic
 *
 * MAIN THREAD
 * - success/failure messages
 *
 */
class SendTokensCommand(
    private val plugin: JavaPlugin,
    private val walletManager: WalletManager
) : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can send tokens."))
            return true
        }

        if (args.size != 2) {
            sender.sendMessage(Component.text("Usage: /send <player|address> <amount>"))
            return true
        }

        val target = args[0]
        val resolvedTarget: String = when {
            target.startsWith("0x") && target.length == 42 -> {
                // raw address
                target
            }

            else -> {
                val targetPlayer = Bukkit.getPlayerExact(target)
                if (targetPlayer == null) {
                    sender.sendMessage(Component.text("Player not found."))
                    return true
                }
                targetPlayer.uniqueId.toString()
            }
        }

        val amount = args[1].toDoubleOrNull()

        if (amount == null || amount <= 0) {
            sender.sendMessage(Component.text("Invalid amount."))
            return true
        }

        val senderUUID = sender.uniqueId

        sender.sendMessage(Component.text("⏳ Sending tokens..."))

        walletManager.sendTokens(senderUUID, resolvedTarget, amount)
            .thenAccept { success ->

                Bukkit.getScheduler().runTask(plugin, Runnable {

                    if (!sender.isOnline) return@Runnable

                    if (success) {
                        val targetPlayer = Bukkit.getPlayerExact(target)

                        sender.sendMessage(
                            Component.text("✅ Sent $amount tokens.")
                                .color(TextColor.color(0, 255, 0))
                        )

                        if (targetPlayer != null) {
                            targetPlayer.sendMessage(
                                Component.text("💰 You received $amount tokens from ${sender.name}.")
                                    .color(TextColor.color(0, 255, 0))
                            )
                        }
                    } else {
                        sender.sendMessage(
                            Component.text("❌ Transaction failed.")
                                .color(TextColor.color(255, 0, 0))
                        )
                    }
                })
            }
            .exceptionally { ex ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (sender.isOnline) {
                        sender.sendMessage(
                            Component.text("❌ Transaction error: ${ex.message}")
                                .color(TextColor.color(255, 0, 0))
                        )
                    }
                })
                null
            }

        return true
    }
}