// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

import "forge-std/Script.sol";
import {UniswapV2FactoryDeployer} from "briefcase/deployers/v2-core/UniswapV2FactoryDeployer.sol";
import {UniswapV2Router02Deployer} from "briefcase/deployers/v2-periphery/UniswapV2Router02Deployer.sol";
import {IWETH} from "briefcase/protocols/v2-periphery/interfaces/IWETH.sol";

contract DeployUniswapV2 is Script {
    function run() external {
        vm.startBroadcast();

        address deployer = msg.sender;

        // Deploy Factory
        address factory = address(UniswapV2FactoryDeployer.deploy(deployer));
        console.log("Factory:", factory);

        // Deploy WETH (for testnets, you can use canonical WETH)
        IWETH weth = IWETH(payable(0xB4FBF271143F4FBf7B91A5ded31805e42b2208d6)); // Goerli WETH

        // Deploy Router
        address router = address(UniswapV2Router02Deployer.deploy(factory, address(weth)));
        console.log("Router:", router);

        vm.stopBroadcast();
    }
}
