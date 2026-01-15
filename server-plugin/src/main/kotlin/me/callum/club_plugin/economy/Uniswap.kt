package me.callum.club_plugin.economy

import me.callum.club_plugin.economy.AssetFactory.waitForReceipt
import org.bukkit.Bukkit
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger
import java.util.concurrent.CompletableFuture

// class to manage swaps, liquidity with uniswap instance
// should implement:
// - function to create new pairs
// - function to swap tokens
// - function to retrieve price / exchange rate between tokens
// - functions to manage liquidity of tokens (potentially as admin)

// HELPFUL INTERFACES
/**
 * interface IUniswapV2Factory {
 *     event PairCreated(address indexed token0, address indexed token1, address pair, uint256);
 *
 *     function feeTo() external view returns (address);
 *     function feeToSetter() external view returns (address);
 *
 *     function getPair(address tokenA, address tokenB) external view returns (address pair);
 *     function allPairs(uint256) external view returns (address pair);
 *     function allPairsLength() external view returns (uint256);
 *
 *     function createPair(address tokenA, address tokenB) external returns (address pair);
 *
 *     function setFeeTo(address) external;
 *     function setFeeToSetter(address) external;
 * }
 **/

 /** interface IUniswapV2Router01 {
 *     function factory() external pure returns (address);
 *     function WETH() external pure returns (address);
 *
 *     function addLiquidity(
 *         address tokenA,
 *         address tokenB,
 *         uint256 amountADesired,
 *         uint256 amountBDesired,
 *         uint256 amountAMin,
 *         uint256 amountBMin,
 *         address to,
 *         uint256 deadline
 *     ) external returns (uint256 amountA, uint256 amountB, uint256 liquidity);
 *     function addLiquidityETH(
 *         address token,
 *         uint256 amountTokenDesired,
 *         uint256 amountTokenMin,
 *         uint256 amountETHMin,
 *         address to,
 *         uint256 deadline
 *     ) external payable returns (uint256 amountToken, uint256 amountETH, uint256 liquidity);
 *     function removeLiquidity(
 *         address tokenA,
 *         address tokenB,
 *         uint256 liquidity,
 *         uint256 amountAMin,
 *         uint256 amountBMin,
 *         address to,
 *         uint256 deadline
 *     ) external returns (uint256 amountA, uint256 amountB);
 *     function removeLiquidityETH(
 *         address token,
 *         uint256 liquidity,
 *         uint256 amountTokenMin,
 *         uint256 amountETHMin,
 *         address to,
 *         uint256 deadline
 *     ) external returns (uint256 amountToken, uint256 amountETH);
 *     function removeLiquidityWithPermit(
 *         address tokenA,
 *         address tokenB,
 *         uint256 liquidity,
 *         uint256 amountAMin,
 *         uint256 amountBMin,
 *         address to,
 *         uint256 deadline,
 *         bool approveMax,
 *         uint8 v,
 *         bytes32 r,
 *         bytes32 s
 *     ) external returns (uint256 amountA, uint256 amountB);
 *     function removeLiquidityETHWithPermit(
 *         address token,
 *         uint256 liquidity,
 *         uint256 amountTokenMin,
 *         uint256 amountETHMin,
 *         address to,
 *         uint256 deadline,
 *         bool approveMax,
 *         uint8 v,
 *         bytes32 r,
 *         bytes32 s
 *     ) external returns (uint256 amountToken, uint256 amountETH);
 *     function swapExactTokensForTokens(
 *         uint256 amountIn,
 *         uint256 amountOutMin,
 *         address[] calldata path,
 *         address to,
 *         uint256 deadline
 *     ) external returns (uint256[] memory amounts);
 *     function swapTokensForExactTokens(
 *         uint256 amountOut,
 *         uint256 amountInMax,
 *         address[] calldata path,
 *         address to,
 *         uint256 deadline
 *     ) external returns (uint256[] memory amounts);
 *     function swapExactETHForTokens(uint256 amountOutMin, address[] calldata path, address to, uint256 deadline)
 *         external
 *         payable
 *         returns (uint256[] memory amounts);
 *     function swapTokensForExactETH(
 *         uint256 amountOut,
 *         uint256 amountInMax,
 *         address[] calldata path,
 *         address to,
 *         uint256 deadline
 *     ) external returns (uint256[] memory amounts);
 *     function swapExactTokensForETH(
 *         uint256 amountIn,
 *         uint256 amountOutMin,
 *         address[] calldata path,
 *         address to,
 *         uint256 deadline
 *     ) external returns (uint256[] memory amounts);
 *     function swapETHForExactTokens(uint256 amountOut, address[] calldata path, address to, uint256 deadline)
 *         external
 *         payable
 *         returns (uint256[] memory amounts);
 *
 *     function quote(uint256 amountA, uint256 reserveA, uint256 reserveB) external pure returns (uint256 amountB);
 *     function getAmountOut(uint256 amountIn, uint256 reserveIn, uint256 reserveOut)
 *         external
 *         pure
 *         returns (uint256 amountOut);
 *     function getAmountIn(uint256 amountOut, uint256 reserveIn, uint256 reserveOut)
 *         external
 *         pure
 *         returns (uint256 amountIn);
 *     function getAmountsOut(uint256 amountIn, address[] calldata path) external view returns (uint256[] memory amounts);
 *     function getAmountsIn(uint256 amountOut, address[] calldata path) external view returns (uint256[] memory amounts);
 * }
 **/
 /**
 * interface IUniswapV2Router02 is IUniswapV2Router01 {
 *     function removeLiquidityETHSupportingFeeOnTransferTokens(
 *         address token,
 *         uint256 liquidity,
 *         uint256 amountTokenMin,
 *         uint256 amountETHMin,
 *         address to,
 *         uint256 deadline
 *     ) external returns (uint256 amountETH);
 *     function removeLiquidityETHWithPermitSupportingFeeOnTransferTokens(
 *         address token,
 *         uint256 liquidity,
 *         uint256 amountTokenMin,
 *         uint256 amountETHMin,
 *         address to,
 *         uint256 deadline,
 *         bool approveMax,
 *         uint8 v,
 *         bytes32 r,
 *         bytes32 s
 *     ) external returns (uint256 amountETH);
 *
 *     function swapExactTokensForTokensSupportingFeeOnTransferTokens(
 *         uint256 amountIn,
 *         uint256 amountOutMin,
 *         address[] calldata path,
 *         address to,
 *         uint256 deadline
 *     ) external;
 *     function swapExactETHForTokensSupportingFeeOnTransferTokens(
 *         uint256 amountOutMin,
 *         address[] calldata path,
 *         address to,
 *         uint256 deadline
 *     ) external payable;
 *     function swapExactTokensForETHSupportingFeeOnTransferTokens(
 *         uint256 amountIn,
 *         uint256 amountOutMin,
 *         address[] calldata path,
 *         address to,
 *         uint256 deadline
 *     ) external;
 * }
 *
 **/


