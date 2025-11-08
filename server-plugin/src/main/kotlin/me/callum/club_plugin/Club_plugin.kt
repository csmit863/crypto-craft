package me.callum.club_plugin

import me.callum.club_plugin.commands.*
import me.callum.club_plugin.commands.admin.*
import me.callum.club_plugin.economy.BlockcoinManager
import me.callum.club_plugin.economy.WalletManager
import org.bukkit.plugin.java.JavaPlugin
import com.google.gson.Gson

data class DeploymentLog(
    val logs: List<String>,
    val returns: Map<String, Any> = emptyMap(), // new field to match new JSON format
    val success: Boolean
)


class Club_plugin : JavaPlugin() {

    private lateinit var blockcoin: BlockcoinManager
    private lateinit var walletManager: WalletManager

    override fun onEnable() {
        // ✅ Load deployments.json from the JAR (src/main/resources or assets folder)
        val deployment = loadDeploymentData()
        val blockCoinAddress = deployment.logs.getOrNull(0)
            ?: throw IllegalStateException("Missing BlockCoin address in deployments.json")
        val assetFactoryAddress = deployment.logs.getOrNull(1)
            ?: throw IllegalStateException("Missing AssetFactory address in deployments.json")

        logger.info("Loaded BlockCoin at: $blockCoinAddress")
        logger.info("Loaded AssetFactory at: $assetFactoryAddress")

        blockcoin = BlockcoinManager(blockCoinAddress, assetFactoryAddress)
        walletManager = WalletManager(blockcoin)

        logger.info("Server configured with Blockcoin at $blockCoinAddress and AssetFactory at $assetFactoryAddress")

        registerCommands()
        registerEvents()
    }

    private fun loadDeploymentData(): DeploymentLog {
        val resourcePath = "/me/callum/club_plugin/assets/deployments.json"
        val stream = this::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("deployments.json not found in plugin resources")

        val json = stream.bufferedReader().use { it.readText() }

        // Parse JSON as a list
        val listType = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, DeploymentLog::class.java).type
        val deployments: List<DeploymentLog> = Gson().fromJson(json, listType)

        // Use the first deployment in the list (or handle multiple if needed)
        return deployments.firstOrNull()
            ?: throw IllegalStateException("No deployments found in deployments.json")
    }


    private fun registerCommands() {
        // economy commands
        getCommand("balance")?.setExecutor(Bal(walletManager))
        getCommand("bal")?.setExecutor(Bal(walletManager))
        getCommand("send")?.setExecutor(SendTokensCommand(walletManager))
        getCommand("sell")?.apply {
            val cmd = SellItemsCommand(walletManager)
            setExecutor(cmd)
            tabCompleter = cmd
        }
        getCommand("buy")?.setExecutor(BuyItemsCommand(walletManager))

        // admin commands
        getCommand("setTokenAddress")?.setExecutor(SetTokenCommand(blockcoin))
        getCommand("setWeb3")?.setExecutor(SetWeb3Command(blockcoin))
        getCommand("setFactory")?.setExecutor(SetFactoryCommand(blockcoin))
        getCommand("getConfig")?.setExecutor(GetConfigCommand(blockcoin))
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(walletManager, this)
    }

    override fun onDisable() {
        logger.info("goodbye")
    }
}
