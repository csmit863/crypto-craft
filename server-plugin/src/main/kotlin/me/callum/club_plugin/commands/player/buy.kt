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

        val itemTokenAddress = AssetFactory.getAssetAddress(itemName)
            ?: run {
                sender.sendMessage(Component.text("Failed to find token address for $itemName")
                    .color(TextColor.color(255, 0, 0)))
                return true
            }

        val DECIMALS = BigInteger.TEN.pow(18)
        val amountOut = BigInteger.valueOf(amount.toLong()).multiply(DECIMALS)

        val path = listOf(Blockcoin.address, itemTokenAddress)

        // --- QUOTE REQUIRED INPUT ---
        val amountsIn = Uniswap.getAmountsIn(amountOut, path).get()
        require(amountsIn.isNotEmpty()) { "No quote returned" }

        val requiredIn = amountsIn.first()

        // --- SLIPPAGE (1%) ---
        val amountInMax = requiredIn
            .multiply(BigInteger.valueOf(101))
            .divide(BigInteger.valueOf(100))

        val walletAddress = walletManager.getWallet(sender.uniqueId)
            ?: run {
                sender.sendMessage(Component.text("No wallet found")
                    .color(TextColor.color(255, 0, 0)))
                return true
            }

        val userSigner = Credentials.create(WalletManager.getWalletAuth(sender.uniqueId))
        val userTxManager = RawTransactionManager(Blockcoin.web3, userSigner)

        // --- APPROVE ---
        Blockcoin.approveSpending(
            Uniswap.v2routerAddress,
            amountInMax,
            userTxManager
        )

        // --- SWAP ---
        CompletableFuture.supplyAsync {
            Uniswap.swapTokensForExactTokens(
                amountOut,
                amountInMax,
                path,
                Address(walletAddress),
                org.web3j.abi.datatypes.generated.Uint256(
                    BigInteger.valueOf(System.currentTimeMillis() / 1000 + 300)
                ),
                userTxManager
            )
        }.thenAccept {
            sender.inventory.addItem(ItemStack(material, amount))
            sender.sendMessage(
                Component.text("✅ Bought $amount ${material.name.lowercase().replace("_", " ")}")
                    .color(TextColor.color(0, 255, 0))
            )
        }.exceptionally { e ->
            sender.sendMessage(
                Component.text("❌ Buy failed: ${e.message}")
                    .color(TextColor.color(255, 0, 0))
            )
            null
        }

        return true
    }
}
