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
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys
import org.web3j.tx.RawTransactionManager
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID


/**
 * To sell items, some contracts have to exist:
 * 1. The token contracts for Blockcoin and the specified item [x]
 * 2. The pair factory contract []
 * 3. The pair contract for Blockcoin and the tokenized item []
 * If these contracts do not exist, they must be created.
 * The first time an item is created, the player who does so should receive
 * an award of 1000 blockcoins. This is how new blockcoin will enter circulation.
 * These 1000 blockcoins will be added to the pair pool automatically.
 * How it works:
 * Player sells 1 dirt -> dirt tokenized -> pair created (dirt/blck)
 * -> liquidity added (1 dirt / 1000 blck) -> 
 */

// logarithmic cap?
// bulk buy?
// what if a player buys 200,000 sticks?
/**
 * // MAIN THREAD
 * - validate command
 * - snapshot inventory
 * - remove items
 * - tell player "processing..."
 *
 * ASYNC THREAD
 * - ALL blockchain logic
 * - throw if anything fails
 *
 * MAIN THREAD (callback)
 * - success → send success message
 * - failure → restore inventory, send error
 *
 */

class SellItemsCommand(
    private val plugin: JavaPlugin,
    private val walletManager: WalletManager
) : CommandExecutor, TabCompleter {
    // utils

    private fun snapshotInventory(player: Player): Array<ItemStack?> {
        return player.inventory.contents.map { it?.clone() }.toTypedArray()
    }

    private fun restoreInventory(player: Player, snapshot: Array<ItemStack?>) {
        player.inventory.contents = snapshot
    }

    private fun removeItemsExact(
        player: Player,
        material: Material,
        amount: Int
    ): Boolean {
        var remaining = amount
        val inv = player.inventory

        for (slot in 0 until inv.size) {
            val item = inv.getItem(slot) ?: continue
            if (item.type != material) continue

            // skip damaged tools
            if (material.maxDurability > 0) {
                val meta = item.itemMeta
                if (meta is org.bukkit.inventory.meta.Damageable && meta.damage > 0) continue
            }

            val take = minOf(item.amount, remaining)
            item.amount -= take
            remaining -= take

            if (item.amount <= 0) {
                inv.setItem(slot, null)
            } else {
                inv.setItem(slot, item)
            }

            if (remaining == 0) break
        }

        return remaining == 0
    }

    fun ensureLiquidity(assetAddress: String): Boolean {
        val pair = Uniswap.getPair(Blockcoin.address, assetAddress).get()

        if (pair == null ||
            pair == "0x0000000000000000000000000000000000000000"
        ) {
            return false
        }

        val reserves = try {
            Uniswap.getReserves(pair).get()
        } catch (e: Exception) {
            return false
        }

        return reserves.first > BigInteger.ZERO && reserves.second > BigInteger.ZERO
    }

    private fun performSellBlockchain(
        playerUUID: UUID,
        material: Material,
        amount: Int,
        walletAddress: String
    ): BigDecimal {
        println("performSellBlockchain()")

        val name = material.key.key.replace("_", " ")
            .lowercase().replaceFirstChar { it.uppercase() }
        val symbol = material.name.take(4).uppercase()
        val ERC20_DECIMALS = BigInteger.TEN.pow(18)

        // create asset if it doesn't exist — factory handles pair + liquidity seeding
        if (!AssetFactory.checkAssetExists(name)) {
            AssetFactory.createAsset(name, symbol) ?: error("Asset creation failed")
        }

        val assetAddress = Keys.toChecksumAddress(
            AssetFactory.getAssetAddress(name) ?: error("Asset address not found")
        )

        val amountWei = BigInteger.valueOf(amount.toLong()).multiply(ERC20_DECIMALS)

        // single contract call: mint + swap + send blockcoin to player
        val blockCoinReceived = AssetFactory.tokenizeAndSellAsset(
            assetAddress,
            walletAddress,
            amountWei
        ) ?: error("Sell failed")

        return blockCoinReceived
    }


    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val marketAssets = AssetFactory.getAssetNames()
                .filter { it.startsWith(args[0], ignoreCase = true) }

            // if we have market matches, show those + hand
            // if not, fall back to all materials (for first-time sells)
            return if (marketAssets.isNotEmpty() || args[0].equals("hand", ignoreCase = true)) {
                val suggestions = mutableListOf("hand")
                suggestions.addAll(marketAssets)
                suggestions.filter { it.startsWith(args[0], ignoreCase = true) }.sorted()
            } else {
                val allMaterials = Material.values()
                    .map { it.key.key }
                    .filter { it.startsWith(args[0], ignoreCase = true) }
                    .sorted()
                listOf("hand") + allMaterials
            }
        }

        if (args.size == 2) {
            return listOf("all", "1", "8", "16", "32", "64")
                .filter { it.startsWith(args[1], ignoreCase = true) }
        }

        return emptyList()
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can sell items."))
            return true
        }

        if (args.size != 2) {
            sender.sendMessage(Component.text("Usage: /sell <item> <amount>"))
            return true
        }

        val rawArg = args[0]

        // resolve material — "hand" uses held item
        val material: Material
        val rawMaterialName: String

        if (rawArg.equals("hand", ignoreCase = true)) {
            val heldItem = sender.inventory.itemInMainHand
            if (heldItem.type == Material.AIR) {
                sender.sendMessage(Component.text("You're not holding anything."))
                return true
            }
            material = heldItem.type
            rawMaterialName = material.name.lowercase().replace("_", " ")
        } else {
            val resolved = Material.matchMaterial(rawArg.substringAfter(":").uppercase())
            if (resolved == null) {
                sender.sendMessage(Component.text("Unknown item: $rawArg"))
                return true
            }
            material = resolved
            rawMaterialName = material.name.lowercase().replace("_", " ")
        }

        // ---- DURABILITY CHECK
        if (material.maxDurability > 0) {
            // it's a tool/weapon/armour — check if held item is at full durability
            val heldItem = sender.inventory.itemInMainHand
            val isHand = rawArg.equals("hand", ignoreCase = true)

            if (isHand) {
                val meta = heldItem.itemMeta
                if (meta is org.bukkit.inventory.meta.Damageable && meta.damage > 0) {
                    sender.sendMessage(Component.text("❌ You can only sell undamaged tools."))
                    return true
                }
            } else {
                // check all matching items in inventory — reject if any are damaged
                val damaged = sender.inventory.contents
                    .filterNotNull()
                    .filter { it.type == material }
                    .any { item ->
                        val meta = item.itemMeta
                        meta is org.bukkit.inventory.meta.Damageable && meta.damage > 0
                    }
                if (damaged) {
                    sender.sendMessage(Component.text("❌ You can only sell undamaged tools."))
                    return true
                }
            }
        }

        // resolve amount: "all" sells everything of that type
        val totalInInventory = sender.inventory.contents
            .filterNotNull()
            .filter { it.type == material }
            .filter { item ->
                if (material.maxDurability > 0) {
                    val meta = item.itemMeta
                    meta !is org.bukkit.inventory.meta.Damageable || meta.damage == 0
                } else true
            }
            .sumOf { it.amount }

        val amount: Int = if (args[1].equals("all", ignoreCase = true)) {
            if (totalInInventory == 0) {
                sender.sendMessage(Component.text("You don't have any $rawMaterialName."))
                return true
            }
            totalInInventory
        } else {
            val parsed = args[1].toIntOrNull()
            if (parsed == null || parsed <= 0) {
                sender.sendMessage(Component.text("Invalid amount."))
                return true
            }
            if (totalInInventory < parsed) {
                sender.sendMessage(Component.text("You only have $totalInInventory $rawMaterialName."))
                return true
            }
            parsed
        }

        val walletAddress = walletManager.getWallet(sender.uniqueId)
        if (walletAddress == null) {
            sender.sendMessage(Component.text("You don't have a wallet yet."))
            return true
        }

        // ---- SNAPSHOT + REMOVE ITEMS (MAIN THREAD)
        val inventorySnapshot = snapshotInventory(sender)

        if (!removeItemsExact(sender, material, amount)) {
            sender.sendMessage(Component.text("Failed to remove items."))
            return true
        }

        sender.sendMessage(Component.text("⏳ Selling $amount $rawMaterialName…"))

        // ---- ASYNC BLOCKCHAIN WORK
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val receivedBlockcoin = performSellBlockchain(
                    sender.uniqueId,
                    material,
                    amount,
                    walletAddress
                )

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage(
                        Component.text(
                            "✅ Sold $amount $rawMaterialName for ${
                                receivedBlockcoin.stripTrailingZeros()
                            } BLOCK"
                        )
                    )
                })

            } catch (e: Exception) {
                println("❌ Exception in sell: ${e.javaClass.name}: ${e.message}")
                e.printStackTrace()
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    restoreInventory(sender, inventorySnapshot)
                    sender.sendMessage(Component.text("❌ Sale failed. Items refunded."))
                })
            }
        })

        return true
    }

}
