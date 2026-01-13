package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
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

        // Resolve asset → ERC20
        val assetAddress = AssetFactory.getAssetAddress(
            material.name.lowercase(),
            material.name.take(4).uppercase()
        )

        if (assetAddress == null) {
            sender.sendMessage(
                Component.text("No market exists for ${material.name.lowercase()}")
                    .color(TextColor.color(255, 165, 0))
            )
            return true
        }

        // Find Blockcoin ↔ Asset pair
        Uniswap.getPair(Blockcoin.address, assetAddress).thenAccept { pairAddress ->

            if (pairAddress == "0x0000000000000000000000000000000000000000") {
                sender.sendMessage(
                    Component.text("No liquidity pool for ${material.name.lowercase()}")
                        .color(TextColor.color(255, 165, 0))
                )
                return@thenAccept
            }

            // Read reserves
            Uniswap.getReserves(pairAddress).thenAccept { reserves ->

                val reserveBlockcoin = reserves.first
                val reserveAsset = reserves.second

                if (reserveBlockcoin == BigInteger.ZERO || reserveAsset == BigInteger.ZERO) {
                    sender.sendMessage(
                        Component.text("Market exists but has no liquidity.")
                            .color(TextColor.color(255, 0, 0))
                    )
                    return@thenAccept
                }

                // Convert to human units (18 decimals)
                val rb = BigDecimal(reserveBlockcoin).movePointLeft(18)
                val ra = BigDecimal(reserveAsset).movePointLeft(18)

                val price = rb.divide(ra, 8, RoundingMode.HALF_UP)

                sender.sendMessage(
                    Component.text(
                        "1 ${material.name.lowercase().replace("_", " ")} costs $price Blockcoin"
                    ).color(TextColor.color(0, 255, 0))
                )
            }
        }

        return true
    }
}
