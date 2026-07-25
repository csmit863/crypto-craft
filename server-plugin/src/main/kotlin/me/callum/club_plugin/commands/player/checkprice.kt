package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

class CheckPriceCommand(private val plugin: JavaPlugin) : CommandExecutor, TabCompleter {

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return AssetFactory.getAssetNames()
                .filter { it.startsWith(args[0], ignoreCase = true) }
                .sorted()
        }
        return emptyList()
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (args.size != 1) {
            sender.sendMessage(
                Component.text("Usage: /price <item>")
                    .color(TextColor.color(255, 0, 0))
            )
            return true
        }

        val material = Material.matchMaterial(args[0].uppercase())
        if (material == null) {
            sender.sendMessage(
                Component.text("Unknown item: ${args[0]}")
                    .color(TextColor.color(255, 0, 0))
            )
            return true
        }

        val itemName = material.name.lowercase()

        sender.sendMessage(Component.text("⏳ Checking price for $itemName..."))

        // ---- ASYNC BLOCKCHAIN / ASSET LOGIC
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {

            try {
                if (!AssetFactory.checkAssetExists(itemName)) {
                    AssetFactory.syncFromChain()

                    if (!AssetFactory.checkAssetExists(itemName)) {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            sender.sendMessage(
                                Component.text("No market exists for $itemName")
                                    .color(TextColor.color(255, 165, 0))
                            )
                        })
                        return@Runnable
                    }
                }

                val assetAddress = AssetFactory.getAssetAddress(itemName)
                    ?: run {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            sender.sendMessage(
                                Component.text("Failed to retrieve asset address for $itemName")
                                    .color(TextColor.color(255, 0, 0))
                            )
                        })
                        return@Runnable
                    }

                // ---- Fetch pair
                Uniswap.getPair(Blockcoin.address, assetAddress).thenAccept { pairAddress ->

                    if (pairAddress == "0x0000000000000000000000000000000000000000") {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            sender.sendMessage(
                                Component.text("No liquidity pool exists for $itemName")
                                    .color(TextColor.color(255, 165, 0))
                            )
                        })
                        return@thenAccept
                    }

                    // ---- Fetch reserves
                    Uniswap.getReserves(pairAddress).thenAccept { reserves ->
                        if (reserves.first == BigInteger.ZERO || reserves.second == BigInteger.ZERO) {
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                sender.sendMessage(
                                    Component.text("Market exists but has no liquidity.")
                                        .color(TextColor.color(255, 0, 0))
                                )
                            })
                            return@thenAccept
                        }

                        // ---- Determine Blockcoin vs asset reserve
                        Uniswap.getToken0(pairAddress).thenAccept { token0 ->
                            val (reserveBlockcoin, reserveAsset) = if (token0.equals(Blockcoin.address, ignoreCase = true)) {
                                Pair(reserves.first, reserves.second)
                            } else {
                                Pair(reserves.second, reserves.first)
                            }

                            val rb = BigDecimal(reserveBlockcoin).movePointLeft(18)
                            val ra = BigDecimal(reserveAsset).movePointLeft(18)
                            val price = rb.divide(ra, 8, RoundingMode.HALF_UP)
                            val priceStr = price.stripTrailingZeros().toPlainString()
                            val displayName = material.name.lowercase().replace("_", " ")

                            // ---- SUCCESS → MAIN THREAD
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                sender.sendMessage(
                                    Component.text("1 $displayName costs $priceStr Blockcoin")
                                        .color(TextColor.color(0, 255, 0))
                                )
                            })
                        }
                    }
                }

            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage(
                        Component.text("❌ Failed to check price: ${e.message}")
                            .color(TextColor.color(255, 0, 0))
                    )
                })
            }
        })

        return true
    }
}