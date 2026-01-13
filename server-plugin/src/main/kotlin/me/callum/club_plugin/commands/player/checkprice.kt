package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

class CheckPriceCommand : CommandExecutor {

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

        val assetAddress = AssetFactory.getAssetAddress(material.name.lowercase())
        if (assetAddress == null) {
            sender.sendMessage(
                Component.text("No market exists for ${material.name.lowercase()}")
                    .color(TextColor.color(255, 165, 0))
            )
            return true
        }

        // Fetch the pair
        Uniswap.getPair(Blockcoin.address, assetAddress).thenAccept { pairAddress ->

            if (pairAddress == "0x0000000000000000000000000000000000000000") {
                sender.sendMessage(
                    Component.text("No liquidity pool exists for ${material.name.lowercase()}")
                        .color(TextColor.color(255, 165, 0))
                )
                return@thenAccept
            }

            // Fetch reserves
            Uniswap.getReserves(pairAddress).thenAccept { reserves ->

                if (reserves.first == BigInteger.ZERO || reserves.second == BigInteger.ZERO) {
                    sender.sendMessage(
                        Component.text("Market exists but has no liquidity.")
                            .color(TextColor.color(255, 0, 0))
                    )
                    return@thenAccept
                }

                // Determine which reserve is Blockcoin vs. asset
                Uniswap.getToken0(pairAddress).thenAccept { token0 ->
                    val (reserveBlockcoin, reserveAsset) = if (token0.equals(Blockcoin.address, ignoreCase = true)) {
                        Pair(reserves.first, reserves.second)
                    } else {
                        Pair(reserves.second, reserves.first)
                    }

                    // Convert to human-readable decimals (18 assumed)
                    val rb = BigDecimal(reserveBlockcoin).movePointLeft(18)
                    val ra = BigDecimal(reserveAsset).movePointLeft(18)

                    val rawPrice = rb.divide(ra, 8, RoundingMode.HALF_UP)
                    val priceStr = rawPrice.stripTrailingZeros().toPlainString()

                    val displayName = material.name.lowercase().replace("_", " ")

                    sender.sendMessage(
                        Component.text("1 $displayName costs $priceStr Blockcoin")
                            .color(TextColor.color(0, 255, 0))
                    )
                }
            }
        }

        return true
    }
}