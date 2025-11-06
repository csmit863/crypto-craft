package me.callum.club_plugin.economy

import org.bukkit.Bukkit
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.Utf8String
import java.math.BigInteger

import java.math.BigDecimal
import java.util.concurrent.CompletableFuture

import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.utils.Convert
import java.math.RoundingMode


class BlockcoinManager {


    // default values
    public var contractAddress: String = "0x5FbDB2315678afecb367f032d93F642f64180aa3"
    public var factoryAddress: String = "0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512"
    public var rpcUrl: String = "https://testnet.qutblockchain.club"
    public var web3j: Web3j = Web3j.build(HttpService(rpcUrl))

    private val decimals: Int = 18 // Change if your token has different decimal places



    fun setWeb3(newRpcUrl: String) {
        rpcUrl = newRpcUrl
        web3j = Web3j.build(HttpService(newRpcUrl))
    }

    /*
    fun setContractAddress(newContractAddress: String) {
        contractAddress = newContractAddress
        Bukkit.getLogger().info("Token contract address updated to: $newContractAddress")
    }

    fun setFactoryAddress(newFactoryAddress: String) {
        factoryAddress = newFactoryAddress
        Bukkit.getLogger().info("Factory address updated to: $newFactoryAddress")
    }

     */

    /**
     * Retrieves the ERC-20 token balance for a given Ethereum address.
     * @param walletAddress The address to check the balance of.
     * @return Balance as a BigInteger (converted from smallest token unit).
     */
    public fun getBalance(walletAddress: String): CompletableFuture<BigDecimal> {
        return try {
            val function = Function(
                "balanceOf",
                listOf(Address(walletAddress)),
                listOf(object : TypeReference<Uint256>() {})
            )

            val encodedFunction = FunctionEncoder.encode(function)
            val ethCall = web3j.ethCall(
                Transaction.createEthCallTransaction(walletAddress, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).sendAsync()

            ethCall.thenApply { result ->
                val decodedValues = org.web3j.abi.FunctionReturnDecoder.decode(result.value, function.outputParameters)
                if (decodedValues.isNotEmpty()) {
                    val balanceInBaseUnit = (decodedValues[0] as Uint256).value
                    // Use BigDecimal constructor that directly takes a BigInteger
                    BigDecimal(balanceInBaseUnit).divide(BigDecimal.TEN.pow(18)) // Adjust for decimals (assuming 18)
                } else {
                    BigDecimal.ZERO
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CompletableFuture.completedFuture(BigDecimal.ZERO) // Return zero on error
        }
    }



    @OptIn(ExperimentalStdlibApi::class)
    public fun sendTokens(fromAddress: String, toAddress: String, amount: Double, privateKey: String): CompletableFuture<Boolean> {
        Bukkit.getLogger().info("Amount: $amount")
        return CompletableFuture.supplyAsync {
            try {
                val ethBalance = web3j.ethGetBalance(fromAddress, org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send().balance
                Bukkit.getLogger().info("ETH Balance: $ethBalance")

                // Convert amount to smallest unit (wei)
                val bigDecimalAmount = BigDecimal(amount).setScale(18, RoundingMode.HALF_UP) // Ensures 18 decimal places
                val amountInWei = Convert.toWei(bigDecimalAmount, Convert.Unit.ETHER).toBigIntegerExact()
                Bukkit.getLogger().info("Amount in Wei: $amountInWei")

                // Build the transfer function
                val transferFunction = Function(
                    "transfer",
                    listOf(Address(toAddress), Uint256(amountInWei)),
                    emptyList()
                )

                // Encode the function
                val encodedFunction = FunctionEncoder.encode(transferFunction)

                // Get the credentials for signing the transaction
                val credentials = Credentials.create(privateKey)
                Bukkit.getLogger().info("DEBUG: Private key loaded successfully.")

                // Fetch nonce, gas price, gas limit
                val nonce = web3j.ethGetTransactionCount(fromAddress, org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send().transactionCount
                val gasPrice = web3j.ethGasPrice().send().gasPrice
                val gasLimit = BigInteger.valueOf(60000) // Adjust gas as needed
                Bukkit.getLogger().info("Nonce, Gas Price, Gas Limit initialized.")

                // Create a raw transaction
                val rawtx = RawTransaction.createTransaction(
                    nonce, gasPrice, gasLimit, contractAddress, BigInteger.ZERO, encodedFunction
                )

                // Sign and send transaction
                val signedTransaction = TransactionEncoder.signMessage(rawtx, credentials)
                Bukkit.getLogger().info("Transaction signed.")

                val response = web3j.ethSendRawTransaction("0x" + signedTransaction.toHexString()).send()
                if (response.hasError()) {
                    throw Exception("Transaction failed: ${response.error.message}")
                }

                val txHash = response.transactionHash
                Bukkit.getLogger().info("Transaction Hash: $txHash")

                // Wait for the transaction receipt
                var receipt: TransactionReceipt? = null
                var attempts = 0
                val maxAttempts = 20
                val delayMillis = 3000L

                while (receipt == null && attempts < maxAttempts) {
                    Thread.sleep(delayMillis)
                    receipt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt.orElse(null)
                    attempts++
                }

                if (receipt == null) {
                    Bukkit.getLogger().info("Transaction $txHash is still pending after ${maxAttempts * (delayMillis / 1000)} seconds.")
                    return@supplyAsync true // Consider the transaction as pending, not failed
                }

                return@supplyAsync if (receipt.status == "0x1") {
                    Bukkit.getLogger().info("Transaction $txHash succeeded.")
                    true
                } else {
                    Bukkit.getLogger().info("Transaction $txHash failed.")
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@supplyAsync false
            }
        }
    }




    /**
     * Checks if the ERC-20 token contract exists by calling its `name()` method.
     * @return True if the contract exists and responds, false otherwise.
     */
    fun contractExists(): CompletableFuture<Boolean> {
        return try {
            val function = Function(
                "name",
                emptyList(),
                listOf(object : TypeReference<Utf8String>() {})
            )

            val encodedFunction = FunctionEncoder.encode(function)
            val ethCall = web3j.ethCall(
                Transaction.createEthCallTransaction(contractAddress, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).sendAsync()

            ethCall.thenApply { result ->
                val decodedValues = org.web3j.abi.FunctionReturnDecoder.decode(result.value, function.outputParameters)
                if (decodedValues.isNotEmpty()) {
                    val contractName = (decodedValues[0] as Utf8String).value
                    contractName.isNotEmpty()
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CompletableFuture.completedFuture(false) // Return false on error
        }
    }
}
