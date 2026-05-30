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
import me.callum.club_plugin.config.ServerConfig
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
    val base: Int = 10,
    var expanded: Long = 0
)

data class AdminConfig(
    val rpcUrl: String,
    val privateKey: String
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

        // load admin configuration
        val adminConfigFile = File(dataFolder, "adminConfiguration.json")
        if (!adminConfigFile.exists()) {
            logger.severe("adminConfiguration.json not found in ${dataFolder.path} — cannot start plugin.")
            server.pluginManager.disablePlugin(this)
            return
        }
        val adminConfig = gson.fromJson(adminConfigFile.readText(), AdminConfig::class.java)
        logger.info("Loaded adminConfiguration.json (rpcUrl: ${adminConfig.rpcUrl})")

        // load deployment data once
        val mainDeployment = loadDeploymentData("/me/callum/club_plugin/assets/deployments.json")
        val uniswapDeployment = loadDeploymentData("/me/callum/club_plugin/assets/uniswap_deployments.json")

        // set server config
        ServerConfig.init(dataFolder)

        ServerConfig.setRpcUrl(adminConfig.rpcUrl)
        ServerConfig.setBlockcoin(mainDeployment.logs[0])
        ServerConfig.setAssetFactory(mainDeployment.logs[1])
        ServerConfig.setUniswapFactory(uniswapDeployment.logs[0])
        ServerConfig.setUniswapRouter(uniswapDeployment.logs[1])


        // initialise web3 from admin config
        val adminSigner = Credentials.create(adminConfig.privateKey)
        val web3j: Web3j = Web3j.build(HttpService(adminConfig.rpcUrl))
        val adminTxManager = RawTransactionManager(web3j, adminSigner)

        val blockCoinAddress = mainDeployment.logs.getOrNull(0)
            ?: throw IllegalStateException("Missing BlockCoin address")
        val assetFactoryAddress = mainDeployment.logs.getOrNull(1)
            ?: throw IllegalStateException("Missing AssetFactory address")
        val uniswapFactoryAddress = uniswapDeployment.logs.getOrNull(0)
            ?: throw IllegalStateException("Missing Uniswap Factory address")
        val uniswapRouterAddress = uniswapDeployment.logs.getOrNull(1)
            ?: throw IllegalStateException("Missing Uniswap Router address")

        assetFactory = AssetFactory.initialize(assetFactoryAddress, web3j, adminTxManager)
        blockcoin = Blockcoin.initialize(blockCoinAddress, web3j, adminTxManager)
        walletManager = WalletManager.initialize(blockcoin, web3j, adminTxManager)
        uniswap = Uniswap.initialize(uniswapFactoryAddress, uniswapRouterAddress, web3j, adminTxManager)

        // sync assets from chain in background

        logger.info("Syncing assets from chain...")
        AssetFactory.syncFromChain()
        logger.info("Sync complete, registering commands...")

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
        getCommand("price")?.setExecutor(CheckPriceCommand(this@CryptoCraft))
        getCommand("balance")?.setExecutor(Bal(walletManager))
        getCommand("bal")?.setExecutor(Bal(walletManager))
        getCommand("send")?.setExecutor(SendTokensCommand(this@CryptoCraft, walletManager))
        getCommand("sell")?.apply {
            val cmd = SellItemsCommand(this@CryptoCraft, walletManager)
            setExecutor(cmd)
            tabCompleter = cmd
        }
        getCommand("buy")?.setExecutor(BuyItemsCommand(this@CryptoCraft, walletManager))
        getCommand("liquidity")?.setExecutor(LiquidityCommand())
        getCommand("expand")?.setExecutor(Expand(this, walletManager))


        // admin commands
        getCommand("setBlockcoinAddress")?.setExecutor(SetBlockcoinCommand())
        getCommand("setWeb3")?.setExecutor(SetWeb3Command())
        getCommand("setFactory")?.setExecutor(SetFactoryCommand())
        getCommand("setRouter")?.setExecutor(SetRouterCommand())
        getCommand("setAssetFactory")?.setExecutor(SetAssetFactoryCommand())
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
