package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.AssetFactory
import me.callum.club_plugin.economy.Bank
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
import org.web3j.crypto.Credentials
import org.web3j.tx.RawTransactionManager
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

class LiquidityCommand(
    private val plugin: org.bukkit.plugin.java.JavaPlugin,
    private val walletManager: WalletManager
) : CommandExecutor, TabCompleter {

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("add", "remove", "info")
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("add", ignoreCase = true)) {
            val suggestions = mutableListOf("hand")
            suggestions.addAll(
                AssetFactory.getAssetNames()
                    .filter { it.startsWith(args[1], ignoreCase = true) }
            )
            return suggestions.sorted()
        }
        if (args.size == 3 && args[0].equals("add", ignoreCase = true)) {
            return listOf("all", "1", "8", "16", "32", "64")
                .filter { it.startsWith(args[2], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("remove", ignoreCase = true)) {
            val suggestions = mutableListOf("all")
            suggestions.addAll(
                AssetFactory.getAssetNames()
                    .filter { it.startsWith(args[1], ignoreCase = true) }
            )
            return suggestions.sorted()
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
            sender.sendMessage(Component.text("Only players can use this command."))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /liquidity <add|remove|info> [item] [amount]"))
            return true
        }

        return when (args[0].lowercase()) {
            "add"    -> handleAdd(sender, args)
            "remove" -> handleRemove(sender, args)
            "info"   -> handleInfo(sender, args)
            else     -> {
                sender.sendMessage(Component.text("Usage: /liquidity <add|remove|info>"))
                true
            }
        }
    }

    private fun handleAdd(sender: Player, args: Array<out String>): Boolean {
        if (args.size != 3) {
            sender.sendMessage(Component.text("Usage: /liquidity add <hand|item> <amount|all>"))
            return true
        }

        val walletAddress = walletManager.getWallet(sender.uniqueId) ?: run {
            sender.sendMessage(Component.text("You don't have a wallet yet."))
            return true
        }

        // resolve material
        val material: Material
        if (args[1].equals("hand", ignoreCase = true)) {
            val held = sender.inventory.itemInMainHand
            if (held.type == Material.AIR) {
                sender.sendMessage(Component.text("You're not holding anything."))
                return true
            }
            material = held.type
        } else {
            material = Material.matchMaterial(args[1].substringAfter(":").uppercase()) ?: run {
                sender.sendMessage(Component.text("Unknown item: ${args[1]}"))
                return true
            }
        }

        // resolve amount
        val totalInInventory = sender.inventory.contents
            .filterNotNull()
            .filter { it.type == material }
            .sumOf { it.amount }

        val amount: Int = if (args[2].equals("all", ignoreCase = true)) {
            if (totalInInventory == 0) {
                sender.sendMessage(Component.text("You don't have any ${material.name.lowercase()}."))
                return true
            }
            totalInInventory
        } else {
            val parsed = args[2].toIntOrNull()
            if (parsed == null || parsed <= 0) {
                sender.sendMessage(Component.text("Invalid amount."))
                return true
            }
            if (totalInInventory < parsed) {
                sender.sendMessage(Component.text("You only have $totalInInventory ${material.name.lowercase()}."))
                return true
            }
            parsed
        }

        val itemName = material.key.key.replace("_", " ")
            .lowercase().replaceFirstChar { it.uppercase() }

        if (!AssetFactory.checkAssetExists(itemName)) {
            sender.sendMessage(Component.text("❌ No market exists for ${material.name.lowercase()} yet. Sell some first."))
            return true
        }

        // remove items from inventory on main thread
        var remaining = amount
        for (slot in 0 until sender.inventory.size) {
            val item = sender.inventory.getItem(slot) ?: continue
            if (item.type != material) continue
            val take = minOf(item.amount, remaining)
            item.amount -= take
            remaining -= take
            if (item.amount <= 0) sender.inventory.setItem(slot, null)
            else sender.inventory.setItem(slot, item)
            if (remaining == 0) break
        }

        sender.sendMessage(Component.text("⏳ Adding liquidity…"))

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val DECIMALS = BigInteger.TEN.pow(18)
                val amountWei = BigInteger.valueOf(amount.toLong()).multiply(DECIMALS)

                val assetAddress = AssetFactory.getAssetAddress(itemName)
                    ?: error("Asset address not found")

                // quote BlockCoin needed based on current pair ratio
                val pairAddress = Uniswap.getPair(Blockcoin.address, assetAddress).get()
                val reserves = Uniswap.getReserves(pairAddress).get()
                val token0 = Uniswap.getToken0(pairAddress).get()

                val (blockCoinReserve, assetReserve) =
                    if (token0.equals(Blockcoin.address, ignoreCase = true))
                        reserves.first to reserves.second
                    else
                        reserves.second to reserves.first

                // blockCoinNeeded = amountWei * blockCoinReserve / assetReserve
                val blockCoinNeeded = amountWei
                    .multiply(blockCoinReserve)
                    .divide(assetReserve)

                // add 1% buffer for slippage
                val blockCoinMax = blockCoinNeeded
                    .multiply(BigInteger.valueOf(101))
                    .divide(BigInteger.valueOf(100))

                val blockCoinHuman = BigDecimal(blockCoinNeeded)
                    .divide(BigDecimal(DECIMALS), 4, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString()

                // player approves bank to spend their BlockCoin
                val creds = Credentials.create(WalletManager.getWalletAuth(sender.uniqueId))
                val playerTxManager = RawTransactionManager(Blockcoin.web3, creds)

                Blockcoin.approveSpending(AssetFactory.factoryAddress, blockCoinMax, playerTxManager)
                AssetFactory.tokenizeAndDeposit(assetAddress, amountWei, blockCoinMax, playerTxManager)
                    ?: error("Deposit failed")

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage(
                        Component.text(
                            "✅ Added $amount ${material.name.lowercase()} + $blockCoinHuman BLOCK as liquidity"
                        ).color(TextColor.color(0, 255, 0))
                    )
                })

            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    // refund items
                    sender.inventory.addItem(ItemStack(material, amount))
                    sender.sendMessage(Component.text("❌ Deposit failed: ${e.message}")
                        .color(TextColor.color(255, 0, 0)))
                })
            }
        })

        return true
    }

    private fun handleRemove(sender: Player, args: Array<out String>): Boolean {
        if (args.size != 2) {
            sender.sendMessage(Component.text("Usage: /liquidity remove <all|item>"))
            return true
        }

        val walletAddress = walletManager.getWallet(sender.uniqueId) ?: run {
            sender.sendMessage(Component.text("You don't have a wallet yet."))
            return true
        }

        sender.sendMessage(Component.text("⏳ Withdrawing liquidity…"))

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val creds = Credentials.create(WalletManager.getWalletAuth(sender.uniqueId))
                val playerTxManager = RawTransactionManager(Blockcoin.web3, creds)

                if (args[1].equals("all", ignoreCase = true)) {
                    AssetFactory.withdrawAllLiquidity(playerTxManager) ?: error("Withdraw failed")
                } else {
                    val material = Material.matchMaterial(args[1].substringAfter(":").uppercase())
                        ?: error("Unknown item: ${args[1]}")
                    val itemName = material.key.key.replace("_", " ")
                        .lowercase().replaceFirstChar { it.uppercase() }
                    val assetAddress = AssetFactory.getAssetAddress(itemName)
                        ?: error("No market for ${args[1]}")
                    AssetFactory.withdrawLiquidity(assetAddress, playerTxManager) ?: error("Withdraw failed")
                }

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage(
                        Component.text("✅ Liquidity withdrawn and converted to BLOCK")
                            .color(TextColor.color(0, 255, 0))
                    )
                })

            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage(Component.text("❌ Withdraw failed: ${e.message}")
                        .color(TextColor.color(255, 0, 0)))
                })
            }
        })

        return true
    }

    private fun handleInfo(sender: Player, args: Array<out String>): Boolean {
        if (args.size != 2) {
            sender.sendMessage(Component.text("Usage: /liquidity info <item>"))
            return true
        }

        val material = Material.matchMaterial(args[1].substringAfter(":").uppercase()) ?: run {
            sender.sendMessage(Component.text("Unknown item: ${args[1]}"))
            return true
        }

        val itemName = material.key.key.replace("_", " ")
            .lowercase().replaceFirstChar { it.uppercase() }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val assetAddress = AssetFactory.getAssetAddress(itemName)
                    ?: error("No market for ${args[1]}")

                val pairAddress = Uniswap.getPair(Blockcoin.address, assetAddress).get()
                val reserves = Uniswap.getReserves(pairAddress).get()
                val token0 = Uniswap.getToken0(pairAddress).get()

                val (blockCoinReserve, assetReserve) =
                    if (token0.equals(Blockcoin.address, ignoreCase = true))
                        reserves.first to reserves.second
                    else
                        reserves.second to reserves.first

                val DECIMALS = BigDecimal.TEN.pow(18)
                val bcHuman = BigDecimal(blockCoinReserve).divide(DECIMALS, 4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                val assetHuman = BigDecimal(assetReserve).divide(DECIMALS, 4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                val price = BigDecimal(blockCoinReserve).divide(BigDecimal(assetReserve), 8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage(Component.text("📊 ${material.name.lowercase()} pool:").color(TextColor.color(0, 200, 255)))
                    sender.sendMessage(Component.text("  BLOCK reserve: $bcHuman"))
                    sender.sendMessage(Component.text("  Item reserve:  $assetHuman"))
                    sender.sendMessage(Component.text("  Price:         $price BLOCK per item"))
                })

            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage(Component.text("❌ Failed: ${e.message}").color(TextColor.color(255, 0, 0)))
                })
            }
        })

        return true
    }

    private fun toHuman(value: BigInteger): String {
        return BigDecimal(value)
            .divide(BigDecimal.TEN.pow(18))
            .stripTrailingZeros()
            .toPlainString()
    }
}