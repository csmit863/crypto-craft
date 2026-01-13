package me.callum.club_plugin.economy

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger


data class AssetDetails(val name: String?, val symbol: String?)

class MinecraftAsset(private val tokenAddress: String, private val web3: Web3j, private val txManager: RawTransactionManager) {

    /**
     * Mints tokens to the specified wallet address.
     */

    fun getAssetDetails(): AssetDetails {
        val name = getName()
        val symbol = getSymbol()
        return AssetDetails(name, symbol)
    }

    private fun getName(): String? {
        val nameFunction = Function(
            "name",
            emptyList(),
            listOf(object : TypeReference<org.web3j.abi.datatypes.Utf8String>() {})
        )
        val encodedFunction = FunctionEncoder.encode(nameFunction)

        return try {
            val response = web3.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    txManager.fromAddress,
                    tokenAddress,
                    encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).send()

            val decoded = FunctionReturnDecoder.decode(response.value, nameFunction.outputParameters)
            (decoded[0] as org.web3j.abi.datatypes.Utf8String).value
        } catch (e: Exception) {
            println("Error retrieving name: ${e.message}")
            null
        }
    }
    private fun getSymbol(): String? {
        val symbolFunction = Function(
            "symbol",
            emptyList(),
            listOf(object : TypeReference<org.web3j.abi.datatypes.Utf8String>() {})
        )
        val encodedFunction = FunctionEncoder.encode(symbolFunction)

        return try {
            val response = web3.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    txManager.fromAddress,
                    tokenAddress,
                    encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).send()

            val decoded = FunctionReturnDecoder.decode(response.value, symbolFunction.outputParameters)
            (decoded[0] as org.web3j.abi.datatypes.Utf8String).value
        } catch (e: Exception) {
            println("Error retrieving symbol: ${e.message}")
            null
        }
    }

    fun mint(walletAddress: String, amount: BigInteger): String? { // has to be admin
        val mintFunction = Function(
            "tokenizeItems",
            listOf(Address(walletAddress), Uint256(amount)),
            emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(mintFunction)

        return try {
            val transactionResponse = txManager.sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                tokenAddress,
                encodedFunction,
                BigInteger.ZERO
            )

            println("Mint transaction sent: ${transactionResponse.transactionHash}")
            // Get the receipt and return the transaction hash on success
            val receipt = waitForReceipt(transactionResponse.transactionHash)
            receipt?.transactionHash  // Return the transaction hash (String?)
        } catch (e: Exception) {
            println("Exception during mint: ${e.message}")
            null
        }
    }

    /**
     * Burns tokens from the specified wallet address.
     */
    fun burn(walletAddress: String, amount: BigInteger): String? {
        val burnFunction = Function(
            "burnItems",
            listOf(Address(walletAddress), Uint256(amount)),
            emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(burnFunction)

        return try {
            val transactionResponse = txManager.sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                tokenAddress,
                encodedFunction,
                BigInteger.ZERO
            )

            println("Burn transaction sent: ${transactionResponse.transactionHash}")
            // Get the receipt and return the transaction hash on success
            val receipt = waitForReceipt(transactionResponse.transactionHash)
            receipt?.transactionHash  // Return the transaction hash (String?)
        } catch (e: Exception) {
            println("Exception during burn: ${e.message}")
            null
        }
    }


    /**
     * Returns the total supply of tokens for this asset.
     */
    fun totalSupply(): BigInteger? {
        val totalSupplyFunction = Function(
            "totalSupply",
            emptyList(),
            listOf(object : TypeReference<Uint256>() {})
        )
        val encodedFunction = FunctionEncoder.encode(totalSupplyFunction)

        return try {
            val response = web3.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    txManager.fromAddress,
                    tokenAddress,
                    encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).send()

            val decoded = FunctionReturnDecoder.decode(response.value, totalSupplyFunction.outputParameters)
            (decoded[0] as Uint256).value
        } catch (e: Exception) {
            println("Error retrieving total supply: ${e.message}")
            null
        }
    }

    /**
     * Waits for a transaction receipt with timeout.
     */
    private fun waitForReceipt(txHash: String, timeoutMs: Long = 15000): TransactionReceipt? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val receiptOpt = web3.ethGetTransactionReceipt(txHash).send().transactionReceipt
            if (receiptOpt.isPresent) {
                val receipt = receiptOpt.get()
                if (receipt.status == "0x1") {
                    println("✅ Transaction successful: $txHash")
                    return receipt
                } else {
                    println("❌ Transaction failed with status: ${receipt.status} for tx: $txHash")
                    return null
                }
            }
            Thread.sleep(1000)
        }
        println("❌ No receipt found within the timeout period for tx: $txHash")
        return null
    }

    /**
     * Approves the specified spender to spend the given amount of tokens on behalf of the caller.
     */
    public fun approveSpending(spenderAddress: String, amount: BigInteger, providedTxManager: RawTransactionManager): String? {
        val approveFunction = Function(
            "approve",
            listOf(Address(spenderAddress), Uint256(amount)), // Parameters: spender address and amount
            emptyList()  // No return values expected
        )

        val encodedFunction = FunctionEncoder.encode(approveFunction)

        return try {
            val transactionResponse = providedTxManager.sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                tokenAddress,
                encodedFunction,
                BigInteger.ZERO
            )

            println("Minecraft Asset approval transaction sent ($amount approved): ${transactionResponse.transactionHash}")

            // Get the receipt and return the transaction hash on success
            val receipt = waitForReceipt(transactionResponse.transactionHash)
            receipt?.transactionHash  // Return the transaction hash (String?)
        } catch (e: Exception) {
            println("Exception during approval: ${e.message}")
            null
        }
    }

}
