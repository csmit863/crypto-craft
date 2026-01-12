package me.callum.club_plugin.commands.admin

import me.callum.club_plugin.economy.AssetDetails
import me.callum.club_plugin.economy.AssetFactory
import me.callum.club_plugin.economy.MinecraftAsset
import me.callum.club_plugin.economy.WalletManager
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

class GetAssetsCommand() : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player && !sender.isOp) {
            sender.sendMessage("You do not have permission to use this command.")
            return true
        }

        val assetList = AssetFactory.getAllAssets()

        sender.sendMessage("Minecraft Assets:")

        for (assetAddress in assetList) {
            val asset = MinecraftAsset(assetAddress.toString(), WalletManager.web3, WalletManager.txManager) // Assuming AssetFactory has web3 and txManager
            val details: AssetDetails = asset.getAssetDetails();
            val name = details.name
            val symbol = details.symbol

            val minecraftAssetComponent = Component.text("$name ($symbol): ")
                .color(TextColor.color(0, 255, 255))
                .append(
                    Component.text(assetAddress.toString())
                        .color(TextColor.color(0, 255, 127))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy!").color(TextColor.color(255, 255, 0))))
                        .clickEvent(ClickEvent.copyToClipboard(assetAddress.toString()))
                )

            sender.sendMessage(minecraftAssetComponent)

        }

        return true
    }
}
