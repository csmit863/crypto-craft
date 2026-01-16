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

    private fun performSellBlockchain(
        playerUUID: UUID,
        material: Material,
        amount: Int,
        walletAddress: String
    ): BigDecimal {
        // EVERYTHING here runs async

        val name = material.key.key.replace("_", " ")
            .lowercase().replaceFirstChar { it.uppercase() }
        val symbol = material.name.take(4).uppercase()
        val ERC20_DECIMALS = BigInteger.TEN.pow(18)

        // asset / pair creation
        if (!AssetFactory.checkAssetExists(name)) {
            AssetFactory.createAsset(name, symbol)
            val newAddress = AssetFactory.getAssetAddress(name)
                ?: error("Asset creation failed")

            Uniswap.createPair(Blockcoin.address, newAddress)

            val adminTxManager = AssetFactory.txManager
            val adminAddress = Address("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
            val mcAsset = MinecraftAsset(newAddress, Blockcoin.web3, adminTxManager)

            val blockcoinAmount = BigInteger("1000").multiply(ERC20_DECIMALS)
            val assetAmount = ERC20_DECIMALS

            mcAsset.mint(adminAddress.toString(), assetAmount)
            Blockcoin.approveSpending(Uniswap.v2routerAddress, blockcoinAmount, adminTxManager)
            mcAsset.approveSpending(Uniswap.v2routerAddress, assetAmount, adminTxManager)

            Uniswap.addLiquidity(
                Blockcoin.address,
                newAddress,
                blockcoinAmount,
                assetAmount,
                blockcoinAmount.multiply(BigInteger("99")).divide(BigInteger("100")),
                assetAmount.multiply(BigInteger("99")).divide(BigInteger("100")),
                adminAddress,
                Uint256(BigInteger.valueOf(System.currentTimeMillis() / 1000 + 300)),
                adminTxManager
            )

            AssetFactory.saveAsset(name, Keys.toChecksumAddress(newAddress))
        }

        val assetAddress = Keys.toChecksumAddress(
            AssetFactory.getAssetAddress(name).toString()
        )

        val amountWei = BigInteger.valueOf(amount.toLong()).multiply(ERC20_DECIMALS)

        AssetFactory.mintAsset(assetAddress, amountWei, walletAddress)
            ?: error("Mint failed")

        val creds = Credentials.create(walletManager.getWalletAuth(playerUUID))
        val txManager = RawTransactionManager(Blockcoin.web3, creds)
        val asset = MinecraftAsset(assetAddress, Blockcoin.web3, txManager)

        asset.approveSpending(Uniswap.v2routerAddress, amountWei, txManager)

        val path = listOf(assetAddress, Blockcoin.address)
        val amountsOut = Uniswap.getAmountsOut(amountWei, path).get()
        val expectedOut = amountsOut.last()

        val receipt = Uniswap.swapExactTokensForTokens(
            amountWei,
            expectedOut.multiply(BigInteger("99")).divide(BigInteger("100")),
            path,
            Address(walletAddress),
            Uint256(BigInteger.valueOf(System.currentTimeMillis() / 1000 + 300)),
            txManager
        )

        require(receipt.status == "0x1") { "Swap failed" }

        return BigDecimal(expectedOut).divide(BigDecimal(ERC20_DECIMALS))
    }


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

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        // ---- BASIC VALIDATION (MAIN THREAD ONLY)

        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can sell items."))
            return true
        }

        if (args.size != 2) {
            sender.sendMessage(Component.text("Usage: /sell <item> <amount>"))
            return true
        }

        val rawMaterialName = args[0].substringAfter(":").uppercase()
        val material = Material.matchMaterial(rawMaterialName)
        val amount = args[1].toIntOrNull()

        if (material == null || amount == null || amount <= 0) {
            sender.sendMessage(Component.text("Invalid item or amount."))
            return true
        }

        val totalInInventory = sender.inventory.contents
            .filterNotNull()
            .filter { it.type == material }
            .sumOf { it.amount }

        if (totalInInventory < amount) {
            sender.sendMessage(Component.text("You don’t have enough $rawMaterialName."))
            return true
        }

        val walletAddress = walletManager.getWallet(sender.uniqueId)
        if (walletAddress == null) {
            sender.sendMessage(Component.text("You don’t have a wallet yet."))
            return true
        }

        // ---- SNAPSHOT + REMOVE ITEMS (MAIN THREAD)

        val inventorySnapshot = snapshotInventory(sender)

        if (!removeItemsExact(sender, material, amount)) {
            sender.sendMessage(Component.text("Failed to remove items."))
            return true
        }

        sender.sendMessage(Component.text("⏳ Processing sale…"))

        // ---- ASYNC BLOCKCHAIN WORK

        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            Runnable {
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
                                } blockcoins"
                            )
                        )
                    })

                } catch (e: Exception) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        restoreInventory(sender, inventorySnapshot)
                        sender.sendMessage(Component.text("❌ Sale failed. Items refunded."))
                    })
                }
            }
        )


        return true
    }

}
