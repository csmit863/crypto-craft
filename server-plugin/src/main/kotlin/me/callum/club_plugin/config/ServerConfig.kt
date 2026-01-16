package me.callum.club_plugin.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.callum.club_plugin.economy.AssetFactory
import me.callum.club_plugin.economy.Blockcoin
import me.callum.club_plugin.economy.Uniswap
import java.io.File

data class ServerConfigData(
    var rpcUrl: String = "",
    var blockcoinAddress: String = "",
    var assetFactoryAddress: String = "",
    var uniswapFactoryAddress: String = "",
    var uniswapRouterAddress: String = ""
)

object ServerConfig {

    private lateinit var file: File
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private var data: ServerConfigData = ServerConfigData()

    fun init(dataFolder: File) {
        file = File(dataFolder, "serverconfig.json")

        if (!file.exists()) {
            save()
        } else {
            load()
        }
    }

    private fun load() {
        data = gson.fromJson(file.readText(), ServerConfigData::class.java)
    }

    fun save() {
        file.writeText(gson.toJson(data))
    }

    /* ===== READ-ONLY ACCESSORS ===== */

    fun rpcUrl(): String = data.rpcUrl
    fun blockcoin(): String = data.blockcoinAddress
    fun assetFactory(): String = data.assetFactoryAddress
    fun uniswapFactory(): String = data.uniswapFactoryAddress
    fun uniswapRouter(): String = data.uniswapRouterAddress

    /* ===== MUTATORS (ADMIN ONLY) ===== */

    fun setRpcUrl(url: String) {
        data.rpcUrl = url
        save()
    }

    fun setBlockcoin(address: String) {
        data.blockcoinAddress = address
        save()
        Blockcoin.updateAddress(address)
    }


    fun setAssetFactory(address: String) {
        data.assetFactoryAddress = address
        save()
        AssetFactory.updateFactoryAddress(address)
    }


    fun setUniswapFactory(address: String) {
        data.uniswapFactoryAddress = address
        save()
        Uniswap.updateFactoryAddress(address)
    }

    fun setUniswapRouter(address: String) {
        data.uniswapRouterAddress = address
        save()
        Uniswap.updateRouterAddress(address)
    }


    fun isComplete(): Boolean {
        return data.rpcUrl.isNotBlank()
                && data.blockcoinAddress.isNotBlank()
                && data.assetFactoryAddress.isNotBlank()
                && data.uniswapFactoryAddress.isNotBlank()
                && data.uniswapRouterAddress.isNotBlank()
    }

}
