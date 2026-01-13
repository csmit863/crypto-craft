//SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import { ERC20 } from "openzeppelin-contracts/contracts/token/ERC20/ERC20.sol";
import { Ownable } from "openzeppelin-contracts/contracts/access/Ownable.sol";


contract BlockCoin is ERC20, Ownable {
    constructor()
    ERC20("BlockCoin","BLCK")
    Ownable(msg.sender)
    {
        _mint(msg.sender, 3000000*10**18);
    }
}