interface IUniswap {

}


object Uniswap: IUniswap {
    public lateinit var v2factoryAddress: String
    public lateinit var v2routerAddress: String
    private lateinit var web3: Web3j
    private lateinit var txManager: RawTransactionManager

    private val gasProvider = DefaultGasProvider()
    fun initialize(
         v2factoryAddress: String,
         v2routerAddress: String,
         web3: Web3j,
         txManager: RawTransactionManager): Uniswap{
         this.v2factoryAddress = v2factoryAddress;
         this.v2routerAddress = v2routerAddress;
         this.web3 = web3;
         this.txManager = txManager
         return this
    }

    fun getAllPairsLength(): CompletableFuture<BigInteger> {
        val function = Function(
            "allPairsLength",
            emptyList(),
            listOf(object : TypeReference<Uint256>() {})
        )

        val encoded = FunctionEncoder.encode(function)

        return web3.ethCall(
            Transaction.createEthCallTransaction(
                v2factoryAddress,
                v2factoryAddress,
                encoded
            ),
            DefaultBlockParameterName.LATEST
        ).sendAsync().thenApply { response ->
            val decoded = org.web3j.abi.FunctionReturnDecoder.decode(
                response.value,
                function.outputParameters
            )
            (decoded[0] as Uint256).value
        }
    }

