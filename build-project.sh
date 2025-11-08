set -e

source .env

cd web3-infra

# deploy blockcoin and asset factory
forge script script/DeployContracts.s.sol:Setup --private-key ${PRIVATE_KEY} \
  --broadcast --rpc-url ${RPC_URL} --json \
  | jq -s '.' > deployments.json

forge script script/DeployUniswapV2.s.sol:DeployUniswapV2Testnet \
    --rpc-url $RPC_URL \
    --private-key $PRIVATE_KEY \
    --broadcast --json \
    | jq -s '.' > uniswap_deployments.json


# copy the deployment addresses and ABI into the Kotlin project
cd ..
cp web3-infra/deployments.json server-plugin/src/main/resources/me/callum/club_plugin/assets/deployments.json
cp web3-infra/out/BlockCoin.sol/BlockCoin.json server-plugin/src/main/resources/me/callum/club_plugin/assets/blockCoin.json
cp web3-infra/out/AssetFactory.sol/AssetFactory.json server-plugin/src/main/resources/me/callum/club_plugin/assets/assetFactory.json
cp web3-infra/out/MinecraftAsset.sol/MinecraftAsset.json server-plugin/src/main/resources/me/callum/club_plugin/assets/minecraftAsset.json

# copy uniswap deployments into Kotlin proj
cp web3-infra/uniswap_deployments.json server-plugin/src/main/resources/me/callum/club_plugin/assets/uniswap_deployments.json


echo "✅ Contracts deployed and ABI + deployment info copied successfully."
echo "Building server-plugin.jar..."

cd server-plugin
mvn clean package
echo "✅ server-plugin successfully built at target/club_plugin-1.0.jar."

cd ..
cp server-plugin/target/club_plugin-1.0.jar docker-minecraft-server/plugins/club_plugin-1.0.jar
echo "✅ copied club_plugin-1.0.jar into docker-minecraft-server/plugins."

cd docker-minecraft-server
docker compose up --build
echo echo "Starting Minecraft server..."