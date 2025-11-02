-include .env


clean :; forge clean

update :; forge update

build :; forge clean && forge build --optimize --optimizer-runs 1000000

deployContracts :; forge create MyCoin --rpc-url localhost:8545