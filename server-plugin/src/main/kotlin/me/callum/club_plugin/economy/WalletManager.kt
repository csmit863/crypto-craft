package me.callum.club_plugin.economy

import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import java.util.*

import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys

import org.web3j.protocol.Web3j
import org.web3j.crypto.Keys.createEcKeyPair
import org.web3j.crypto.Wallet.createStandard
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.tx.RawTransactionManager
import org.web3j.utils.Convert
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CompletableFuture


object WalletManager: Listener {
    public lateinit var blockcoin: Blockcoin
    public lateinit var web3: Web3j
    public lateinit var txManager: RawTransactionManager

    private val gson = Gson()
    private val walletFile = File("plugins/ClubPlugin/wallets.json")
    private val playerWallets = mutableMapOf<UUID, String>() // Maps Minecraft UUID to Ethereum address
    private val playerPrivateKeys = mutableMapOf<UUID, String>() // Maps Minecraft UUID to Ethereum address
    private val balances = mutableMapOf<UUID, Double>() // ClubCoin balances

    // todo: add 'export wallet' function which gives the user their private key
    fun initialize(blockcoinInstance: Blockcoin, web3j: Web3j, txManager: RawTransactionManager): WalletManager {
        this.blockcoin = blockcoinInstance;
        this.web3 = web3j;
        this.txManager = txManager;
        loadWallets() // Load data on startup
        return this
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
            fundWalletEth(ethAddress)
            Bukkit.getLogger().info("Wallet created for player ${player?.name} with address: $ethAddress")
        }
        saveWallets()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun fundWalletEth(toAddress: String): CompletableFuture<Boolean> {
        val senderPrivateKey = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80" // Replace with actual private key
        val senderAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" // Replace with actual sender address

        val web3 = Web3j.build(HttpService("https://testnet.qutblockchain.club")) // Change for correct RPC

        return CompletableFuture.supplyAsync {
            try {
                val credentials = Credentials.create(senderPrivateKey)

                // Get transaction nonce
                val nonce = web3.ethGetTransactionCount(senderAddress, DefaultBlockParameterName.LATEST).send().transactionCount

                // Gas settings
                val gasPrice = web3.ethGasPrice().send().gasPrice
                val gasLimit = BigInteger.valueOf(21000) // Standard for ETH transfer

                // Amount to send (0.01 ETH in Wei)
                val value = Convert.toWei("0.05", Convert.Unit.ETHER).toBigInteger()

                // Create transaction
                val rawTx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, toAddress, value)

                // Sign transaction
                val signedMessage = TransactionEncoder.signMessage(rawTx, credentials)
                val hexValue = "0x" + signedMessage.toHexString()

                Bukkit.getLogger().info("Adding Ether to new account...")

                // Send transaction
                val response = web3.ethSendRawTransaction(hexValue).send()

                if (response.hasError()) {
                    Bukkit.getLogger().severe("Failed to send ETH: ${response.error.message}")
                    return@supplyAsync false // Return failure
                } else {
                    Bukkit.getLogger().info("Successfully sent 0.01 ETH to $toAddress. Tx: ${response.transactionHash}")
                    return@supplyAsync true // Return success
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Bukkit.getLogger().severe("Error funding wallet: ${e.message}")
                return@supplyAsync false // Return failure
            }
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    fun fundWalletCoin(playerUUID: UUID, amount: Double){
        val playerWallet = playerWallets[playerUUID]
        val privateKey = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
        blockcoin.sendTokens("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", playerWallet, amount, privateKey)
    }

    fun getWallet(playerUUID: UUID): String? {
        return playerWallets[playerUUID] // Returns Ethereum address
    }

    fun getWalletAuth(playerUUID: UUID): String? {
        return playerPrivateKeys[playerUUID] // Returns Ethereum address
    }

    fun getBalance(playerUUID: UUID): CompletableFuture<BigDecimal> {
        val walletAddress = getWallet(playerUUID) ?: return CompletableFuture.failedFuture(Exception("No wallet found"))
        val balance = blockcoin.getBalance(walletAddress)
        return balance
    }

    fun getBalanceWei(playerUUID: UUID): CompletableFuture<BigInteger> {
        val walletAddress = getWallet(playerUUID) ?: return CompletableFuture.failedFuture(Exception("No wallet found"))
        val balance = blockcoin.getBalanceWei(walletAddress)
        return balance
    }

    fun getEthBalance(walletAddress: String): CompletableFuture<BigDecimal> {
        return CompletableFuture.supplyAsync {
            try {
                // Fetch the balance in Wei
                val balanceWei = web3.ethGetBalance(walletAddress, DefaultBlockParameterName.LATEST).send().balance
                // Convert Wei to Ether
                Convert.fromWei(balanceWei.toString(), Convert.Unit.ETHER)
            } catch (e: Exception) {
                Bukkit.getLogger().severe("Error getting ETH balance for wallet: $walletAddress. ${e.message}")
                BigDecimal.ZERO // Return 0 in case of an error
            }
        }
    }

    fun sendTokens(fromPlayer: UUID, toPlayerOrAddress: String, amount: Double): CompletableFuture<Boolean> {
        val fromWallet = playerWallets[fromPlayer] ?: return CompletableFuture.completedFuture(false)
        val privateKey = playerPrivateKeys[fromPlayer] ?: return CompletableFuture.completedFuture(false)

        return getEthBalance(fromWallet).thenCompose { ethBalance ->
            // Check if the balance is below the threshold
            println(ethBalance);
            Bukkit.getLogger().info(ethBalance.toString());
            if (ethBalance < BigDecimal(0.05)) {
                // Fund wallet if balance is insufficient
                fundWalletEth(fromWallet).thenCompose {
                    // Proceed with sending tokens after wallet has been funded
                    executeTokenTransfer(fromWallet, toPlayerOrAddress, amount, privateKey)
                }
            } else {
                // Proceed with sending tokens
                executeTokenTransfer(fromWallet, toPlayerOrAddress, amount, privateKey)
            }
        }
    }

    private fun executeTokenTransfer(fromWallet: String, toPlayerOrAddress: String, amount: Double, privateKey: String): CompletableFuture<Boolean> {
        // Determine if sending to a player or an Ethereum address
        return if (toPlayerOrAddress.startsWith("0x") && toPlayerOrAddress.length == 42) {
            blockcoin.sendTokens(fromWallet, toPlayerOrAddress, amount, privateKey)
        } else {
            // Sending to a player
            val toPlayerUUID = UUID.fromString(toPlayerOrAddress)
            val toPlayer = Bukkit.getPlayer(toPlayerUUID)

            if (toPlayer != null) {
                val toWallet = playerWallets[toPlayer.uniqueId]
                if (toWallet != null) {
                    blockcoin.sendTokens(fromWallet, toWallet, amount, privateKey)
                } else {
                    CompletableFuture.completedFuture(false) // Receiver does not have a wallet
                }
            } else {
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
        val adminAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"

        getBalanceWei(player.uniqueId).thenAccept { balanceWei ->
            if (balanceWei <= BigInteger.ZERO) return@thenAccept

            // 10% using integer math (correct)
            val lossWei = balanceWei.multiply(BigInteger.TEN).divide(BigInteger.valueOf(100))
            if (lossWei <= BigInteger.ZERO) return@thenAccept

            // Convert ONCE at the boundary
            val lossTokens = BigDecimal(lossWei)
                .divide(BigDecimal.TEN.pow(18))
                .toDouble()

            if (lossTokens <= 0.0) return@thenAccept

            val recipient = when {
                killer != null -> getWallet(killer.uniqueId)
                else -> adminAddress
            } ?: adminAddress

            sendTokens(player.uniqueId, recipient, lossTokens)
                .thenAccept { success ->
                    if (!success) return@thenAccept

                    val display = "%.4f".format(lossTokens)

                    if (killer != null) {
                        player.sendMessage("§cYou lost $display Blockcoins to ${killer.name}.")
                        killer.sendMessage("§aYou gained $display Blockcoins from ${player.name}.")
                    } else {
                        player.sendMessage("§cWhen you died, you lost $display Blockcoins.")
                    }
                }
                .exceptionally {
                    Bukkit.getLogger().severe("Death tax failed: ${it.message}")
                    null
                }
        }
    }

}
