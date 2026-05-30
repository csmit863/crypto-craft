set -e
source .env
cd web3-infra

if [ "$1" = "sepolia" ]; then
  echo "sepolia testnet selected"
  RPC_URL=${RPC_URL_SEPOLIA}
  PRIVATE_KEY=${PRIVATE_KEY_SEPOLIA}
  BLOCKCOIN_ADDRESS=${BLOCKCOIN_ADDRESS_SEPOLIA}
  ASSETFACTORY_ADDRESS=${ASSETFACTORY_ADDRESS_SEPOLIA}
  UNISWAP_FACTORY_ADDRESS=${UNISWAP_FACTORY_ADDRESS_SEPOLIA}
  UNISWAP_ROUTER_ADDRESS=${UNISWAP_ROUTER_ADDRESS_SEPOLIA}
elif [ "$1" = "base" ]; then
  echo "base mainnet selected"
  RPC_URL=${RPC_URL_BASE}
  PRIVATE_KEY=${PRIVATE_KEY_SEPOLIA}
  BLOCKCOIN_ADDRESS=${BLOCKCOIN_ADDRESS_BASE}
  ASSETFACTORY_ADDRESS=${ASSETFACTORY_ADDRESS_BASE}
  UNISWAP_FACTORY_ADDRESS=${UNISWAP_FACTORY_ADDRESS_BASE}
  UNISWAP_ROUTER_ADDRESS=${UNISWAP_ROUTER_ADDRESS_BASE}
else
  echo "qut testnet selected"
  RPC_URL=${RPC_URL_QUT}
  PRIVATE_KEY=${PRIVATE_KEY_ANVIL}
  BLOCKCOIN_ADDRESS=${BLOCKCOIN_ADDRESS_QUT}
  ASSETFACTORY_ADDRESS=${ASSETFACTORY_ADDRESS_QUT}
  UNISWAP_FACTORY_ADDRESS=${UNISWAP_FACTORY_ADDRESS_QUT}
  UNISWAP_ROUTER_ADDRESS=${UNISWAP_ROUTER_ADDRESS_QUT}
fi

forge install

# deploy blockcoin and asset factory, or use existing addresses
if [ -n "$BLOCKCOIN_ADDRESS" ] && [ -n "$ASSETFACTORY_ADDRESS" ]; then
  echo "Using pre-existing contracts: Blockcoin=$BLOCKCOIN_ADDRESS AssetFactory=$ASSETFACTORY_ADDRESS"
  echo '[{"logs":["'"$BLOCKCOIN_ADDRESS"'","'"$ASSETFACTORY_ADDRESS"'"],"success":true}]' > deployments.json
else
  echo "Deploying fresh contracts..."
  forge script script/DeployContracts.s.sol:Setup --private-key ${PRIVATE_KEY} \
  --broadcast --rpc-url ${RPC_URL} --json 2>&1 \
  | grep -E '^\{"logs"' \
  | jq -s '.' > deployments.json
fi

if [ -n "$UNISWAP_FACTORY_ADDRESS" ] && [ -n "$UNISWAP_ROUTER_ADDRESS" ]; then
  echo "Using pre-existing Uniswap: Factory=$UNISWAP_FACTORY_ADDRESS Router=$UNISWAP_ROUTER_ADDRESS"
  echo '[{"logs":["'"$UNISWAP_FACTORY_ADDRESS"'","'"$UNISWAP_ROUTER_ADDRESS"'"]}]' > uniswap_deployments.json
elif [ "$1" = "sepolia" ]; then
  echo "Using pre-deployed Uniswap V2 on Sepolia"
  echo '[{"logs":["0xF62c03E08ada871A0bEb309762E260a7a6a880E6","0xeE567Fe1712Faf6149d80dA1E6934E354124CfE3"]}]' > uniswap_deployments.json
elif [ "$1" = "base" ]; then
  echo "Using pre-deployed Uniswap V2 on Base"
  echo '[{"logs":["0x8909Dc15e40173Ff4699343b6eB8132c65e18eC6","0x4752ba5dbc23f44d87826276bf6fd6b1c372ad24"]}]' > uniswap_deployments.json
else
  forge script script/DeployUniswapV2.s.sol:DeployUniswapV2Testnet \
  --rpc-url $RPC_URL \
  --private-key $PRIVATE_KEY \
  --broadcast --json 2>&1 \
    | jq -s '.' > uniswap_deployments.json
fi

cd ..

# write adminConfiguration.json
cat > adminConfiguration.json << EOF
{
  "rpcUrl": "${RPC_URL}",
  "privateKey": "${PRIVATE_KEY}"
}
EOF
mkdir -p docker-minecraft-server/plugins/crypto-craft
cp adminConfiguration.json docker-minecraft-server/plugins/crypto-craft/adminConfiguration.json
echo "✅ adminConfiguration.json written."

cp web3-infra/deployments.json server-plugin/src/main/resources/me/callum/club_plugin/assets/deployments.json
cp web3-infra/out/BlockCoin.sol/BlockCoin.json server-plugin/src/main/resources/me/callum/club_plugin/assets/blockCoin.json
cp web3-infra/out/AssetFactory.sol/AssetFactory.json server-plugin/src/main/resources/me/callum/club_plugin/assets/assetFactory.json
cp web3-infra/out/MinecraftAsset.sol/MinecraftAsset.json server-plugin/src/main/resources/me/callum/club_plugin/assets/minecraftAsset.json
cp web3-infra/uniswap_deployments.json server-plugin/src/main/resources/me/callum/club_plugin/assets/uniswap_deployments.json
echo "✅ Contracts deployed and ABI + deployment info copied successfully."

echo "Building server-plugin.jar..."
cd server-plugin
mvn clean package
echo "✅ server-plugin successfully built at target/club_plugin-1.0.jar."
cd ..

cp server-plugin/target/club_plugin-1.0.jar docker-minecraft-server/plugins/club_plugin-1.0.jar
echo "✅ copied club_plugin-1.0.jar into docker-minecraft-server/plugins."

# clear stale state — but only if deploying fresh contracts
if [ -z "$BLOCKCOIN_ADDRESS" ] || [ -z "$ASSETFACTORY_ADDRESS" ]; then
  rm -f docker-minecraft-server/plugins/crypto-craft/assets.json
  rm -f docker-minecraft-server/plugins/crypto-craft/config.json
  echo "✅ Cleared stale plugin state."
else
  echo "ℹ️ Using existing contracts — preserving assets.json."
fi

cd docker-minecraft-server
sudo docker compose up --build
echo "Starting Minecraft server..."