    fun getPairAtIndex(index: BigInteger): CompletableFuture<String> {
        val function = Function(
            "allPairs",
            listOf(Uint256(index)),
            listOf(object : TypeReference<Address>() {})
        )

        val encoded = FunctionEncoder.encode(function)

        return web3.ethCall(
            Transaction.createEthCallTransaction(
                v2factoryAddress,
                v2factoryAddress,
                encoded
            ),
            DefaultBlockParameterName.LATEST
        ).sendAsync().thenApply { response ->
            val decoded = org.web3j.abi.FunctionReturnDecoder.decode(
                response.value,
                function.outputParameters
            )
            (decoded[0] as Address).value
        }
    }

    fun getAllPairs(): CompletableFuture<List<String>> {
        return getAllPairsLength().thenCompose { length ->
            val futures = mutableListOf<CompletableFuture<String>>()

            var i = BigInteger.ZERO
            while (i < length) {
                futures.add(getPairAtIndex(i))
                i += BigInteger.ONE
            }

            CompletableFuture.allOf(*futures.toTypedArray())
                .thenApply {
                    futures.map { it.join() }
                }
        }
    }






    public fun getPair(tokenA: String, tokenB: String): CompletableFuture<String> {
        return try {
            // Define the function to call the getPair method
            val function = Function(
                "getPair",
                listOf(Address(tokenA), Address(tokenB)),
                listOf(object : TypeReference<Address>() {})
            )

            // Encode the function call
            val encodedFunction = FunctionEncoder.encode(function)

            // Create and execute the eth call
            val ethCall = web3.ethCall(
                Transaction.createEthCallTransaction(v2factoryAddress, v2factoryAddress, encodedFunction), // Contract address and encoded function
                DefaultBlockParameterName.LATEST
            ).sendAsync()

            ethCall.thenApply { result ->
                // Decode the result to get the pair address
                val decodedValues = org.web3j.abi.FunctionReturnDecoder.decode(result.value, function.outputParameters)
                if (decodedValues.isNotEmpty()) {
                    (decodedValues[0] as Address).value // Return the pair address
                } else {
                    "0xnull" // Return null if address not found
                }
            }.exceptionally { ex ->
                ex.printStackTrace()
                "0xnull" // Return null on error
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CompletableFuture.completedFuture("0xnull") // Return null on error
        }
    }

    fun getToken0(pairAddress: String): CompletableFuture<String> {
        val function = Function(
            "token0",
            emptyList(),
            listOf(object : TypeReference<Address>() {})
        )

        val encoded = FunctionEncoder.encode(function)

        return web3.ethCall(
            Transaction.createEthCallTransaction(pairAddress, pairAddress, encoded),
            DefaultBlockParameterName.LATEST
        ).sendAsync().thenApply { response ->
            val decoded = org.web3j.abi.FunctionReturnDecoder.decode(response.value, function.outputParameters)
            if (decoded.isNotEmpty()) (decoded[0] as Address).value else "0x0000000000000000000000000000000000000000"
        }.exceptionally { ex ->
            ex.printStackTrace()
            "0x0000000000000000000000000000000000000000"
        }
    }


    public fun getReserves(pairAddress: String): CompletableFuture<Pair<BigInteger, BigInteger>> {
        return try {
            // Define the function to call the reserves method
            val function = Function(
                "getReserves",
                emptyList(), // No input parameters for getReserves
                listOf(object : TypeReference<Uint256>() {}, object : TypeReference<Uint256>() {}, object : TypeReference<Uint256>() {}) // Output parameters for reserves
            )

            // Encode the function call
            val encodedFunction = FunctionEncoder.encode(function)

            // Create and execute the eth call
            val ethCall = web3.ethCall(
                Transaction.createEthCallTransaction(pairAddress, pairAddress, encodedFunction), // Use the pairAddress
                DefaultBlockParameterName.LATEST
            ).sendAsync()

            ethCall.thenApply { result ->
                // Decode the result to get the reserves
                val decodedValues = org.web3j.abi.FunctionReturnDecoder.decode(result.value, function.outputParameters)
                if (decodedValues.size >= 2) {
                    Pair((decodedValues[0] as Uint256).value, (decodedValues[1] as Uint256).value) // Return the reserves as a Pair
                } else {
                    Pair(BigInteger.ZERO, BigInteger.ZERO) // Return zero reserves if not found
                }
            }.exceptionally { ex ->
                ex.printStackTrace()
                Pair(BigInteger.ZERO, BigInteger.ZERO) // Return zero on error
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CompletableFuture.completedFuture(Pair(BigInteger.ZERO, BigInteger.ZERO)) // Return zero on error
        }
    }

    fun getAmountsOut(
        amountIn: BigInteger,
        path: List<String>
    ): CompletableFuture<List<BigInteger>> {

        val function = Function(
            "getAmountsOut",
            listOf(
                Uint256(amountIn),
                org.web3j.abi.datatypes.DynamicArray(
                    Address::class.java,
                    path.map { Address(it) }
                )
            ),
            listOf(object : TypeReference<org.web3j.abi.datatypes.DynamicArray<Uint256>>() {})
        )

        val encodedFunction = FunctionEncoder.encode(function)

        return web3.ethCall(
            Transaction.createEthCallTransaction(
                null,
                v2routerAddress,
                encodedFunction
            ),
            DefaultBlockParameterName.LATEST
        ).sendAsync().thenApply { response ->

            val decoded = org.web3j.abi.FunctionReturnDecoder.decode(
                response.value,
                function.outputParameters
            )

            if (decoded.isEmpty()) {
                emptyList()
            } else {
                @Suppress("UNCHECKED_CAST")
                val amounts = decoded[0] as org.web3j.abi.datatypes.DynamicArray<Uint256>
                amounts.value.map { it.value }
            }
        }
    }

    fun getAmountsIn(
        amountOut: BigInteger,
        path: List<String>
    ): CompletableFuture<List<BigInteger>> {

        val function = Function(
            "getAmountsIn",
            listOf(
                Uint256(amountOut),
                org.web3j.abi.datatypes.DynamicArray(
                    Address::class.java,
                    path.map { Address(it) }
                )
            ),
            listOf(
                object : TypeReference<
                        org.web3j.abi.datatypes.DynamicArray<Uint256>
                        >() {}
            )
        )

        val encodedFunction = FunctionEncoder.encode(function)

        return web3.ethCall(
            Transaction.createEthCallTransaction(
                null,
                v2routerAddress,
                encodedFunction
            ),
            DefaultBlockParameterName.LATEST
        ).sendAsync().thenApply { response ->

            val decoded = org.web3j.abi.FunctionReturnDecoder.decode(
                response.value,
                function.outputParameters
            )

            if (decoded.isEmpty()) {
                emptyList()
            } else {
                @Suppress("UNCHECKED_CAST")
                val amounts =
                    decoded[0] as org.web3j.abi.datatypes.DynamicArray<Uint256>

                amounts.value.map { it.value }
            }
        }
    }



    fun addLiquidity(
        tokenA: String,
        tokenB: String,
        amountADesired: BigInteger,
        amountBDesired: BigInteger,
        amountAMin: BigInteger,
        amountBMin: BigInteger,
        to: Address,
        deadline: Uint256,
        senderTxManager: RawTransactionManager
    ): TransactionReceipt {

        val function = Function(
            "addLiquidity",
            listOf(
                Address(tokenA),
                Address(tokenB),
                Uint256(amountADesired),
                Uint256(amountBDesired),
                Uint256(amountAMin),
                Uint256(amountBMin),
                to,
                deadline
            ),
            listOf(
                object : TypeReference<Uint256>() {},
                object : TypeReference<Uint256>() {},
                object : TypeReference<Uint256>() {}
            )
        )

        val encoded = FunctionEncoder.encode(function)

        val tx = senderTxManager.sendTransaction(
            gasProvider.gasPrice,
            gasProvider.getGasLimit("addLiquidity"),
            v2routerAddress,
            encoded,
            BigInteger.ZERO
        )

        require(tx.transactionHash != null) { "addLiquidity tx hash is null" }

        val receipt = waitForReceipt(tx.transactionHash)
            ?: error("No receipt for addLiquidity")

        require(receipt.status == "0x1") {
            "addLiquidity reverted: ${receipt.transactionHash}"
        }
        Bukkit.getLogger().info("addLiquidity tx sent: ${tx.transactionHash}")
        return receipt
    }


    fun removeLiquidity(){}

    fun swapTokens(){}

    public fun createPair(tokenA: String, tokenB: String): String {
         // create pair tx & send to v2Factory (createPair function, see interface)
         println("Creating pair with $tokenA and $tokenB")
         val function = Function(
             "createPair",
             listOf(Address(tokenA), Address(tokenB)),
             emptyList()
         )
         val encodedFunction = FunctionEncoder.encode(function)
         val txHash = txManager.sendTransaction(
             gasProvider.gasPrice,
             gasProvider.getGasLimit("createPair"),
             v2factoryAddress,
             encodedFunction,
             BigInteger.ZERO
         ).transactionHash
         println("Create Pair transaction sent: $txHash")

         val receipt = waitForReceipt(txHash)
         if (receipt == null) {
             println("❌ Failed to get receipt for create pair tx: $txHash")
             return "0xnull"
         }
         return receipt.toString()

    }

    fun swapExactTokensForTokens(
        amountIn: BigInteger,
        amountOutMin: BigInteger,
        path: List<String>,
        to: Address,
        deadline: Uint256,
        senderTxManager: RawTransactionManager
    ): TransactionReceipt {

        val function = Function(
            "swapExactTokensForTokens",
            listOf(
                Uint256(amountIn),
                Uint256(amountOutMin),
                org.web3j.abi.datatypes.DynamicArray(Address::class.java, path.map { Address(it) }),
                to,
                deadline
            ),
            listOf(object : TypeReference<org.web3j.abi.datatypes.generated.Uint256>() {})
        )

        val encoded = FunctionEncoder.encode(function)

        val tx = senderTxManager.sendTransaction(
            gasProvider.gasPrice,
            gasProvider.getGasLimit("swapExactTokensForTokens"),
            v2routerAddress,
            encoded,
            BigInteger.ZERO
        )

        require(tx.transactionHash != null) { "swapExactTokensForTokens tx hash is null" }

        val receipt = waitForReceipt(tx.transactionHash)
            ?: error("No receipt for swapExactTokensForTokens")

        require(receipt.status == "0x1") {
            "swapExactTokensForTokens reverted: ${tx.transactionHash}"
        }
        Bukkit.getLogger().info("swapExactTokensForTokens tx sent: ${tx.transactionHash}")
        return receipt
    }

    fun swapTokensForExactTokens(
        amountOut: BigInteger,
        amountInMax: BigInteger,
        path: List<String>,
        to: Address,
        deadline: Uint256,
        senderTxManager: RawTransactionManager
    ): TransactionReceipt {

        val function = Function(
            "swapTokensForExactTokens",
            listOf(
                Uint256(amountOut),
                Uint256(amountInMax),
                org.web3j.abi.datatypes.DynamicArray(
                    Address::class.java,
                    path.map { Address(it) }
                ),
                to,
                deadline
            ),
            listOf(
                object : TypeReference<org.web3j.abi.datatypes.DynamicArray<Uint256>>() {}
            )
        )

        val encoded = FunctionEncoder.encode(function)

        val tx = senderTxManager.sendTransaction(
            gasProvider.gasPrice,
            gasProvider.getGasLimit("swapTokensForExactTokens"),
            v2routerAddress,
            encoded,
            BigInteger.ZERO
        )

        require(tx.transactionHash != null) {
            "swapTokensForExactTokens tx hash is null"
        }

        val receipt = waitForReceipt(tx.transactionHash)
            ?: error("No receipt for swapTokensForExactTokens")

        require(receipt.status == "0x1") {
            "swapTokensForExactTokens reverted: ${tx.transactionHash}"
        }

        Bukkit.getLogger().info(
            "swapTokensForExactTokens tx sent: ${tx.transactionHash}"
        )

        return receipt
    }



}