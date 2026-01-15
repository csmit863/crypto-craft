package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.AssetFactory
import me.callum.club_plugin.economy.Blockcoin
import me.callum.club_plugin.economy.Uniswap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import java.math.BigDecimal
import java.math.BigInteger

class LiquidityCommand : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (args.size != 1) {
            sender.sendMessage(Component.text("Usage: /liquidity <item>")
                .color(TextColor.color(255, 0, 0)))
            return true
        }

        val itemName = args[0]
        val material = Material.matchMaterial(itemName)

        if (material == null) {
            sender.sendMessage(Component.text("Unknown item: $itemName")
                .color(TextColor.color(255, 0, 0)))
            return true
        }

        val itemToken = AssetFactory.getAssetAddress(itemName)
        if (itemToken == null) {
            sender.sendMessage(Component.text("No token exists for $itemName")
                .color(TextColor.color(255, 0, 0)))
            return true
        }

        val blockcoin = Blockcoin.address

        // Resolve pair
        Uniswap.getPair(blockcoin, itemToken).thenAccept { pairAddress ->
            if (pairAddress == "0xnull" || pairAddress == "0x0000000000000000000000000000000000000000") {
                sender.sendMessage(Component.text("No liquidity pool exists for $itemName")
                    .color(TextColor.color(255, 0, 0)))
                return@thenAccept
            }

            // Read reserves
            Uniswap.getReserves(pairAddress).thenAccept { (reserve0, reserve1) ->

                // Determine token ordering
                Uniswap.getToken0(pairAddress).thenAccept { token0 ->
                    val (blockcoinReserve, itemReserve) =
                        if (token0.equals(blockcoin, ignoreCase = true)) {
                            reserve0 to reserve1
                        } else {
                            reserve1 to reserve0
                        }

                    val bcHuman = toHuman(blockcoinReserve)
                    val itemHuman = toHuman(itemReserve)

                    sender.sendMessage(Component.text("Liquidity for ${material.name.lowercase()}:")
                        .color(TextColor.color(0, 255, 0)))

                    sender.sendMessage(Component.text("• Blockcoin: $bcHuman"))
                    sender.sendMessage(Component.text("• ${material.name.lowercase()}: $itemHuman"))
                }
            }
        }

        return true
    }

    private fun toHuman(value: BigInteger): String {
        return BigDecimal(value)
            .divide(BigDecimal.TEN.pow(18))
            .stripTrailingZeros()
            .toPlainString()
    }
}
