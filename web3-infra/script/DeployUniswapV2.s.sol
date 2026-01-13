// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

import "forge-std/Script.sol";
import {UniswapV2FactoryDeployer} from "briefcase/deployers/v2-core/UniswapV2FactoryDeployer.sol";
import {UniswapV2Router02Deployer} from "briefcase/deployers/v2-periphery/UniswapV2Router02Deployer.sol";
import {WETH9} from "../src/WETH9.sol";

contract DeployUniswapV2Testnet is Script {
    function run() external {
        vm.startBroadcast();

        address deployer = msg.sender;

        // 1. Deploy WETH
        WETH9 weth = new WETH9();
        //console.log("WETH deployed at:", address(weth));

        // 2. Deploy Uniswap Factory
        address factory = address(UniswapV2FactoryDeployer.deploy(deployer));
        console.log(factory);

        // 3. Deploy Router
        address router = address(UniswapV2Router02Deployer.deploy(factory, address(weth)));
        console.log(router);

        vm.stopBroadcast();
    }
}
