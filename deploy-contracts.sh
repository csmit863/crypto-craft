cd web3-infra

ANVIL_DEFAULT_PRIVATE_KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80" # [0]

forge script script/DeployContracts.s.sol:Setup --private-key ${ANVIL_DEFAULT_PRIVATE_KEY} \
    --broadcast --rpc-url https://testnet.qutblockchain.club --json > deployments.json 

