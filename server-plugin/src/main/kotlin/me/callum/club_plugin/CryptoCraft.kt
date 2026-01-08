package me.callum.club_plugin

import me.callum.club_plugin.commands.admin.*
import me.callum.club_plugin.economy.Blockcoin
import me.callum.club_plugin.economy.WalletManager
import org.bukkit.plugin.java.JavaPlugin
import com.google.gson.Gson
import me.callum.club_plugin.commands.player.Bal
import me.callum.club_plugin.commands.player.BuyItemsCommand
import me.callum.club_plugin.commands.player.SellItemsCommand
import me.callum.club_plugin.commands.player.SendTokensCommand
import me.callum.club_plugin.commands.player.CheckPriceCommand
import me.callum.club_plugin.economy.AssetFactory
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager

data class DeploymentLog(
    val logs: List<String>,
    val returns: Map<String, Any> = emptyMap(), // new field to match new JSON format
    val success: Boolean
)


class CryptoCraft : JavaPlugin() {

    private lateinit var blockcoin: Blockcoin
    private lateinit var walletManager: WalletManager
    private lateinit var assetFactory: AssetFactory

    private lateinit var wethAddress: String
    private lateinit var uniswapFactoryAddress: String
    private lateinit var uniswapRouterAddress: String

    override fun onEnable() {

        // centralised management of key variables
        val adminSigner = Credentials.create("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80")
        val rpcUrl: String = "https://testnet.qutblockchain.club"
        val web3j: Web3j = Web3j.build(HttpService(rpcUrl))
        val txManager = RawTransactionManager(web3j, adminSigner)

        // ✅ Load main deployments
        val mainDeployment = loadDeploymentData("/me/callum/club_plugin/assets/deployments.json")

        val blockCoinAddress = mainDeployment.logs.getOrNull(0)
            ?: throw IllegalStateException("Missing BlockCoin address")
        val assetFactoryAddress = mainDeployment.logs.getOrNull(1)
            ?: throw IllegalStateException("Missing AssetFactory address")

        // TO IMPLEMENT:
        // uniswap = Uniswap(uniswapAddress) << check what this contract interaction needs to be
        assetFactory = AssetFactory(assetFactoryAddress, web3j, txManager)
        blockcoin = Blockcoin(blockCoinAddress, web3j)

        walletManager = WalletManager(blockcoin, web3j, txManager)

        logger.info("BlockCoin at $blockCoinAddress, AssetFactory at $assetFactoryAddress")

        // ✅ Load Uniswap deployments
        val uniDeployment = loadDeploymentData("/me/callum/club_plugin/assets/uniswap_deployments.json")
        wethAddress = uniDeployment.logs.getOrNull(0)?.substringAfter(": ")
            ?: throw IllegalStateException("Missing WETH address")
        uniswapFactoryAddress = uniDeployment.logs.getOrNull(1)?.substringAfter(": ")
            ?: throw IllegalStateException("Missing Factory address")
        uniswapRouterAddress = uniDeployment.logs.getOrNull(2)?.substringAfter(": ")
            ?: throw IllegalStateException("Missing Router address")

        logger.info("Uniswap WETH at $wethAddress, Factory at $uniswapFactoryAddress, Router at $uniswapRouterAddress")

        registerCommands()
        registerEvents()
    }

    private fun loadDeploymentData(resourcePath: String): DeploymentLog {
        val stream = this::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("$resourcePath not found")

        val json = stream.bufferedReader().use { it.readText() }

        val listType = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, DeploymentLog::class.java).type
        val deployments: List<DeploymentLog> = Gson().fromJson(json, listType)

        return deployments.firstOrNull()
            ?: throw IllegalStateException("No deployments found in $resourcePath")
    }


    private fun registerCommands() {
        // economy commands
        getCommand("price")?.setExecutor(CheckPriceCommand())
        getCommand("balance")?.setExecutor(Bal(walletManager))
        getCommand("bal")?.setExecutor(Bal(walletManager))
        getCommand("send")?.setExecutor(SendTokensCommand(walletManager))
        getCommand("sell")?.apply {
            val cmd = SellItemsCommand(walletManager, assetFactory)
            setExecutor(cmd)
            tabCompleter = cmd
        }
        getCommand("buy")?.setExecutor(BuyItemsCommand(walletManager))

        // admin commands
        //getCommand("setTokenAddress")?.setExecutor(SetTokenCommand(blockcoin))
        //getCommand("setWeb3")?.setExecutor(SetWeb3Command(blockcoin))
        //getCommand("setFactory")?.setExecutor(SetFactoryCommand(blockcoin))
        getCommand("getConfig")?.setExecutor(GetConfigCommand())
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(walletManager, this)
    }

    override fun onDisable() {
        logger.info("goodbye")
    }
}
