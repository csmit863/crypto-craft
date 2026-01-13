package me.callum.club_plugin.economy

import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import org.bukkit.Bukkit
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.math.BigInteger
import java.util.concurrent.CompletableFuture


// a singleton to interact with the AssetFactory contract

// should implement:
// - ability to mint new assets
// - record new assets as they are created, check if they already exist
// - 2 functions to return the token address of an item and vice versa

object AssetFactory {
    private lateinit var factoryAddress: String
    private lateinit var web3: Web3j
    public lateinit var txManager: RawTransactionManager

    private val gasProvider = DefaultGasProvider()
    private val gson = Gson()
    private val assetFile = File("plugins/ClubPlugin/assets.json")
    private val assets: MutableMap<String, String> = mutableMapOf()

    fun initialize(factoryAddress: String, web3: Web3j, txManager: RawTransactionManager): AssetFactory {
        if (!this::factoryAddress.isInitialized) {
            this.factoryAddress = factoryAddress
            this.web3 = web3
            this.txManager = txManager

            if (!assetFile.exists()) {
                assetFile.parentFile.mkdirs()
                saveAssets()
            } else {
                loadAssets()
            }
        }
        return this
    }

    private fun loadAssets() {
        try {
            FileReader(assetFile).use {
                val type = object : TypeToken<Map<String, String>>() {}.type
                val loaded: Map<String, String> = gson.fromJson(it, type) ?: emptyMap()
                assets.putAll(loaded)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveAssets() {
        try {
            FileWriter(assetFile).use {
                gson.toJson(assets, it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveAsset(name: String, address: String) {
        val normalized = normalizeName(name)

        require(address.startsWith("0x") && address.length == 42) {
            "Invalid asset address: $address"
        }

        assets[normalized] = address
        saveAssets()
    }


    fun getAssetAddress(name: String): String? {
        val normalized = normalizeName(name)
        println("Getting asset address for $normalized")
        return assets[normalized]
    }

    fun waitForReceipt(txHash: String, timeoutMs: Long = 15000): org.web3j.protocol.core.methods.response.TransactionReceipt? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val receiptOpt = web3.ethGetTransactionReceipt(txHash).send().transactionReceipt
            if (receiptOpt.isPresent) {
                val receipt = receiptOpt.get()
                if (receipt.status == "0x1") { // success
                    println("✅ Transaction successful!")
                } else {
                    println("❌ Transaction failed with status: ${receipt.status}")
                }
                return receipt
            }
            Thread.sleep(1000)
        }
        println("❌ No receipt found within timeout period.")
        return null
    }

    fun waitForReceiptAsync(
        txHash: String,
        timeoutMs: Long = 15_000,
        pollIntervalMs: Long = 1_000
    ): CompletableFuture<TransactionReceipt?> {

        return CompletableFuture.supplyAsync {
            val start = System.currentTimeMillis()

            while (System.currentTimeMillis() - start < timeoutMs) {
                val receiptOpt = web3
                    .ethGetTransactionReceipt(txHash)
                    .send()
                    .transactionReceipt

                if (receiptOpt.isPresent) {
                    return@supplyAsync receiptOpt.get()
                }

                Thread.sleep(pollIntervalMs)
            }

            null
        }
    }


    fun getAllAssets(): List<Address>{
        val getAllAssetsFunction = Function(
            "getAllAssets",
            emptyList(),
            listOf(object : TypeReference<org.web3j.abi.datatypes.DynamicArray<Address>>() {})
        )

        val encodedFunction = FunctionEncoder.encode(getAllAssetsFunction)
        val response = web3.ethCall(
            org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                txManager.fromAddress,
                factoryAddress,
                encodedFunction
            ),
            org.web3j.protocol.core.DefaultBlockParameterName.LATEST
        ).send()

        val decoded = FunctionReturnDecoder.decode(
            response.value,
            getAllAssetsFunction.outputParameters
        )

        val assetAddresses = decoded[0].value as List<Address>
        return assetAddresses
    }


    fun normalizeName(name: String?): String {
        return name?.lowercase()?.replace("[_\\s]+".toRegex(), "")?.trim() ?: ""
    }

    fun checkAssetExists(name: String): Boolean {
        val assetAddresses = getAllAssets()

        for (assetAddress in assetAddresses) {
            try {
                val nameFunc = Function("name", emptyList(), listOf(object : TypeReference<Utf8String>() {}))

                val nameCall = web3.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                        txManager.fromAddress,
                        assetAddress.toString(),
                        FunctionEncoder.encode(nameFunc)
                    ),
                    org.web3j.protocol.core.DefaultBlockParameterName.LATEST
                ).send()

                val existingName = FunctionReturnDecoder.decode(nameCall.value, nameFunc.outputParameters)
                    .firstOrNull()?.value as? String ?: continue

                // Normalize names
                if (normalizeName(existingName) == normalizeName(name)) {
                    println("✅ Found asset: $existingName at ${assetAddress.value}")
                    saveAsset(existingName, assetAddress.value)
                    return true
                }

            } catch (e: Exception) {
                println("⚠️ Error checking asset at ${assetAddress.value}: ${e.message}")
            }
        }

        println("ℹ️ No matching asset found for $name")
        return false
    }



    fun createAsset(name: String, symbol: String): String? {
        val upload_name = normalizeName(name)
        println("creating asset $upload_name")
        val function = Function(
            "createAsset",
            listOf(Utf8String(upload_name), Utf8String(symbol)),
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(function)
        val txHash = txManager.sendTransaction(
            gasProvider.gasPrice,
            gasProvider.getGasLimit("createAsset"),
            factoryAddress,
            encodedFunction,
            BigInteger.ZERO
        ).transactionHash

        println("Create asset transaction sent: $txHash")

        val receipt = waitForReceipt(txHash)
        if (receipt == null) {
            println("❌ Failed to get receipt for tx: $txHash")
            return null
        }

        // Event definition
        val assetCreatedEvent = org.web3j.abi.datatypes.Event(
            "AssetCreated",
            listOf(
                TypeReference.create(Address::class.java),
                TypeReference.create(Utf8String::class.java),
                TypeReference.create(Utf8String::class.java),
                TypeReference.create(Address::class.java)
            )
        )

        val eventSig = Hash.sha3String("AssetCreated(address,string,string,address)")

        val log = receipt.logs.firstOrNull {
            it.topics.isNotEmpty() && it.topics[0] == eventSig
        }

        if (log == null) {
            println("⚠️ AssetCreated event not found in receipt")
            return null
        }

        val decoded = FunctionReturnDecoder.decode(
            log.data,
            assetCreatedEvent.nonIndexedParameters
        )

        val assetAddress = (decoded[0].value as String)
        val createdName  = decoded[1].value as String
        val symbol       = decoded[2].value as String
        val owner        = decoded[3].value as String

        saveAsset(createdName, assetAddress)

        println("✅ Asset created: $createdName -> $assetAddress (owner $owner)")
        return assetAddress

    }


    fun mintAsset(
        assetAddress: String,
        amount: Number,
        walletAddress: String
    ): String? {
        println(assetAddress)
        // Hard guard – never attempt to mint to non-address
        if (!assetAddress.startsWith("0x") || assetAddress.length != 42) {
            println("❌ Invalid asset address: $assetAddress")
            return null
        }

        if (!walletAddress.startsWith("0x") || walletAddress.length != 42) {
            println("❌ Invalid wallet address: $walletAddress")
            return null
        }

        val mintFunction = Function(
            "tokenizeItems",
            listOf(
                Address(walletAddress),
                org.web3j.abi.datatypes.generated.Uint256(
                    BigInteger.valueOf(amount.toLong())
                )
            ),
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(mintFunction)

        return try {
            val tx = txManager.sendTransaction(
                gasProvider.gasPrice,
                gasProvider.getGasLimit("tokenizeItems"),
                assetAddress,
                encodedFunction,
                BigInteger.ZERO
            )

            val txHash = tx.transactionHash
            println("✅ Mint transaction sent: $txHash")

            val receipt = waitForReceipt(txHash)
            if (receipt == null) {
                println("❌ No receipt for mint tx: $txHash")
                return null
            }

            if (!receipt.isStatusOK) {
                println("❌ Mint reverted: $txHash")
                return null
            }

            println("✅ Successfully minted $amount tokens to $walletAddress")
            txHash

        } catch (e: Exception) {
            println("❌ Exception during mint: ${e.message}")
            null
        }
    }


}