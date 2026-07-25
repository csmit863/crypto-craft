// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import { IUniswapV2Router02 } from "briefcase/protocols/v2-periphery/interfaces/IUniswapV2Router02.sol";
import { IUniswapV2Factory } from "briefcase/protocols/v2-core/interfaces/IUniswapV2Factory.sol";
import { IUniswapV2Pair } from "briefcase/protocols/v2-core/interfaces/IUniswapV2Pair.sol";
import { MinecraftAsset } from "./MinecraftAsset.sol";
import { BlockCoin } from "./BlockCoin.sol";
import { IERC20 } from "openzeppelin-contracts/contracts/token/ERC20/IERC20.sol";
import { Ownable } from "openzeppelin-contracts/contracts/access/Ownable.sol";

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

    IUniswapV2Router02 public immutable ROUTER;
    IUniswapV2Factory  public immutable UNISWAP_FACTORY;
    BlockCoin          public immutable BLOCKCOIN;

    uint256 public constant SEED_MIN   = 5    * 1e18;
    uint256 public constant SEED_RANGE = 16  * 1e18;

    event AssetBurned(address asset, address caller, uint256 amountOut, uint256 amount);
    event AssetSold(address asset, address recipient, uint256 amountIn, uint256 blockCoinOut);

    constructor(
        address _router
    ) Ownable(msg.sender) {
        ROUTER         = IUniswapV2Router02(_router);
        UNISWAP_FACTORY = IUniswapV2Factory(IUniswapV2Router02(_router).factory());
        BLOCKCOIN      = new BlockCoin(); // AssetFactoryV2 becomes owner of BlockCoin, thus being minted 3m blockcoins
    }

    // should be onlyOwner
    function createAsset(string memory name, string memory symbol) external returns (address) {
        
        // factory is owner so it can mint seed supply for the pool
        MinecraftAsset newAsset = new MinecraftAsset(name, symbol, address(this));
        address assetAddress = address(newAsset);

        allAssets.push(assetAddress);
        isAsset[assetAddress] = true;

        // pseudo-random seed amount between 5-20 units
        uint256 assetSeedAmount = SEED_MIN + (
            uint256(keccak256(abi.encodePacked(block.prevrandao, assetAddress, block.timestamp)))
            % SEED_RANGE
        );

        uint256 blockcoinSeedAmount = (SEED_MIN + (
            uint256(keccak256(abi.encodePacked(block.prevrandao, address(BLOCKCOIN), block.timestamp)))
            % SEED_RANGE
        )) * 69;

        // mint seed amounts of MinecraftAsset directly into this contract
        newAsset.tokenizeItems(address(this), assetSeedAmount);


        // create pair if it doesn't exist yet
        address pair = UNISWAP_FACTORY.getPair(assetAddress, address(BLOCKCOIN));
        if (pair == address(0)) {
            pair = UNISWAP_FACTORY.createPair(assetAddress, address(BLOCKCOIN));
        }

        // approve router to pull both tokens
        IERC20(assetAddress).approve(address(ROUTER), assetSeedAmount);
        IERC20(address(BLOCKCOIN)).approve(address(ROUTER), blockcoinSeedAmount);

        // seed the pool: LP tokens locked in factory permanently
        ROUTER.addLiquidity(
            assetAddress,
            address(BLOCKCOIN),
            assetSeedAmount,
            blockcoinSeedAmount,
            0,               // amountAMin: no slippage protection needed for initial seed
            0,               // amountBMin
            address(this),   // LP tokens stay in factory (locked liquidity)
            block.timestamp + 300
        );

        emit AssetCreated(assetAddress, name, symbol, msg.sender, pair, assetSeedAmount, blockcoinSeedAmount);
        return assetAddress;
    }


    function tokenizeAndSellAsset(
        address assetAddress,
        address recipient,
        uint256 amountIn
    ) external onlyOwner {
        require(isAsset[assetAddress], "Not a valid asset");

        // mint asset tokens into this contract
        MinecraftAsset(assetAddress).tokenizeItems(address(this), amountIn);

        // build swap path: asset --> BLOCKCOIN
        address[] memory path = new address[](2);
        path[0] = assetAddress;
        path[1] = address(BLOCKCOIN);

        // get quote with 1% slippage tolerance
        uint256[] memory amountsOut = ROUTER.getAmountsOut(amountIn, path);
        uint256 amountOutMin = amountsOut[1] * 99 / 100;

        // approve router and swap
        IERC20(assetAddress).approve(address(ROUTER), amountIn);
        uint256[] memory amounts = ROUTER.swapExactTokensForTokens(
            amountIn,
            amountOutMin,
            path,
            recipient,  // BlockCoin goes directly to recipient
            block.timestamp + 300
        );

        // amounts[1] is actual BlockCoin received. goes straight to recipient via router
        emit AssetSold(assetAddress, recipient, amountIn, amounts[1]);
    }

    function buyAssetAndBurn(
        address assetAddress,
        uint256 amountOut  // how many item tokens the user wants
    ) external {
        require(isAsset[assetAddress], "Not a valid asset");

        // build swap path: BLOCKCOIN --> asset
        address[] memory path = new address[](2);
        path[0] = address(BLOCKCOIN);
        path[1] = assetAddress;

        // calculate how much BLOCKCOIN is needed
        uint256[] memory amountsIn = ROUTER.getAmountsIn(amountOut, path);
        uint256 amountInMax = amountsIn[0] * 101 / 100; // 1% slippage

        // pull BLOCKCOIN from caller into this contract
        IERC20(address(BLOCKCOIN)).transferFrom(msg.sender, address(this), amountInMax);

        // approve router and swap
        IERC20(address(BLOCKCOIN)).approve(address(ROUTER), amountInMax);
        uint256[] memory amounts = ROUTER.swapTokensForExactTokens(
            amountOut,
            amountInMax,
            path,
            address(this),  // asset tokens come here to be burned
            block.timestamp + 300
        );

        // refund any unused BLOCKCOIN
        uint256 refund = amountInMax - amounts[0];
        if (refund > 0) {
            IERC20(address(BLOCKCOIN)).transfer(msg.sender, refund);
        }

        // burn the asset tokens
        MinecraftAsset(assetAddress).burnItems(address(this), amountOut);

        emit AssetBurned(assetAddress, msg.sender, amountOut, amounts[0]);
    }

    function getAllAssets() external view returns (address[] memory) {
        return allAssets;
    }
}