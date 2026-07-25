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
import org.bukkit.command.TabCompleter
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
) : CommandExecutor, TabCompleter {

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            // only show items that actually have a market
            return AssetFactory.getAssetNames()
                .filter { it.startsWith(args[0], ignoreCase = true) }
                .sorted()
        }

        if (args.size == 2) {
            return listOf("1", "8", "16", "32", "64")
                .filter { it.startsWith(args[1], ignoreCase = true) }
        }

        return emptyList()
    }

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

        val itemName = material.key.key.replace("_", " ")
            .lowercase().replaceFirstChar { it.uppercase() }

        val walletAddress = walletManager.getWallet(sender.uniqueId)
            ?: run {
                sender.sendMessage(Component.text("No wallet found."))
                return true
            }

        sender.sendMessage(Component.text("⏳ Processing purchase..."))

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                if (!AssetFactory.checkAssetExists(itemName)) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        sender.sendMessage(Component.text("❌ This item hasn't been sold yet — no market exists."))
                    })
                    return@Runnable
                }

                val DECIMALS = BigInteger.TEN.pow(18)
                val amountOut = BigInteger.valueOf(amount.toLong()).multiply(DECIMALS)

                val assetAddress = AssetFactory.getAssetAddress(itemName)
                    ?: error("Asset address not found")

                // quote how much BlockCoin is needed
                val path = listOf(Blockcoin.address, assetAddress)
                val amountsIn = Uniswap.getAmountsIn(amountOut, path).get()
                require(amountsIn.isNotEmpty()) { "No quote returned" }

                val requiredIn = amountsIn.first()
                val amountInMax = requiredIn
                    .multiply(BigInteger.valueOf(101))
                    .divide(BigInteger.valueOf(100)) // 1% slippage buffer

                val spentBlockcoin = BigDecimal(requiredIn)
                    .divide(BigDecimal(DECIMALS))

                // player signs both transactions
                val creds = Credentials.create(WalletManager.getWalletAuth(sender.uniqueId))
                val playerTxManager = RawTransactionManager(Blockcoin.web3, creds)

                // player approves factory to pull their BlockCoin
                Blockcoin.approveSpending(
                    AssetFactory.factoryAddress,
                    amountInMax,
                    playerTxManager
                )

                // player calls buyAssetAndBurn on the factory
                AssetFactory.buyAssetAndBurn(
                    assetAddress,
                    amountOut,
                    playerTxManager
                ) ?: error("Buy failed")

                // success — give items on main thread
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