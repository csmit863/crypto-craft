package me.callum.club_plugin.economy

import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import java.util.*

import me.callum.club_plugin.economy.Blockcoin
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys

import org.web3j.protocol.Web3j
import org.web3j.crypto.Keys.createEcKeyPair
import org.web3j.crypto.Wallet.createStandard
import org.web3j.crypto.Keys.getAddress
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.utils.Convert
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CompletableFuture


class WalletManager(private val blockcoin: Blockcoin) : Listener  {
    private val gson = Gson()
    private val walletFile = File("plugins/ClubPlugin/wallets.json")
    private val playerWallets = mutableMapOf<UUID, String>() // Maps Minecraft UUID to Ethereum address
    private val playerPrivateKeys = mutableMapOf<UUID, String>() // Maps Minecraft UUID to Ethereum address
    private val balances = mutableMapOf<UUID, Double>() // ClubCoin balances

    public var tokenizeItem: TokenizeItem = TokenizeItem(blockcoin)
    // todo: add 'export wallet' function which gives the user their private key
    init {
        loadWallets() // Load data on startup
    }
    private fun saveWallets() {
        try {
            val data = playerWallets.mapValues { (uuid, address) ->
                mapOf("address" to address, "privateKey" to playerPrivateKeys[uuid])
            }
            walletFile.parentFile.mkdirs() // Ensure directory exists
            FileWriter(walletFile).use { it.write(gson.toJson(data)) }
            Bukkit.getLogger().info("Wallets saved successfully!")
        } catch (e: Exception) {
            Bukkit.getLogger().severe("Failed to save wallets: ${e.message}")
        }
    }
    private fun loadWallets() {
        if (!walletFile.exists()) return
        try {
            FileReader(walletFile).use { reader ->
                val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
                val data: Map<String, Map<String, String>> = gson.fromJson(reader, type)
                playerWallets.putAll(data.mapKeys { UUID.fromString(it.key) }.mapValues { it.value["address"]!! })
                playerPrivateKeys.putAll(data.mapKeys { UUID.fromString(it.key) }.mapValues { it.value["privateKey"]!! })
                Bukkit.getLogger().info("Wallets loaded successfully!")
            }
        } catch (e: Exception) {
            Bukkit.getLogger().severe("Failed to load wallets: ${e.message}")
        }
    }

    // will use web3 later to sign transactions
    val web3 = Web3j.build(HttpService("https://testnet.qutblockchain.club"))


    fun hasWallet(playerUUID: UUID): Boolean {
        return playerWallets.containsKey(playerUUID)
    }

