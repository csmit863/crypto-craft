# **Blockcoin: in-game fully functional cryptocurrency for Minecraft**

What is this repo:
The smart contracts associated with the cryptocraft system.

What are they?
# Contracts
Blockcoin.sol - the standard currency of the server.
MinecraftAsset.sol - an ERC20 with tokenize and burn functions, only callable by the owner.
AssetFactory.sol - a factory contract for creating MinecraftAssets and recording them for use by the server plugin.

# Scripts
DeployContracts.s.sol - deploy Blockcoin, Assetfactory, new assets, get assets, tests
DistributeToken.s.sol - send tokens to an address