package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.config.ServerConfig
import me.callum.club_plugin.economy.AssetFactory
import me.callum.club_plugin.economy.Blockcoin
import me.callum.club_plugin.economy.Uniswap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class GetConfigCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.isOp) {
            sender.sendMessage(Component.text("You do not have permission to use this command.")
                .color(TextColor.color(255, 0, 0)))
            return true
        }

        fun makeCopyable(label: String, value: String) = Component.text("$label: ")
            .color(TextColor.color(0, 255, 255))
            .append(
                Component.text(value)
                    .color(TextColor.color(0, 255, 127))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy!").color(TextColor.color(255, 255, 0))))
                    .clickEvent(ClickEvent.copyToClipboard(value))
            )

        val messages = listOf(
            Component.text("===== Server Config =====").color(TextColor.color(255, 255, 0)),
            Component.text("RPC URL: ${ServerConfig.rpcUrl()}").color(TextColor.color(0, 255, 255)),
            makeCopyable("Blockcoin Address", ServerConfig.blockcoin()),
            makeCopyable("AssetFactory Address", ServerConfig.assetFactory()),
            makeCopyable("Uniswap Factory Address", ServerConfig.uniswapFactory()),
            makeCopyable("Uniswap Router Address", ServerConfig.uniswapRouter()),
            Component.text("=========================").color(TextColor.color(255, 255, 0))
        )

        // Send to console
        Bukkit.getLogger().info("===== Server Config =====")
        Bukkit.getLogger().info("RPC URL: ${ServerConfig.rpcUrl()}")
        Bukkit.getLogger().info("Blockcoin Address: ${ServerConfig.blockcoin()}")
        Bukkit.getLogger().info("AssetFactory Address: ${ServerConfig.assetFactory()}")
        Bukkit.getLogger().info("Uniswap Factory Address: ${ServerConfig.uniswapFactory()}")
        Bukkit.getLogger().info("Uniswap Router Address: ${ServerConfig.uniswapRouter()}")
        Bukkit.getLogger().info("=========================")

        // Send to player
        if (sender is Player) {
            messages.forEach { sender.sendMessage(it) }
        }

        return true
    }
}
