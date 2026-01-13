package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.AssetFactory
import me.callum.club_plugin.economy.Blockcoin
import me.callum.club_plugin.economy.Uniswap
import me.callum.club_plugin.economy.WalletManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.web3j.abi.datatypes.Address
import org.web3j.tx.RawTransactionManager
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CompletableFuture
import org.web3j.crypto.Credentials

/**
 * To sell items, some contracts have to exist:
 * 1. The token contracts for Blockcoin and the specified item
 * 2. The pair factory contract
 * 3. The pair contract for Blockcoin and the tokenized item
 * If these contracts do not exist, they must be created.
 *
 * Rule: no one should be able to buy an item unless it has been sold at least once.
 * IE, the token must be created before it can be bought.
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
        Bukkit.getLogger().info(material.toString())

        if (material == null) {
            sender.sendMessage(Component.text("Unknown item type: $itemName").color(TextColor.color(255, 0, 0)))
            return true
        }

        if (amount == null || amount <= 0) {
            sender.sendMessage(Component.text("Invalid amount: must be a positive number.").color(TextColor.color(255, 0, 0)))
            return true
        }

        if (!AssetFactory.checkAssetExists(itemName)) {
            sender.sendMessage(Component.text("The asset $itemName does not exist on the market yet!").color(TextColor.color(255, 0, 0)))
            return true
        }

        val price: Int = 1 // will have to implement uniswap quoting
        val coinPrice: BigDecimal = BigDecimal(price*amount)
        val balanceFuture: CompletableFuture<BigDecimal> = walletManager.getBalance(sender.uniqueId)
        // ether balance errors. if the account does not have ether, the transaction will fail: "Transaction failed: Insufficient funds for gas * price + value".
        // However, the item will still be provided.
        balanceFuture.thenAccept { balance ->
            if (balance < coinPrice) {
                sender.sendMessage(Component.text("You don't have enough coins."))
            } else {
                val itemTokenAddress = AssetFactory.getAssetAddress(itemName)
                if (itemTokenAddress == null) {
                    sender.sendMessage(Component.text("Failed to find token address for $itemName").color(TextColor.color(255, 0, 0)))
                    return@thenAccept
                }

                val paymentTokenAddress = Blockcoin.address
                val amountIn = coinPrice.toBigInteger()
                val amountOutMin = BigInteger.ZERO  // could calculate via Uniswap quote
                val path = listOf(paymentTokenAddress, itemTokenAddress)
                val recipient = Address(walletManager.getWallet(sender.uniqueId))
                val deadline = org.web3j.abi.datatypes.generated.Uint256(BigInteger.valueOf(System.currentTimeMillis() / 1000 + 300))

                val userSigner = Credentials.create(WalletManager.getWalletAuth(sender.uniqueId))
                val userTxManager = RawTransactionManager(Blockcoin.web3, userSigner)

                CompletableFuture.supplyAsync {
                    Uniswap.swapExactTokensForTokens(
                        amountIn,
                        amountOutMin,
                        path,
                        recipient,
                        deadline,
                        userTxManager
                    )
                }.thenAccept { receipt ->
                    // Only give items after a successful swap
                    val itemToAdd = ItemStack(material, amount)
                    sender.inventory.addItem(itemToAdd)
                    sender.sendMessage(Component.text(
                        "Purchased $amount ${material.name.lowercase().replace("_", " ")} with $coinPrice blockcoins."
                    ).color(TextColor.color(0, 255, 0)))
                }.exceptionally { throwable ->
                    sender.sendMessage(Component.text("Swap failed: ${throwable.message}")
                        .color(TextColor.color(255, 0, 0)))
                    null
                }

            }
        }
        return true

    }
}
