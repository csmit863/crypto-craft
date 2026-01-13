package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.economy.Uniswap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

class GetAssetsCommand : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        sender.sendMessage(Component.text("Uniswap Pairs and Prices:").color(TextColor.color(0, 255, 255)))

        Uniswap.getAllPairs().thenAccept { pairs ->

            if (pairs.isEmpty()) {
                sender.sendMessage(Component.text("No pairs found.").color(TextColor.color(255, 0, 0)))
                return@thenAccept
            }

            pairs.forEachIndexed { index, pairAddress ->

                Uniswap.getReserves(pairAddress).thenAccept { reserves ->

                    val reserve0 = reserves.first
                    val reserve1 = reserves.second

                    // Avoid dividing by zero
                    if (reserve1 == BigInteger.ZERO) return@thenAccept

                    val price = BigDecimal(reserve0)
                        .movePointLeft(18)
                        .divide(BigDecimal(reserve1).movePointLeft(18), 8, RoundingMode.HALF_UP)
                    val priceStr = price.stripTrailingZeros().toPlainString()
                    sender.sendMessage(
                        Component.text("Pair[$index]: $pairAddress").color(TextColor.color(0, 255, 255))
                    )
                    sender.sendMessage(
                        Component.text("  Price (reserve0/reserve1): $priceStr").color(TextColor.color(0, 255, 0))
                    )
                }
            }
        }.exceptionally { ex ->
            ex.printStackTrace()
            sender.sendMessage(Component.text("Failed to fetch pairs.").color(TextColor.color(255, 0, 0)))
            null
        }

        return true
    }
}
