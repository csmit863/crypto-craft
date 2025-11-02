// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.13;

import { Script, console } from "forge-std/Script.sol";
import { BlockCoin } from "../src/BlockCoin.sol";

contract DistributeToken is Script {
    BlockCoin blockcoin;

    address private toAddress = 0x0e71cb405d96f851452e8247e5253E794d40Aa85;

    address private addr = 0x5FbDB2315678afecb367f032d93F642f64180aa3;    

    function run() public {
        vm.startBroadcast();
        blockcoin = BlockCoin(addr);
        bool success = blockcoin.transfer(toAddress, 850*10**18);
        vm.stopBroadcast();
    }
}
