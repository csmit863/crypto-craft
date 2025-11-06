// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import {ERC20} from "openzeppelin-contracts/contracts/token/ERC20/ERC20.sol";
import {Ownable} from "openzeppelin-contracts/contracts/access/Ownable.sol";

contract MinecraftAsset is ERC20, Ownable {

    constructor(string memory _name, string memory _symbol, address _owner)
        ERC20(_name, _symbol)
        Ownable(_owner)
    {}

    function tokenizeItems(address wallet, uint256 amount) external onlyOwner {
        _mint(wallet, amount);
    }

    function burnItems(address wallet, uint256 amount) external onlyOwner {
        _burn(wallet, amount);
    }
}
