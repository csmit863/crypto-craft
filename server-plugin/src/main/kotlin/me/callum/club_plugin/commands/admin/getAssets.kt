package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.economy.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.web3j.protocol.Web3j
import org.web3j.tx.RawTransactionManager
import java.math.BigInteger

class GetAssetsCommand : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (sender is Player && !sender.isOp) {
            sender.sendMessage("You do not have permission to use this command.")
            return true
        }

        sender.sendMessage("Uniswap Pairs:")

        // ONE async entry point
        Uniswap.getAllPairs().thenAccept { pairs ->

            if (pairs.isEmpty()) {
                sender.sendMessage("No pairs found.")
                return@thenAccept
            }

            pairs.forEachIndexed { index, pairAddress ->

                // Fetch reserves for each pair
                Uniswap.getReserves(pairAddress).thenAccept { reserves ->
                    val reserveA = reserves.first
                    val reserveB = reserves.second

                    Bukkit.getLogger().info(
                        "Pair[$index] $pairAddress | A=$reserveA B=$reserveB"
                    )

                    sender.sendMessage(
                        "Pair[$index]: $pairAddress"
                    )
                    sender.sendMessage(
                        "  Reserves -> A: $reserveA | B: $reserveB"
                    )
                }
            }
        }.exceptionally { ex ->
            ex.printStackTrace()
            sender.sendMessage("Failed to fetch pairs.")
            null
        }

        return true
    }
}

