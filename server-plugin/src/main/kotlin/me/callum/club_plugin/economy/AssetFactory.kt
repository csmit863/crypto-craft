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
import org.web3j.protocol.Web3j
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.math.BigInteger


// a singleton to interact with the AssetFactory contract

// should implement:
// - ability to mint new assets
// - record new assets as they are created, check if they already exist
// - 2 functions to return the token address of an item and vice versa

object AssetFactory {
    private lateinit var factoryAddress: String
    private lateinit var web3: Web3j
    private lateinit var txManager: RawTransactionManager

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

    fun saveAsset(name: String, symbol: String, address: String) {
        println("Saving asset: $name ($symbol) -> $address")
        assets["$name|$symbol"] = address
        saveAssets()
    }

    fun getAssetAddress(name: String, symbol: String): String? {
        println("Getting asset address for $name ($symbol)")
        return assets["$name|$symbol"]
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


    fun checkAssetExists(name: String): Boolean {
        val assetAddresses = getAllAssets()
        Bukkit.getLogger().info(assetAddresses.toString())
        Bukkit.getLogger().info("Just logged the asset addresses")

        for (assetAddress in assetAddresses) {
            try {
                val nameFunc = Function("name", emptyList(), listOf(object : TypeReference<Utf8String>() {}))
                val symbolFunc = Function("symbol", emptyList(), listOf(object : TypeReference<Utf8String>() {}))

                val nameCall = web3.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                        txManager.fromAddress,
                        assetAddress.toString(),
                        FunctionEncoder.encode(nameFunc)
                    ),
                    org.web3j.protocol.core.DefaultBlockParameterName.LATEST
                ).send()

                val symbolCall = web3.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                        txManager.fromAddress,
                        assetAddress.toString(),
                        FunctionEncoder.encode(symbolFunc)
                    ),
                    org.web3j.protocol.core.DefaultBlockParameterName.LATEST
                ).send()

                val existingName = FunctionReturnDecoder.decode(nameCall.value, nameFunc.outputParameters)
                    .firstOrNull()?.value as? String ?: continue

                val existingSymbol = FunctionReturnDecoder.decode(symbolCall.value, symbolFunc.outputParameters)
                    .firstOrNull()?.value as? String ?: continue

                if (existingName.lowercase() == name.lowercase()) {
                    println("✅ Found asset: $existingName ($existingSymbol) at ${assetAddress.value}")
                    saveAsset(existingName, existingSymbol, assetAddress.value)
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
        val function = Function(
            "createAsset",
            listOf(Utf8String(name), Utf8String(symbol)),
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

        println("Transaction sent: $txHash")

        val receipt = waitForReceipt(txHash)
        if (receipt == null) {
            println("❌ Failed to get receipt for tx: $txHash")
            return null
        }

        // Ideally decode logs to get asset address, for now simulate or log
        println("✅ Asset created in tx: $txHash, but address not yet extracted")

        // Optional: If decoded address available, save it:
        // saveAsset(name, symbol, actualAddress)

        return null
    }

    fun mintAsset(assetAddress: String, amount: Number, walletAddress: String): String? {
        // mint an asset to the user's wallet.
        val toAddress = walletAddress
        val mintFunction = Function(
            "tokenizeItems",
            listOf(
                Address(toAddress),
                org.web3j.abi.datatypes.generated.Uint256(BigInteger.valueOf(amount.toLong()))
            ),
            emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(mintFunction)

        return try {
            val transactionResponse = txManager.sendTransaction(
                gasProvider.gasPrice,
                gasProvider.getGasLimit("mint"),
                assetAddress,
                encodedFunction,
                BigInteger.ZERO
            )

            println("✅ Mint transaction sent: ${transactionResponse.transactionHash}")

            val receipt = waitForReceipt(transactionResponse.transactionHash)
            if (receipt == null || !receipt.isStatusOK) {
                println("❌ Mint failed or receipt status not OK for tx: ${transactionResponse.transactionHash}")
                return null
            }


            println("✅ Successfully minted $amount tokens to $toAddress")
            transactionResponse.transactionHash
        } catch (e: Exception) {
            println("❌ Exception during mint: ${e.message}")
            null
        }
    }

}