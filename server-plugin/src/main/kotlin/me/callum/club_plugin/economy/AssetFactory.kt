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
import org.web3j.abi.datatypes.generated.Uint256
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
import org.web3j.abi.datatypes.Type
import org.web3j.protocol.core.methods.request.Transaction

// a singleton to interact with the AssetFactory contract

// should implement:
// - ability to mint new assets
// - record new assets as they are created, check if they already exist
// - 2 functions to return the token address of an item and vice versa

object AssetFactory {
    public lateinit var factoryAddress: String
    private lateinit var web3: Web3j
    public lateinit var txManager: RawTransactionManager

    private val gasProvider = DefaultGasProvider()
    private val gson = Gson()
    private val assetFile = File("plugins/crypto-craft/assets.json")
    private val assets: MutableMap<String, String> = mutableMapOf()

    fun updateFactoryAddress(newAddress: String) {
        if (!newAddress.startsWith("0x") || newAddress.length != 42) {
            throw IllegalArgumentException("Invalid factory address: $newAddress")
        }
        factoryAddress = newAddress
        println("✅ AssetFactory address updated to $factoryAddress")
    }

    fun initialize(factoryAddress: String, web3: Web3j, txManager: RawTransactionManager): AssetFactory {
        this.factoryAddress = factoryAddress
        this.web3 = web3
        this.txManager = txManager

        if (!assetFile.exists()) {
            assetFile.parentFile.mkdirs()
            saveAssets()
        } else {
            loadAssets()
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
        println("getting all assets")
        // use AssetFactory.sol "getAllAssets" function, returns array of addresses (ERC20 token addresses)
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

        println("getAllAssets response: ${response.value}")
        println("getAllAssets error: ${response.error?.message}")

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



    fun getAssetAddress(name: String): String? {
        val normalized = normalizeName(name)
        println("Getting asset address for $normalized")
        return assets[normalized]
    }

    fun checkAssetExists(name: String): Boolean {
        return assets[normalizeName(name)] != null
    }

    fun checkAssetExistsold(name: String): Boolean {
        println("running check asset exists")
        // uses getAllAssets to get all token addresses, gets name from each token contract and compare to existing assets.json
        val assetAddresses = getAllAssets()

        for (assetAddress in assetAddresses) {
            // this should NOT be written like this. network call for each asset? wtf
            // say it takes 30ms to retrieve the name from a contract to compare.
            // 30 * n assets, lets say 300. 30*300 = 9000 ms. 9 seconds of delay, this is unacceptable
            // asset search should not require ANY networking. a mapping of coin addresses and coin names should be kept in the JSON file.

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

        val uploadName = normalizeName(name)

        println("creating asset $uploadName")

        val function = Function(
            "createAsset",
            listOf(
                Utf8String(uploadName),
                Utf8String(symbol)
            ),
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(function)

        // -----------------------------
        // Dynamic gas price
        // -----------------------------

        val gasPrice = web3.ethGasPrice()
            .send()
            .gasPrice

        println("Gas price: $gasPrice")

        // -----------------------------
        // Estimate gas
        // -----------------------------

        val estimateTx = Transaction.createFunctionCallTransaction(
            txManager.fromAddress,
            null,
            gasPrice,
            null,
            factoryAddress,
            encodedFunction
        )

        val estimateResponse = web3.ethEstimateGas(estimateTx).send()

        require(estimateResponse.amountUsed != null) {
            "Failed to estimate gas: ${estimateResponse.error?.message}"
        }

        // add 20% safety buffer
        val gasLimit = estimateResponse.amountUsed
            .multiply(BigInteger("12"))
            .divide(BigInteger.TEN)

        println("Estimated gas: ${estimateResponse.amountUsed}")
        println("Buffered gas limit: $gasLimit")

        // -----------------------------
        // Send transaction
        // -----------------------------

        val response = txManager.sendTransaction(
            gasPrice,
            gasLimit,
            factoryAddress,
            encodedFunction,
            BigInteger.ZERO
        )

        println("FULL RESPONSE: $response")
        println("TX HASH: ${response.transactionHash}")
        println("ERROR: ${response.error}")

        require(response.transactionHash != null) {
            "Transaction failed: ${response.error?.message}"
        }

        val txHash = response.transactionHash

        println("Create asset transaction sent: $txHash")

        // -----------------------------
        // Wait for receipt
        // -----------------------------

        val receipt = waitForReceipt(txHash)

        require(receipt != null) {
            "Failed to get receipt for tx: $txHash"
        }

        require(receipt.status == "0x1") {
            "Transaction reverted: $txHash"
        }

        // -----------------------------
        // Decode AssetCreated event
        // -----------------------------

        val eventSig =
            "0x" + Hash.sha3String(
                "AssetCreated(address,string,string,address)"
            ).removePrefix("0x")

        val log = receipt.logs.firstOrNull {
            it.topics.isNotEmpty() &&
                    it.topics[0].equals(eventSig, ignoreCase = true)
        }

        require(log != null) {
            "AssetCreated event not found"
        }

        val decoded = FunctionReturnDecoder.decode(
            log.data,
            listOf(
                object : TypeReference<Address>() {} as TypeReference<Type<*>>,
                object : TypeReference<Utf8String>() {} as TypeReference<Type<*>>,
                object : TypeReference<Utf8String>() {} as TypeReference<Type<*>>,
                object : TypeReference<Address>() {} as TypeReference<Type<*>>
            )
        )

        val assetAddress = (decoded[0] as Address).value
        val createdName = (decoded[1] as Utf8String).value
        val createdSymbol = (decoded[2] as Utf8String).value
        val owner = (decoded[3] as Address).value

        saveAsset(createdName, assetAddress)

        println(
            "✅ Asset created: " +
                    "$createdName ($createdSymbol) -> " +
                    "$assetAddress owner=$owner"
        )

        return assetAddress
    }


    fun mintAsset(
        assetAddress: String,
        amount: BigInteger,
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
                Uint256(amount)
            ),
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(mintFunction)

        return try {
            println("gasPrice = ${gasProvider.gasPrice}")
            println("gasLimit = ${gasProvider.getGasLimit("tokenizeItems")}")
            println("txManager = $txManager")
            val estimateTx = Transaction.createFunctionCallTransaction(
                txManager.fromAddress,
                null,
                null,
                null,
                assetAddress,
                encodedFunction
            )

            val (gasPrice, gasLimit) = GasUtils.estimateGas(web3, estimateTx)

            val tx = txManager.sendTransaction(
                gasPrice,
                gasLimit,
                assetAddress,
                encodedFunction,
                BigInteger.ZERO
            )
            println("FULL RESPONSE: $tx")
            val txHash = requireNotNull(tx.transactionHash) {
                "Transaction sent but hash is null (RPC or txManager failure)"
            }
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
            println("❌ Mint failed with exception:")
            e.printStackTrace();
            null
        }
    }

    fun syncFromChain() {
        println("🔄 Syncing assets from chain...")
        try {
            val addresses = getAllAssets()
            if (addresses.isEmpty()) {
                println("ℹ️ No assets found on chain.")
                return
            }
            for (addr in addresses) {
                try {
                    val nameFunc = Function(
                        "name",
                        emptyList(),
                        listOf(object : TypeReference<Utf8String>() {})
                    )
                    val response = web3.ethCall(
                        org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            "0x0000000000000000000000000000000000000000",
                            addr.value,
                            FunctionEncoder.encode(nameFunc)
                        ),
                        org.web3j.protocol.core.DefaultBlockParameterName.LATEST
                    ).send()

                    val name = FunctionReturnDecoder.decode(response.value, nameFunc.outputParameters)
                        .firstOrNull()?.value as? String ?: continue

                    saveAsset(name, addr.value)
                    println("✅ Synced: $name -> ${addr.value}")
                } catch (e: Exception) {
                    println("⚠️ Failed to sync asset at ${addr.value}: ${e.message}")
                }
            }
            println("✅ Asset sync complete. ${assets.size} assets loaded.")
        } catch (e: Exception) {
            println("❌ Asset sync failed: ${e.message}")
        }
    }


}