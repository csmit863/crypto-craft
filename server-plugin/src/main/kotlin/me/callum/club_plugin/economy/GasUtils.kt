package me.callum.club_plugin.economy

import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.request.Transaction
import java.math.BigDecimal
import java.math.BigInteger

object GasUtils {

    fun estimateGas(
        web3: Web3j,
        tx: org.web3j.protocol.core.methods.request.Transaction,
        multiplier: BigDecimal = BigDecimal("1.2")
    ): Pair<BigInteger, BigInteger> {

        val gasPrice = web3.ethGasPrice().send().gasPrice

        val estimate = web3.ethEstimateGas(tx).send()

        val raw = try {
            estimate.amountUsed
        } catch (e: Exception) {
            null
        }

        requireNotNull(raw) {
            "Gas estimate failed: ${estimate.error?.message ?: "no amountUsed returned"}"
        }

        val gasLimit = raw.toBigDecimal()
            .multiply(multiplier)
            .toBigInteger()

        return gasPrice to gasLimit
    }
}