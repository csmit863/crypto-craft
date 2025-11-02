// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.13;

import { Script, console } from "forge-std/Script.sol";
import { BlockCoin } from "../src/BlockCoin.sol";
import { AssetFactory } from "../src/AssetFactory.sol";
import { MinecraftAsset } from "../src/MinecraftAsset.sol";

contract DeployMC is Script {
    function run() public {
        vm.startBroadcast();

        // create blockcoin instance and save the address
        BlockCoin blockcoin = new BlockCoin();
        console.log("blockcoin token address:", address(blockcoin));

        // create asset factory contract
        AssetFactory assetFactory = new AssetFactory();
        console.log("asset factory address:", address(assetFactory));

        vm.stopBroadcast();

    }
}


contract NewAsset is Script {
    function run() public {
        vm.startBroadcast();

        // create blockcoin instance and save the address
        BlockCoin blockcoin = BlockCoin(0x0B306BF915C4d645ff596e518fAf3F9669b97016);

        // create asset factory contract
        AssetFactory assetFactory = AssetFactory(0x959922bE3CAee4b8Cd9a407cc3ac1C251C2007B1);

        address asset = assetFactory.createAsset("test", "test");

        console.log(asset);

        vm.stopBroadcast();

    }
}

contract GetAsset is Script {
    function run() public {
        vm.startBroadcast();                  
        MinecraftAsset asset = MinecraftAsset(0xbc51860c89838ec548d7190657874556407423f4);

        address target = 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266; // Address you're minting for

        string memory name = asset.name();
        string memory symbol = asset.symbol(); 

        // Debugging logs
        console.log("Contract owner address:", asset.owner());
        console.log("Target address:", target);
        console.log("msg.sender", msg.sender);
        require(asset.owner() == msg.sender, "Error: Only the owner can mint tokens!");

        asset.totalSupply();
        asset.tokenizeItems(target, 1);
        asset.burnItems(target, 1);

        // Get the total supply and the balance of the target address again after minting
        uint256 supply = asset.totalSupply();
        uint256 targetBalance = asset.balanceOf(target);

        // Log the supply and balance correctly
        console.log("Total supply after minting:", supply);
        console.log("Balance of target after minting:", targetBalance);

        vm.stopBroadcast();
    }
}

contract MintTest is Script {
    function run() public {
        vm.startBroadcast(0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80); // asset factory: 0x457cCf29090fe5A24c19c1bc95F492168C0EaFdb
        MinecraftAsset allium = MinecraftAsset(0xA4a4137C0Ee2b8034C63ccbe0428f0d45F30ab23);
        string memory name = allium.name(); // should be ALLIUM
        string memory symbol = allium.symbol(); // should be ALLI
        uint256 totalSupply = allium.totalSupply(); // should be 0
        address owner = allium.owner();
        //allium.tokenizeItems(0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266, 1);
        // 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 result from cast wallet address <key>
        // 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 result from owner
        
        allium.tokenizeItems(0x5b12dDCc01ED323251beD11613d5B16083d6881e, 1);
        allium.balanceOf(0x5b12dDCc01ED323251beD11613d5B16083d6881e);
        allium.totalSupply();
        vm.stopBroadcast();
    }
}
