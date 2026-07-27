// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import { AssetFactoryV2 } from "./AssetFactoryV2.sol";
import { BlockCoin } from "./BlockCoin.sol";
import { IUniswapV2Router02 } from "briefcase/protocols/v2-periphery/interfaces/IUniswapV2Router02.sol";
import { IUniswapV2Factory } from "briefcase/protocols/v2-core/interfaces/IUniswapV2Factory.sol";
import { IUniswapV2Pair } from "briefcase/protocols/v2-core/interfaces/IUniswapV2Pair.sol";
import { IERC20 } from "openzeppelin-contracts/contracts/token/ERC20/IERC20.sol";

contract Bank {
    // Players simply add any tokenised minecraft item to the Item Bank, it then acts
    // as liquidity for the market and generates yield. 
    // Players should be able to see how much yield they are generating (in blockcoins)
    // and withdraw and take the coins. 

    BlockCoin          private immutable BLOCKCOIN;
    AssetFactoryV2     private immutable ASSET_FACTORY;
    IUniswapV2Router02 public  immutable ROUTER;
    IUniswapV2Factory  private immutable UNISWAP_FACTORY;

    struct Position {
        uint256 lpTokens;
        uint256 assetDeposited;
        uint256 blockCoinDeposited;
    }

    // player => assetAddress => Position
    mapping(address => mapping(address => Position)) public positions;
    // player => list of deposited assets (for iterating in withdraw)
    mapping(address => address[]) public playerAssets;

    event Deposited(address indexed player, address indexed asset, uint256 assetAmount, uint256 blockCoinAmount, uint256 lpTokens);
    event Withdrawn(address indexed player, address indexed asset, uint256 blockCoinOut);

    constructor(
        address _blockCoin,
        address _assetFactory,
        address _router
    ) {
        BLOCKCOIN       = BlockCoin(_blockCoin);
        ASSET_FACTORY   = AssetFactoryV2(_assetFactory);
        ROUTER          = IUniswapV2Router02(_router);
        UNISWAP_FACTORY = IUniswapV2Factory(IUniswapV2Router02(_router).factory());
    }

    // player deposits asset + blockcoin, bank holds LP tokens on their behalf
    function deposit(
        address _assetAddress,
        uint256 _assetAmount,
        uint256 _blockCoinAmount
    ) external {
        require(ASSET_FACTORY.isAsset(_assetAddress), "Not a valid asset");
        require(_assetAmount > 0 && _blockCoinAmount > 0, "Amounts must be > 0");

        // pull both tokens from player
        IERC20(_assetAddress).transferFrom(msg.sender, address(this), _assetAmount);
        IERC20(address(BLOCKCOIN)).transferFrom(msg.sender, address(this), _blockCoinAmount);

        // approve router
        IERC20(_assetAddress).approve(address(ROUTER), _assetAmount);
        IERC20(address(BLOCKCOIN)).approve(address(ROUTER), _blockCoinAmount);

        // add liquidity: LP tokens come to the bank
        (uint256 assetUsed, uint256 blockCoinUsed, uint256 lp) = ROUTER.addLiquidity(
            _assetAddress,
            address(BLOCKCOIN),
            _assetAmount,
            _blockCoinAmount,
            (_assetAmount * 95) / 100,      // 5% slippage tolerance
            (_blockCoinAmount * 95) / 100,
            address(this),                   // bank holds LP tokens
            block.timestamp + 300
        );

        // refund any unused tokens back to player
        if (_assetAmount > assetUsed) {
            IERC20(_assetAddress).transfer(msg.sender, _assetAmount - assetUsed);
        }
        if (_blockCoinAmount > blockCoinUsed) {
            IERC20(address(BLOCKCOIN)).transfer(msg.sender, _blockCoinAmount - blockCoinUsed);
        }

        // track position
        Position storage pos = positions[msg.sender][_assetAddress];
        if (pos.lpTokens == 0) {
            playerAssets[msg.sender].push(_assetAddress);
        }
        pos.lpTokens          += lp;
        pos.assetDeposited    += assetUsed;
        pos.blockCoinDeposited += blockCoinUsed;

        emit Deposited(msg.sender, _assetAddress, assetUsed, blockCoinUsed, lp);
    }

    // withdraw all positions, convert everything to blockcoin, send to player
    function withdraw() external {
        address[] storage assets = playerAssets[msg.sender];
        require(assets.length > 0, "No positions");

        for (uint256 i = 0; i < assets.length; i++) {
            _withdrawAsset(msg.sender, assets[i]);
        }

        delete playerAssets[msg.sender];
    }

    function _withdrawAsset(address _player, address _assetAddress) internal {
        Position storage pos = positions[_player][_assetAddress];
        require(pos.lpTokens > 0, "No position for this asset");

        address pair = UNISWAP_FACTORY.getPair(_assetAddress, address(BLOCKCOIN));
        require(pair != address(0), "Pair not found");

        // approve router to spend LP tokens
        IERC20(pair).approve(address(ROUTER), pos.lpTokens);

        // remove liquidity — get back asset + blockcoin
        (uint256 assetOut, uint256 blockCoinOut) = ROUTER.removeLiquidity(
            _assetAddress,
            address(BLOCKCOIN),
            pos.lpTokens,
            0,  // amountAMin
            0,  // amountBMin
            address(this),  // tokens come here first so we can swap
            block.timestamp + 300
        );

        // clear position before external calls
        delete positions[_player][_assetAddress];

        // swap asset tokens back to blockcoin
        IERC20(_assetAddress).approve(address(ROUTER), assetOut);

        address[] memory path = new address[](2);
        path[0] = _assetAddress;
        path[1] = address(BLOCKCOIN);

        uint256[] memory amounts = ROUTER.swapExactTokensForTokens(
            assetOut,
            0,              // amountOutMin — accept any amount
            path,
            _player,        // blockcoin goes directly to player
            block.timestamp + 300
        );

        // send the blockcoin from removeLiquidity directly to player too
        IERC20(address(BLOCKCOIN)).transfer(_player, blockCoinOut);

        uint256 totalBlockCoin = blockCoinOut + amounts[1];
        emit Withdrawn(_player, _assetAddress, totalBlockCoin);
    }

    // view: how many LP tokens a player has for an asset
    function getPosition(address _player, address _assetAddress)
        external view returns (uint256 lpTokens, uint256 assetDeposited, uint256 blockCoinDeposited)
    {
        Position storage pos = positions[_player][_assetAddress];
        return (pos.lpTokens, pos.assetDeposited, pos.blockCoinDeposited);
    }

    // view: all assets a player has deposited
    function getPlayerAssets(address _player) external view returns (address[] memory) {
        return playerAssets[_player];
    }
}