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
import me.callum.club_plugin.commands.player.Expand
import me.callum.club_plugin.commands.player.LiquidityCommand
import me.callum.club_plugin.economy.AssetFactory
import me.callum.club_plugin.economy.Uniswap
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import java.io.File

data class DeploymentLog(
    val logs: List<String>,
    val returns: Map<String, Any> = emptyMap(), // new field to match new JSON format
    val success: Boolean
)

data class WorldBorderState(
    val base: Int = 200,
    var expanded: Long = 0
)



class CryptoCraft : JavaPlugin() {

    private lateinit var blockcoin: Blockcoin
    private lateinit var walletManager: WalletManager
    private lateinit var assetFactory: AssetFactory
    private lateinit var uniswap: Uniswap

    private lateinit var worldBorderFile: File
    private lateinit var worldBorderState: WorldBorderState
    private val gson = Gson()


    override fun onEnable() {

        // setup worldborder
        worldBorderFile = File(dataFolder, "worldborder.json")
        if (!worldBorderFile.exists()) {
            dataFolder.mkdirs()
            worldBorderState = WorldBorderState()
            worldBorderFile.writeText(gson.toJson(worldBorderState))
            logger.info("Created worldborder.json with default values")
        } else {
            worldBorderState = gson.fromJson(
                worldBorderFile.readText(),
                WorldBorderState::class.java
            )
            logger.info("Loaded worldborder.json")
        }

        applyWorldBorder()

        // centralised management of key variables
        val adminSigner = Credentials.create("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80") // TODO: retrieve from .env or config.json, not hardcode
        val rpcUrl: String = "https://testnet.qutblockchain.club"
        val web3j: Web3j = Web3j.build(HttpService(rpcUrl))
        val adminTxManager = RawTransactionManager(web3j, adminSigner)

        // ✅ Load contract deployment logs
        val mainDeployment = loadDeploymentData("/me/callum/club_plugin/assets/deployments.json")
        val uniswapDeployment = loadDeploymentData("/me/callum/club_plugin/assets/uniswap_deployments.json")

        val blockCoinAddress = mainDeployment.logs.getOrNull(0)
            ?: throw IllegalStateException("Missing BlockCoin address")
        val assetFactoryAddress = mainDeployment.logs.getOrNull(1)
            ?: throw IllegalStateException("Missing AssetFactory address")
        val uniswapFactoryAddress = uniswapDeployment.logs.getOrNull(0)
            ?: throw IllegalStateException("Missing Uniswap Factory address")
        val uniswapRouterAddress = uniswapDeployment.logs.getOrNull(1)
            ?: throw IllegalStateException("Missing Uniswap Router address")

        // TO IMPLEMENT:
        assetFactory = AssetFactory.initialize(assetFactoryAddress, web3j, adminTxManager)
        blockcoin = Blockcoin.initialize(blockCoinAddress, web3j, adminTxManager)
        walletManager = WalletManager.initialize(blockcoin, web3j, adminTxManager)
        uniswap = Uniswap.initialize(uniswapFactoryAddress, uniswapRouterAddress, web3j, adminTxManager)

        logger.info("BlockCoin at $blockCoinAddress, AssetFactory at $assetFactoryAddress")
        logger.info("Uniswap Factory at $uniswapFactoryAddress, Router at $uniswapRouterAddress")

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

    private fun applyWorldBorder() {
        val world = server.worlds.first() // or explicitly "world"
        val radius = worldBorderState.base + worldBorderState.expanded
        world.worldBorder.size = radius * 2.0
        logger.info("World border set to radius $radius")
    }

    fun saveWorldBorder() {
        worldBorderFile.writeText(gson.toJson(worldBorderState))
    }


    private fun registerCommands() {
        // economy commands
        getCommand("price")?.setExecutor(CheckPriceCommand())
        getCommand("balance")?.setExecutor(Bal(walletManager))
        getCommand("bal")?.setExecutor(Bal(walletManager))
        getCommand("send")?.setExecutor(SendTokensCommand(walletManager))
        getCommand("sell")?.apply {
            val cmd = SellItemsCommand(walletManager)
            setExecutor(cmd)
            tabCompleter = cmd
        }
        getCommand("buy")?.setExecutor(BuyItemsCommand(walletManager))
        getCommand("liquidity")?.setExecutor(LiquidityCommand())
        getCommand("expand")?.setExecutor(Expand(this, walletManager))


        // admin commands
        //getCommand("setTokenAddress")?.setExecutor(SetTokenCommand(blockcoin))
        //getCommand("setWeb3")?.setExecutor(SetWeb3Command(blockcoin))
        //getCommand("setFactory")?.setExecutor(SetFactoryCommand(blockcoin))
        getCommand("getConfig")?.setExecutor(GetConfigCommand())
        getCommand("getAssets")?.setExecutor(GetAssetsCommand())
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(walletManager, this)
    }

    override fun onDisable() {
        logger.info("goodbye")
    }
}
