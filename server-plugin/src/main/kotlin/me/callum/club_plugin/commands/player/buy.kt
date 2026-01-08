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
import org.web3j.abi.datatypes.Address
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture

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
        val price: Int = 1
        val coinPrice: BigDecimal = BigDecimal(price*amount)
        val balanceFuture: CompletableFuture<BigDecimal> = walletManager.getBalance(sender.uniqueId)
        // ether balance errors. if the account does not have ether, the transaction will fail: "Transaction failed: Insufficient funds for gas * price + value".
        // However, the item will still be provided.
        balanceFuture.thenAccept { balance ->
            if (balance < coinPrice) {
                sender.sendMessage(Component.text("You don't have enough coins."))
            } else {
                walletManager.sendTokens(
                    sender.uniqueId,
                    "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
                    coinPrice.toDouble()
                ).thenAccept { success ->
                    if (success) {
                        val itemToAdd = ItemStack(material, amount)
                        sender.inventory.addItem(itemToAdd)
                        sender.sendMessage(Component.text("Purchased $amount ${material.name.lowercase().replace("_", " ")} with $coinPrice blockcoins.")
                            .color(TextColor.color(0, 255, 0)))
                    } else {
                        sender.sendMessage(Component.text("Failed to send tokens. Please try again.")
                            .color(TextColor.color(255, 0, 0)))
                    }
                }.exceptionally { throwable ->
                    sender.sendMessage(Component.text("An error occurred: ${throwable.message}")
                        .color(TextColor.color(255, 0, 0)))
                    null // Return null to satisfy the CompletableFuture
                }
            }
        }
        return true

    }
}
