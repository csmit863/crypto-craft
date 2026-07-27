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

        // player => assetAddress => LP tokens held by factory on their behalf
    mapping(address => mapping(address => uint256)) public lpBalances;
    // player => list of assets they've provided liquidity for
    mapping(address => address[]) public liquidityPositions;

    event LiquidityAdded(address indexed player, address indexed asset, uint256 assetAmount, uint256 blockCoinAmount, uint256 lpTokens);
    event LiquidityRemoved(address indexed player, address indexed asset, uint256 blockCoinOut);
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



    // player brings physical items (factory mints them) + BlockCoin from their wallet
    function tokenizeAndDeposit(
        address assetAddress,
        uint256 assetAmount,      // items from Minecraft inventory
        uint256 blockCoinAmount   // pulled from player wallet
    ) external {
        require(isAsset[assetAddress], "Not a valid asset");
        require(assetAmount > 0 && blockCoinAmount > 0, "Amounts must be > 0");

        // mint asset tokens directly to this contract
        MinecraftAsset(assetAddress).tokenizeItems(address(this), assetAmount);

        // pull BlockCoin from player
        IERC20(address(BLOCKCOIN)).transferFrom(msg.sender, address(this), blockCoinAmount);

        // approve router
        IERC20(assetAddress).approve(address(ROUTER), assetAmount);
        IERC20(address(BLOCKCOIN)).approve(address(ROUTER), blockCoinAmount);

        (uint256 assetUsed, uint256 blockCoinUsed, uint256 lp) = ROUTER.addLiquidity(
            assetAddress,
            address(BLOCKCOIN),
            assetAmount,
            blockCoinAmount,
            (assetAmount * 95) / 100,
            (blockCoinAmount * 95) / 100,
            address(this),   // factory holds LP tokens
            block.timestamp + 300
        );

        // refund unused BlockCoin
        if (blockCoinAmount > blockCoinUsed) {
            IERC20(address(BLOCKCOIN)).transfer(msg.sender, blockCoinAmount - blockCoinUsed);
        }

        // track position
        if (lpBalances[msg.sender][assetAddress] == 0) {
            liquidityPositions[msg.sender].push(assetAddress);
        }
        lpBalances[msg.sender][assetAddress] += lp;

        emit LiquidityAdded(msg.sender, assetAddress, assetUsed, blockCoinUsed, lp);
    }

    // remove liquidity for one asset, convert everything to BlockCoin
    function withdrawLiquidity(address assetAddress) external {
        uint256 lp = lpBalances[msg.sender][assetAddress];
        require(lp > 0, "No position");

        address pair = UNISWAP_FACTORY.getPair(assetAddress, address(BLOCKCOIN));
        require(pair != address(0), "Pair not found");

        // clear position before external calls
        lpBalances[msg.sender][assetAddress] = 0;
        _removeFromPositions(msg.sender, assetAddress);

        // remove liquidity
        IERC20(pair).approve(address(ROUTER), lp);
        (uint256 assetOut, uint256 blockCoinOut) = ROUTER.removeLiquidity(
            assetAddress,
            address(BLOCKCOIN),
            lp,
            0,
            0,
            address(this),
            block.timestamp + 300
        );

        // swap asset back to BlockCoin
        IERC20(assetAddress).approve(address(ROUTER), assetOut);
        address[] memory path = new address[](2);
        path[0] = assetAddress;
        path[1] = address(BLOCKCOIN);

        uint256[] memory amounts = ROUTER.swapExactTokensForTokens(
            assetOut,
            0,
            path,
            msg.sender,      // BlockCoin from swap goes directly to player
            block.timestamp + 300
        );

        // send BlockCoin from removeLiquidity to player
        IERC20(address(BLOCKCOIN)).transfer(msg.sender, blockCoinOut);

        emit LiquidityRemoved(msg.sender, assetAddress, blockCoinOut + amounts[1]);
    }

    // withdraw all positions at once
    function withdrawAllLiquidity() external {
        address[] memory assets = liquidityPositions[msg.sender];
        require(assets.length > 0, "No positions");

        for (uint256 i = 0; i < assets.length; i++) {
            uint256 lp = lpBalances[msg.sender][assets[i]];
            if (lp == 0) continue;

            address pair = UNISWAP_FACTORY.getPair(assets[i], address(BLOCKCOIN));
            if (pair == address(0)) continue;

            lpBalances[msg.sender][assets[i]] = 0;

            IERC20(pair).approve(address(ROUTER), lp);
            (uint256 assetOut, uint256 blockCoinOut) = ROUTER.removeLiquidity(
                assets[i],
                address(BLOCKCOIN),
                lp,
                0, 0,
                address(this),
                block.timestamp + 300
            );

            IERC20(assets[i]).approve(address(ROUTER), assetOut);
            address[] memory path = new address[](2);
            path[0] = assets[i];
            path[1] = address(BLOCKCOIN);

            uint256[] memory amounts = ROUTER.swapExactTokensForTokens(
                assetOut, 0, path, msg.sender, block.timestamp + 300
            );

            IERC20(address(BLOCKCOIN)).transfer(msg.sender, blockCoinOut);
            emit LiquidityRemoved(msg.sender, assets[i], blockCoinOut + amounts[1]);
        }

        delete liquidityPositions[msg.sender];
    }

    // view: LP balance for a player/asset
    function getLpBalance(address player, address assetAddress) external view returns (uint256) {
        return lpBalances[player][assetAddress];
    }

    // view: all assets a player has liquidity in
    function getLiquidityPositions(address player) external view returns (address[] memory) {
        return liquidityPositions[player];
    }

    function _removeFromPositions(address player, address assetAddress) internal {
        address[] storage positions = liquidityPositions[player];
        for (uint256 i = 0; i < positions.length; i++) {
            if (positions[i] == assetAddress) {
                positions[i] = positions[positions.length - 1];
                positions.pop();
                break;
            }
        }
    }

    function getAllAssets() external view returns (address[] memory) {
        return allAssets;
    }
}