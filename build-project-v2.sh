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

if [ -n "$BLOCKCOIN_ADDRESS" ] && [ -n "$ASSETFACTORY_ADDRESS" ]; then
  echo "Using pre-existing contracts: Blockcoin=$BLOCKCOIN_ADDRESS AssetFactory=$ASSETFACTORY_ADDRESS"
  echo '[{"logs":["'"$BLOCKCOIN_ADDRESS"'","'"$ASSETFACTORY_ADDRESS"'"],"success":true}]' > deployments.json

  # uniswap addresses still needed separately
  if [ -n "$UNISWAP_FACTORY_ADDRESS" ] && [ -n "$UNISWAP_ROUTER_ADDRESS" ]; then
    echo '[{"logs":["'"$UNISWAP_FACTORY_ADDRESS"'","'"$UNISWAP_ROUTER_ADDRESS"'"]}]' > uniswap_deployments.json
  elif [ "$1" = "base" ]; then
    echo '[{"logs":["0x8909Dc15e40173Ff4699343b6eB8132c65e18eC6","0x4752ba5dbc23f44d87826276bf6fd6b1c372ad24"]}]' > uniswap_deployments.json
  elif [ "$1" = "sepolia" ]; then
    echo '[{"logs":["0xF62c03E08ada871A0bEb309762E260a7a6a880E6","0xeE567Fe1712Faf6149d80dA1E6934E354124CfE3"]}]' > uniswap_deployments.json
  fi

else
  echo "Deploying fresh contracts..."

  # capture the single {"logs":[...]} line from forge output
  LOGS_LINE=$(forge script script/DeployCryptoCraft.s.sol:Setup \
    --private-key ${PRIVATE_KEY} \
    --broadcast --rpc-url ${RPC_URL} --json 2>&1 \
    | grep -E '^\{"logs"' \
    | head -n 1)

  # split into the two JSON files by index
  echo "$LOGS_LINE" | jq '[{logs: [.logs[0], .logs[1]], success: true}]' > deployments.json
  echo "$LOGS_LINE" | jq '[{logs: [.logs[2], .logs[3]]}]'               > uniswap_deployments.json
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
cp web3-infra/out/AssetFactoryV2.sol/AssetFactoryV2.json server-plugin/src/main/resources/me/callum/club_plugin/assets/assetFactory.json
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

# Set up each plugins folder independently
for i in 1 2 3; do
  PLUGINS_DIR="docker-minecraft-server/plugins${i}"
  mkdir -p "${PLUGINS_DIR}/crypto-craft"

  cp server-plugin/target/club_plugin-1.0.jar "${PLUGINS_DIR}/club_plugin-1.0.jar"
  echo "✅ Copied jar to plugins${i}."

  cp adminConfiguration.json "${PLUGINS_DIR}/crypto-craft/adminConfiguration.json"
  echo "✅ Copied adminConfiguration.json to plugins${i}."

  if [ ! -f "${PLUGINS_DIR}/crypto-craft/assets.json" ]; then
    echo "ℹ️  No assets.json in plugins${i} — will be created fresh on server start."
  else
    echo "ℹ️  Preserving existing assets.json in plugins${i}."
  fi
done

cd docker-minecraft-server
sudo docker compose up --build
echo "Starting Minecraft server..."