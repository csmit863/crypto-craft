package me.callum.club_plugin.economy

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.tx.RawTransactionManager
import org.web3j.protocol.core.methods.request.Transaction
import java.math.BigInteger

object Bank {
    lateinit var bankAddress: String
    private lateinit var web3: Web3j

    fun initialize(bankAddress: String, web3: Web3j): Bank {
        this.bankAddress = bankAddress
        this.web3 = web3
        return this
    }

    fun deposit(
        assetAddress: String,
        assetAmount: BigInteger,
        blockCoinAmount: BigInteger,
        playerTxManager: RawTransactionManager
    ): String? {
        val function = Function(
            "deposit",
            listOf(
                Address(assetAddress),
                Uint256(assetAmount),
                Uint256(blockCoinAmount)
            ),
            emptyList()
        )
        return sendTx(function, playerTxManager, "Bank.deposit")
    }

    fun withdraw(playerTxManager: RawTransactionManager): String? {
        val function = Function("withdraw", emptyList(), emptyList())
        return sendTx(function, playerTxManager, "Bank.withdraw")
    }

    fun withdrawAsset(
        assetAddress: String,
        playerTxManager: RawTransactionManager
    ): String? {
        val function = Function(
            "withdrawAsset",
            listOf(
                Address(playerTxManager.fromAddress),
                Address(assetAddress)
            ),
            emptyList()
        )
        return sendTx(function, playerTxManager, "Bank.withdrawAsset")
    }

    fun getPosition(
        playerAddress: String,
        assetAddress: String
    ): Triple<BigInteger, BigInteger, BigInteger>? {
        val function = Function(
            "getPosition",
            listOf(Address(playerAddress), Address(assetAddress)),
            listOf(
                object : TypeReference<Uint256>() {},
                object : TypeReference<Uint256>() {},
                object : TypeReference<Uint256>() {}
            )
        )

        return try {
            val response = web3.ethCall(
                Transaction.createEthCallTransaction(
                    playerAddress,
                    bankAddress,
                    FunctionEncoder.encode(function)
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()

            val decoded = FunctionReturnDecoder.decode(response.value, function.outputParameters)
            Triple(
                (decoded[0] as Uint256).value,
                (decoded[1] as Uint256).value,
                (decoded[2] as Uint256).value
            )
        } catch (e: Exception) {
            println("❌ Bank.getPosition failed: ${e.message}")
            null
        }
    }

    fun getPlayerAssets(playerAddress: String): List<String>? {
        val function = Function(
            "getPlayerAssets",
            listOf(Address(playerAddress)),
            listOf(object : TypeReference<org.web3j.abi.datatypes.DynamicArray<Address>>() {})
        )

        return try {
            val response = web3.ethCall(
                Transaction.createEthCallTransaction(
                    playerAddress,
                    bankAddress,
                    FunctionEncoder.encode(function)
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()

            val decoded = FunctionReturnDecoder.decode(response.value, function.outputParameters)
            @Suppress("UNCHECKED_CAST")
            (decoded[0].value as List<Address>).map { it.value }
        } catch (e: Exception) {
            println("❌ Bank.getPlayerAssets failed: ${e.message}")
            null
        }
    }

    private fun sendTx(
        function: Function,
        txManager: RawTransactionManager,
        label: String
    ): String? {
        val encodedFunction = FunctionEncoder.encode(function)

        return try {
            val estimateTx = Transaction.createFunctionCallTransaction(
                txManager.fromAddress,
                null, null, null,
                bankAddress,
                encodedFunction
            )

            val (gasPrice, gasLimit) = GasUtils.estimateGas(web3, estimateTx)

            val tx = txManager.sendTransaction(
                gasPrice, gasLimit, bankAddress, encodedFunction, BigInteger.ZERO
            )

            val txHash = requireNotNull(tx.transactionHash) { "TX hash null" }
            println("✅ $label sent: $txHash")

            val receipt = AssetFactory.waitForReceipt(txHash)
            require(receipt != null && receipt.isStatusOK) { "$label reverted" }

            println("✅ $label successful")
            txHash

        } catch (e: Exception) {
            println("❌ $label failed:")
            e.printStackTrace()
            null
        }
    }
}