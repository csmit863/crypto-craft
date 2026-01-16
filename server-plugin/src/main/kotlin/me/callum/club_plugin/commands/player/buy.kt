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
import org.bukkit.plugin.java.JavaPlugin
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
/**
 * MAIN THREAD
 * - validate
 * - resolve material / wallet
 * - send "processing..."
 *
 * ASYNC
 * - ALL blockchain logic:
 *   - quote
 *   - approve
 *   - swap
 *
 * MAIN THREAD
 * - give item
 * - send success / failure
 *
 */
class BuyItemsCommand(
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
            sender.sendMessage(Component.text("Only players can buy items."))
            return true
        }

        if (args.size != 2) {
            sender.sendMessage(Component.text("Usage: /buy <item> <amount>"))
            return true
        }

        val material = Material.matchMaterial(args[0])
        val amount = args[1].toIntOrNull()

        if (material == null || amount == null || amount <= 0) {
            sender.sendMessage(Component.text("Invalid item or amount."))
            return true
        }

        val itemName = material.key.key

        if (!AssetFactory.checkAssetExists(itemName)) {
            sender.sendMessage(Component.text("This item is not available on the market yet."))
            return true
        }

        val walletAddress = walletManager.getWallet(sender.uniqueId)
            ?: run {
                sender.sendMessage(Component.text("No wallet found."))
                return true
            }

        sender.sendMessage(Component.text("⏳ Processing purchase..."))

        // ---- ASYNC BLOCKCHAIN WORK
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val DECIMALS = BigInteger.TEN.pow(18)
                val amountOut = BigInteger.valueOf(amount.toLong()).multiply(DECIMALS)

                val tokenAddress = AssetFactory.getAssetAddress(itemName)
                    ?: error("Token address missing")

                val path = listOf(Blockcoin.address, tokenAddress)

                // ---- QUOTE
                val amountsIn = Uniswap.getAmountsIn(amountOut, path).get()
                require(amountsIn.isNotEmpty()) { "No quote returned" }

                val requiredIn = amountsIn.first()
                val amountInMax = requiredIn
                    .multiply(BigInteger.valueOf(101))
                    .divide(BigInteger.valueOf(100))

                val spentBlockcoin = BigDecimal(requiredIn)
                    .divide(BigDecimal(DECIMALS))

                // ---- SIGNER
                val creds = Credentials.create(
                    WalletManager.getWalletAuth(sender.uniqueId)
                )
                val txManager = RawTransactionManager(Blockcoin.web3, creds)

                // ---- APPROVE
                Blockcoin.approveSpending(
                    Uniswap.v2routerAddress,
                    amountInMax,
                    txManager
                )

                // ---- SWAP
                val receipt = Uniswap.swapTokensForExactTokens(
                    amountOut,
                    amountInMax,
                    path,
                    Address(walletAddress),
                    org.web3j.abi.datatypes.generated.Uint256(
                        BigInteger.valueOf(System.currentTimeMillis() / 1000 + 300)
                    ),
                    txManager
                )

                require(receipt.status == "0x1") { "Swap failed" }

                // ---- SUCCESS → MAIN THREAD
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (!sender.isOnline) return@Runnable

                    sender.inventory.addItem(ItemStack(material, amount))
                    sender.sendMessage(
                        Component.text(
                            "✅ Bought $amount ${material.name.lowercase().replace("_", " ")} " +
                                    "for ${spentBlockcoin.stripTrailingZeros()} blockcoins"
                        ).color(TextColor.color(0, 255, 0))
                    )
                })

            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (sender.isOnline) {
                        sender.sendMessage(
                            Component.text("❌ Buy failed: ${e.message}")
                                .color(TextColor.color(255, 0, 0))
                        )
                    }
                })
            }
        })

        return true
    }
}