package me.callum.club_plugin.config

import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

abstract class AdminCommand : CommandExecutor {

    protected fun ensureOp(sender: CommandSender): Boolean {
        if (sender is Player && !sender.isOp) {
            sender.sendMessage("You do not have permission to use this command.")
            return false
        }
        return true
    }
    protected fun isEthAddress(value: String): Boolean {
        return value.startsWith("0x") && value.length == 42
    }
}
