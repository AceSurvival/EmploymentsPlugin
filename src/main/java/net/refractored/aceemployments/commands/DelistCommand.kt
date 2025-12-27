package net.refractored.aceemployments.commands

import net.refractored.aceemployments.storage.OrderStorage
import net.refractored.aceemployments.exceptions.CommandErrorException
import net.refractored.aceemployments.order.Order
import net.refractored.aceemployments.order.OrderStatus
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import net.refractored.aceemployments.commands.framework.*
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.util.*

class DelistCommand {
    @PermissionAnnotation("aceemployments.admin.delist")
    @DescriptionAnnotation("Cancel all job listings for a player")
    @CommandAnnotation("employ delist", "emp delist", "employments delist")
    fun delist(sender: CommandSender, targetPlayer: String) {
        val target = Bukkit.getOfflinePlayer(targetPlayer)
        if (!target.hasPlayedBefore() && !target.isOnline) {
            throw CommandErrorException(
                MessageUtil.getMessage("General.PlayerNotFound"),
            )
        }

        val orders = OrderStorage.getOrdersByStatus(OrderStatus.PENDING)
            .filter { it.user == target.uniqueId }

        var cancelled = 0
        for (order in orders) {
            order.removeOrder()
            cancelled++
        }

        sender.sendMessage(
            MessageUtil.getMessage(
                "Delist.Cancelled",
                listOf(
                    MessageReplacement(target.name ?: "Unknown"),
                    MessageReplacement(cancelled.toString()),
                ),
            ),
        )
    }
}

