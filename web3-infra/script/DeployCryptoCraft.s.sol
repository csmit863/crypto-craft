// SPDX-License-Identifier: MIT
pragma solidity ^0.8.13;

import { Script, console } from "forge-std/Script.sol";
import { AssetFactoryV2 } from "../src/AssetFactoryV2.sol";
import {UniswapV2FactoryDeployer} from "briefcase/deployers/v2-core/UniswapV2FactoryDeployer.sol";
import {UniswapV2Router02Deployer} from "briefcase/deployers/v2-periphery/UniswapV2Router02Deployer.sol";
import {WETH9} from "../src/WETH9.sol";



contract Setup is Script {
    function run() public {
        vm.startBroadcast();

        // ----------
        // ** Uniswap Deployments **
        address deployer = msg.sender;

        // 1. Deploy WETH
        WETH9 weth = new WETH9();
        //console.log("WETH deployed at:", address(weth));

        // 2. Deploy Uniswap Factory
        address factory = address(UniswapV2FactoryDeployer.deploy(deployer));
        

        // 3. Deploy Router
        address router = address(UniswapV2Router02Deployer.deploy(factory, address(weth)));
        // ----------


        // ----------
        // ** CryptoCraft V2 Deployment **
        AssetFactoryV2 assetFactoryV2 = new AssetFactoryV2(router);
        
        // log blockcoin address
        console.log(address(assetFactoryV2.BLOCKCOIN())); // [0]
        // log assetFactory address
        console.log(address(assetFactoryV2)); // [1]
        // log uniswap factory
        console.log(factory); // [2]
        // log uniswap router
        console.log(router); // [3]
        // ----------

        vm.stopBroadcast();
    }
}