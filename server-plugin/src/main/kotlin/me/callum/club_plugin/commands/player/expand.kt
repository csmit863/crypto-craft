package me.callum.club_plugin.commands.player

import com.google.gson.Gson
import me.callum.club_plugin.CryptoCraft
import me.callum.club_plugin.economy.WalletManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.io.File
import java.math.BigInteger

data class WorldBorderState(
    val base: Int = 1000,
    var expanded: Long = 0
)

class Expand(
    private val plugin: CryptoCraft,
    private val walletManager: WalletManager
) : CommandExecutor {

    private val gson = Gson()
    private val adminBurnAddress = "0x000000000000000000000000000000000000dEaD"

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (sender !is Player) {
            sender.sendMessage("Players only.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("§cUsage: /expand <blocks>")
            return true
        }

        val amount = args[0].toLongOrNull()
        if (amount == null || amount <= 0) {
            sender.sendMessage("§cInvalid amount.")
            return true
        }

        // Load state
        val file = File(plugin.dataFolder, "worldborder.json")
        val state = gson.fromJson(file.readText(), WorldBorderState::class.java)

        val playerUUID = sender.uniqueId

        // Check balance (exact integer math)
        WalletManager.getBalanceWei(playerUUID).thenAccept { balanceWei ->
            val costWei = BigInteger.valueOf(amount).multiply(BigInteger.TEN.pow(18))

            if (balanceWei < costWei) {
                sender.sendMessage("§cYou can't afford that.")
                return@thenAccept
            }

            // Burn coins
            WalletManager.sendTokens(
                playerUUID,
                adminBurnAddress,
                amount.toDouble()
            ).thenAccept { success ->

                if (!success) {
                    sender.sendMessage("§cTransaction failed.")
                    return@thenAccept
                }

                // Apply expansion
                state.expanded += amount
                file.writeText(gson.toJson(state))

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val world = Bukkit.getWorlds().first()
                    val newRadius = state.base + state.expanded
                    world.worldBorder.size = newRadius * 2.0

                    Bukkit.broadcastMessage(
                        "§6${sender.name} expanded the world border by §e$amount§6 blocks!"
                    )
                })
            }
        }

        return true
    }
}
