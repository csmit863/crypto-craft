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