    fun createWallet(playerUUID: UUID) {
        if (!hasWallet(playerUUID)) {
            val (ethAddress, privateKey) = generateEthereumAddress()
            playerWallets[playerUUID] = ethAddress
            playerPrivateKeys[playerUUID] = privateKey

            val player = Bukkit.getPlayer(playerUUID)

            player?.sendMessage("§aWallet created! Your address: $ethAddress")
            fundWallet(ethAddress)
            Bukkit.getLogger().info("Wallet created for player ${player?.name} with address: $ethAddress")
        }
        saveWallets()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun fundWallet(toAddress: String) {
        // pre generated anvil wallet private keys. dont get too excited.
        val senderPrivateKey = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80" // Replace with actual private key
        val senderAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" // Replace with actual sender address

        val web3 = Web3j.build(HttpService("https://testnet.qutblockchain.club")) // Change for correct RPC

        CompletableFuture.runAsync {
            try {
                val credentials = Credentials.create(senderPrivateKey)

                // Get transaction nonce
                val nonce = web3.ethGetTransactionCount(senderAddress, DefaultBlockParameterName.LATEST)
                    .send().transactionCount

                // Gas settings
                val gasPrice = web3.ethGasPrice().send().gasPrice
                val gasLimit = BigInteger.valueOf(21000) // Standard for ETH transfer

                // Amount to send (1 ETH in Wei)
                val value = Convert.toWei("1", Convert.Unit.ETHER).toBigInteger()

                // Create transaction
                val rawTx = RawTransaction.createEtherTransaction(
                    nonce,
                    gasPrice,
                    gasLimit,
                    toAddress,
                    value
                )

                // Sign transaction
                val signedMessage = TransactionEncoder.signMessage(rawTx, credentials)
                val hexValue = "0x" + signedMessage.toHexString()

                Bukkit.getLogger().info("adding ether to new account...")

                // Send transaction
                val response = web3.ethSendRawTransaction(hexValue).send()

                if (response.hasError()) {
                    Bukkit.getLogger().severe("Failed to send ETH: ${response.error.message}")
                } else {
                    Bukkit.getLogger().info("Successfully sent 1 test ETH to $toAddress. Tx: ${response.transactionHash}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Bukkit.getLogger().severe("Error funding wallet: ${e.message}")
            }
        }
    }

    fun getWallet(playerUUID: UUID): String? {
        return playerWallets[playerUUID] // Returns Ethereum address
    }

    fun getBalance(playerUUID: UUID): CompletableFuture<BigDecimal> {
        val walletAddress = getWallet(playerUUID) ?: return CompletableFuture.failedFuture(Exception("No wallet found"))

        return blockcoin.getBalance(walletAddress)
    }

    fun sendTokens(fromPlayer: UUID, toPlayerOrAddress: String, amount: Double): CompletableFuture<Boolean> {
        // will need to manually top up user wallet on each transaction, or allocate a small amount of ether upon joining
        val fromWallet = playerWallets[fromPlayer] ?: return CompletableFuture.completedFuture(false)
        // val toWallet = playerWallets[toPlayerOrAddress] ?: return CompletableFuture.completedFuture(false)
        val privateKey = playerPrivateKeys[fromPlayer] ?: return CompletableFuture.completedFuture(false)
        val fromPlayerName = Bukkit.getPlayer(fromPlayer)?.name ?: "Unknown Player"

        Bukkit.getLogger().info("Player $fromPlayerName is sending $amount tokens from wallet $fromWallet")

        // Check if the destination is a player or an Ethereum address
        // instead of starts with 0x, use web3j's checksum method
        return if (toPlayerOrAddress.startsWith("0x") && toPlayerOrAddress.length == 42) {
            // It's an Ethereum address
            Bukkit.getLogger().info("sending tokens to ethereum address")
            Bukkit.getLogger().info(toPlayerOrAddress)
            blockcoin.sendTokens(fromWallet, toPlayerOrAddress, amount, privateKey)
        } else {
            // It's a player
            Bukkit.getLogger().info("sending tokens to player")
            Bukkit.getLogger().info(toPlayerOrAddress)
            val toPlayerUUID = UUID.fromString(toPlayerOrAddress)
            val toPlayer = Bukkit.getPlayer(toPlayerUUID)
            Bukkit.getLogger().info(""+toPlayer)
            if (toPlayer != null) {
                // Player found, send tokens to the player's wallet address
                val toWallet = playerWallets[toPlayer.uniqueId]
                Bukkit.getLogger().info(toWallet)
                if (toWallet != null) {
                    blockcoin.sendTokens(fromWallet, toWallet, amount, privateKey)
                } else {
                    CompletableFuture.completedFuture(false) // Receiver does not have a wallet
                }
            } else {
                Bukkit.getLogger().info("receiver player not found")
                CompletableFuture.completedFuture(false) // Receiver player not found
            }
        }
    }

    private fun generateEthereumAddress(): Pair<String, String> {
        // Implement actual logic to generate an EVM wallet keypair
        val key = createEcKeyPair() // Generate the key pair
        val wallet = createStandard("password", key) // Generate the wallet from the key pair

        val address = Keys.toChecksumAddress(wallet.address) // Convert to checksummed address
        val privateKey = key.privateKey.toString(16) // Get the private key in hexadecimal format

        return Pair(address, privateKey) // Return both address and private key
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        createWallet(player.uniqueId)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val killer = player.killer

        val lossAmount = getBalance(player.uniqueId) // * 0.1 // 10% of coins lost

        if (killer != null) {
            // Transfer coins to the killer
            val killerBalance = getBalance(killer.uniqueId)
            //setBalance(killer.uniqueId, killerBalance + lossAmount)
            player.sendMessage("§cYou lost $lossAmount ClubCoins to ${killer.name}!")
            killer.sendMessage("§aYou stole $lossAmount ClubCoins from ${player.name}!")
        } else {
            // Burn coins if death was not PvP
            //setBalance(player.uniqueId, getBalance(player.uniqueId) - lossAmount)
            player.sendMessage("§cYou lost $lossAmount ClubCoins (burned).")
        }
    }
}
