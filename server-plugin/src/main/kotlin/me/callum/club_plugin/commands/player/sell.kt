package me.callum.club_plugin.commands.player

import me.callum.club_plugin.economy.AssetFactory
import me.callum.club_plugin.economy.WalletManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.web3j.crypto.Keys


/**
 * To sell items, some contracts have to exist:
 * 1. The token contracts for Blockcoin and the specified item [x]
 * 2. The pair factory contract []
 * 3. The pair contract for Blockcoin and the tokenized item []
 * If these contracts do not exist, they must be created.
 */

class SellItemsCommand(private val walletManager: WalletManager) : CommandExecutor, TabCompleter {

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (args.size == 1) {
            return org.bukkit.Material.values()
                .map { it.key.toString() } // e.g. "minecraft:diamond"
                .filter { it.startsWith(args[0], ignoreCase = true) }
                .sorted()
        }

        return emptyList()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can sell tokens!").color(TextColor.color(255, 0, 0)))
            return true
        }

        // Check if the command has the correct number of arguments
        if (args.size != 2) {
            sender.sendMessage(Component.text("Usage: /sell <item> <amount>").color(TextColor.color(255, 0, 0)))
            return true
        }

        val itemName = args[0] // The item to sell
        val rawMaterialName = itemName.substringAfter(":").uppercase()
        val material = Material.matchMaterial(rawMaterialName)

        if (material == null) {
            sender.sendMessage(Component.text("Unknown item type: $itemName").color(TextColor.color(255, 0, 0)))
            return true
        }

        val amount = args[1].toIntOrNull()

        if (material == null) {
            sender.sendMessage(Component.text("Unknown item type: $itemName").color(TextColor.color(255, 0, 0)))
            return true
        }

        if (amount == null || amount <= 0) {
            sender.sendMessage(Component.text("Invalid amount.").color(TextColor.color(255, 0, 0)))
            return true
        }

        val senderUUID = sender.uniqueId

        val itemToRemove = ItemStack(material, amount)

        // Count total amount of that material in inventory
        val totalInInventory = sender.inventory.contents
            .filterNotNull()
            .filter { it.type == material }
            .sumOf { it.amount }

        if (totalInInventory < amount) {
            sender.sendMessage(Component.text("You don’t have enough $rawMaterialName to sell.").color(TextColor.color(255, 0, 0)))
            return true
        }



        // if the item is not a token, create the token and execute the mint function
        val name = material.key.key.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        val symbol = material.name.take(4).uppercase() // e.g., "DIAM" for "DIAMOND"


        // this function should be used in the event that:
        // there is no minecraft asset created by the factory that matches the required item (e.g. diamond, DIAM)
        val alreadyExists = AssetFactory.checkAssetExists(name)

        if (!alreadyExists) {
            AssetFactory.createAsset(name, symbol)

            val newAddress = AssetFactory.getAssetAddress(name, symbol)
            if (newAddress == null) {
                sender.sendMessage(Component.text("❌ Failed to create token for $rawMaterialName. Try again").color(TextColor.color(255, 0, 0)))
                return true
            }
            sender.sendMessage(Component.text(newAddress));
            val checksummed = Keys.toChecksumAddress(newAddress.toString())


            AssetFactory.saveAsset(name, symbol, checksummed)
            sender.sendMessage(Component.text("✅ Created new token for $rawMaterialName").color(TextColor.color(0, 255, 0)))
        } else {
            sender.sendMessage(Component.text("ℹ️ Token exists for $rawMaterialName").color(TextColor.color(200, 200, 0)))
            val assetAddress = AssetFactory.getAssetAddress(name, symbol)
            val checksummed = Keys.toChecksumAddress(assetAddress.toString())

            println(checksummed)
            sender.sendMessage(Component.text("ℹ️ $rawMaterialName address: $checksummed").color(TextColor.color(200, 200, 0)))
        }

        // If enough, remove the exact amount manually
        var amountToRemove = amount!!
        val inventory = sender.inventory

        // TODO: Ensure pair contract exists between Blockcoin and $symbol
        // after removing the item from inventory, mint it to the user's wallet.
        // then, ensure the token pair exists.
        // walletManager.ensurePairExists("BLOCK", symbol)
        // then get the price of the token
        // then attempt to sell the asset
        val walletAddress = walletManager.getWallet(senderUUID)
        if (walletAddress == null) {
            sender.sendMessage(Component.text("❌ You don't have a wallet yet. Please create one first.").color(TextColor.color(255, 0, 0)))
            return true
        }
        val assetAddress = AssetFactory.getAssetAddress(name, symbol)
        val checksummed = Keys.toChecksumAddress(assetAddress.toString())

        if (checksummed == null) {
            sender.sendMessage(Component.text("❌ Could not retrieve token address for $rawMaterialName").color(TextColor.color(255, 0, 0)))
            return true
        }

        val price = 1 // expected swap price needs to be fetched from the AMM.

        sender.sendMessage("Minting asset at contract address $checksummed")
        val txHash = AssetFactory.mintAsset(checksummed, amountToRemove, walletAddress)
        if (txHash == null) {
            sender.sendMessage(Component.text("❌ Failed to mint tokenized asset to your wallet").color(TextColor.color(255, 0, 0)))
        } else {
            sender.sendMessage(Component.text("✅ Minted $amountToRemove $symbol tokens to $walletAddress").color(TextColor.color(0, 255, 0)))
            sender.sendMessage(Component.text("Selling $amount $rawMaterialName at $price Blockcoin each.").color(TextColor.color(0, 255, 255)))
            for (slot in 0 until inventory.size) {
                val item = inventory.getItem(slot)
                if (item != null && item.type == material) {
                    if (item.amount <= amountToRemove) {
                        amountToRemove -= item.amount
                        inventory.setItem(slot, null)
                    } else {
                        item.amount -= amountToRemove
                        inventory.setItem(slot, item)
                        amountToRemove = 0
                    }
                }
                if (amountToRemove <= 0) break
            }
        }

        val coins = price*amount + 0.0 //
        walletManager.fundWalletCoin(senderUUID, coins)




        return true
    }
}
