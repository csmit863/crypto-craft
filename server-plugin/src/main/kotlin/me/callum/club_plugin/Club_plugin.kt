package me.callum.club_plugin
import me.callum.club_plugin.commands.*
import me.callum.club_plugin.commands.admin.SetFactoryCommand
import me.callum.club_plugin.commands.admin.SetTokenCommand
import me.callum.club_plugin.commands.admin.SetWeb3Command
import me.callum.club_plugin.economy.Blockcoin
import org.bukkit.plugin.java.JavaPlugin
import me.callum.club_plugin.economy.WalletManager

class Club_plugin : JavaPlugin() {

    private lateinit var blockcoin: Blockcoin
    private lateinit var walletManager: WalletManager  // Store the instance

    override fun onEnable() {
        // Plugin startup logic
        logger.info("hello");

        blockcoin = Blockcoin()
        walletManager = WalletManager(blockcoin)

        registerCommands();
        registerEvents();
    }

    private fun registerCommands() {
        // economy commands
        getCommand("balance")?.setExecutor(Bal(walletManager));
        getCommand("bal")?.setExecutor(Bal(walletManager));
        getCommand("send")?.setExecutor(SendTokensCommand(walletManager))

        getCommand("sell")?.setExecutor(SellItemsCommand(walletManager))
        getCommand("sell")?.tabCompleter = SellItemsCommand(walletManager)
        getCommand("buy")?.setExecutor(BuyItemsCommand(walletManager))

        // admin commands
        getCommand("setTokenAddress")?.setExecutor(SetTokenCommand(blockcoin))
        getCommand("setWeb3")?.setExecutor(SetWeb3Command(blockcoin))
        getCommand("setFactory")?.setExecutor(SetFactoryCommand(blockcoin))
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(walletManager, this)  // Register WalletManager
    }

    override fun onDisable() {
        // Plugin shutdown logic
        logger.info("goodbye");
    }
}
