// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import {MinecraftAsset} from "./MinecraftAsset.sol";
import { Ownable } from "openzeppelin-contracts/contracts/access/Ownable.sol";


contract AssetFactory is Ownable {
    event AssetCreated(address assetAddress, string name, string symbol, address owner);

    address[] public allAssets;
    constructor() Ownable(msg.sender) {}

    function createAsset(string memory name, string memory symbol) external returns (address) {
        MinecraftAsset newAsset = new MinecraftAsset(name, symbol, msg.sender);
        allAssets.push(address(newAsset));
        emit AssetCreated(address(newAsset), name, symbol, msg.sender);
        return address(newAsset);
    }

    function getAllAssets() external view returns (address[] memory) {
        return allAssets;
    }
}


import { IUniswapV2Router02 } from "briefcase/src/protocols/v2-periphery/interfaces/IUniswapV2Router02.sol";
import { IUniswapV2Factory } from "briefcase/src/protocols/v2-core/interfaces/IUniswapV2Factory.sol";
import { IUniswapV2Pair } from "briefcase/src/protocols/v2-core/interfaces/IUniswapV2Pair.sol";
import { MinecraftAsset } from "./MinecraftAsset.sol";
import { BlockCoin } from "./BlockCoin.sol";
import { IERC20 } from "openzeppelin-contracts/contracts/token/ERC20/IERC20.sol";

// TODO: when an asset is created, automatically create uniswap pair 
//       & seed with Blockcoin liquidity

contract AssetFactoryV2 is Ownable {
    event AssetCreated(
        address assetAddress,
        string name,
        string symbol,
        address owner,
        address pair,
        uint256 initialAssetAmount,
        uint256 initialBlockcoinAmount
    );

    mapping(address => bool) public isAsset;
    address[] public allAssets;

    IUniswapV2Router02 public immutable router;
    IUniswapV2Factory  public immutable uniswapFactory;
    BlockCoin          public immutable blockcoin;

    uint256 public constant BLOCKCOIN_SEED   = 1000 * 1e18;
    uint256 public constant ASSET_SEED_MIN   = 5    * 1e18;
    uint256 public constant ASSET_SEED_RANGE = 16  * 1e18;

    constructor(
        address _router
    ) Ownable(msg.sender) {
        router         = IUniswapV2Router02(_router);
        uniswapFactory = IUniswapV2Factory(IUniswapV2Router02(_router).factory());
        blockcoin      = new BlockCoin(); // AssetFactoryV2 becomes owner of BlockCoin, thus being minted 3m blockcoins
    }

    function createAsset(string memory name, string memory symbol) external returns (address) {
        // factory is owner so it can mint seed supply for the pool
        MinecraftAsset newAsset = new MinecraftAsset(name, symbol, address(this));
        address assetAddress = address(newAsset);

        allAssets.push(assetAddress);
        isAsset[assetAddress] = true;

        // pseudo-random seed amount between 5-20 units
        uint256 assetSeedAmount = ASSET_SEED_MIN + (
            uint256(keccak256(abi.encodePacked(block.prevrandao, assetAddress, block.timestamp)))
            % ASSET_SEED_RANGE
        );

        // mint seed amounts directly into this contract
        newAsset.tokenizeItems(address(this), assetSeedAmount);


        // create pair if it doesn't exist yet
        address pair = uniswapFactory.getPair(assetAddress, address(blockcoin));
        if (pair == address(0)) {
            pair = uniswapFactory.createPair(assetAddress, address(blockcoin));
        }

        // approve router to pull both tokens
        IERC20(assetAddress).approve(address(router), assetSeedAmount);
        IERC20(address(blockcoin)).approve(address(router), BLOCKCOIN_SEED);

        // seed the pool: LP tokens locked in factory permanently
        router.addLiquidity(
            assetAddress,
            address(blockcoin),
            assetSeedAmount,
            BLOCKCOIN_SEED,
            0,               // amountAMin: no slippage protection needed for initial seed
            0,               // amountBMin
            address(this),   // LP tokens stay in factory (locked liquidity)
            block.timestamp + 300
        );

        emit AssetCreated(assetAddress, name, symbol, msg.sender, pair, assetSeedAmount, BLOCKCOIN_SEED);
        return assetAddress;
    }

    function getAllAssets() external view returns (address[] memory) {
        return allAssets;
    }
}