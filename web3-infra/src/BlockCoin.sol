//SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

// uncomment if using remix:
// import "@openzeppelin/contracts/token/ERC20/ERC20.sol";


// uncomment if using foundry:
import "openzeppelin-contracts/contracts/token/ERC20/ERC20.sol";
import "openzeppelin-contracts/contracts/access/Ownable.sol";


contract BlockCoin is ERC20, Ownable {
    constructor()
    ERC20("BlockCoin","BLCK")
    Ownable(msg.sender)
    {
        _mint(msg.sender, 10000*10**18);
    }
}