// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;
contract Bank {}
/*
import { AssetFactoryV2 } from "./AssetFactoryV2.sol";
import { BlockCoin } from "./BlockCoin.sol";


contract Bank {
    // Players simply add any tokenised minecraft item to the Item Bank, it then acts
    // as liquidity for the market and generates yield. 
    // Players should be able to see how much yield they are generating (in blockcoins)
    // and withdraw and take the coins. 
    BlockCoin private immutable blockcoin;
    AssetFactoryV2 private immutable assetFactory;
    
    constructor(address blockCoinAddress, address assetFactoryAddress){
        // set up interaction with blockcoin & assetfactory
        blockcoin = BlockCoin(blockCoinAddress);
        assetFactory = AssetFactoryV2(assetFactoryAddress);
    }

    function deposit(address minecraftAssetAddress) public {
        // take blockcoin & itemtoken 
        // check assetFactory.allAssets
        // deposit liquidity to pair of blockcoin, itemtoken
        require(assetFactory.isAsset(minecraftAssetAddress), "MinecraftAsset does not exist in AssetFactory");
        
    }

    function takeYield() public {
        // take msg.sender

        
    }

    function withdraw() public {

    }
}*/