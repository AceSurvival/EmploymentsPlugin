package net.refractored.aceemployments.commands

import net.refractored.aceemployments.storage.OrderStorage
import net.refractored.aceemployments.exceptions.CommandErrorException
import net.refractored.aceemployments.order.OrderStatus
import net.refractored.aceemployments.order.contributeItems
import net.refractored.aceemployments.util.MessageUtil
import net.refractored.aceemployments.commands.framework.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.Damageable
import java.util.*

class ContributeCommand {
    @PermissionAnnotation("aceemployments.contribute")
    @DescriptionAnnotation("Contribute items to a job listing")
    @CommandAnnotation("employ contribute", "emp contribute", "employments contribute")
    fun contribute(sender: CommandSender, orderId: String) {
        val player = sender as? Player ?: throw CommandErrorException(
            MessageUtil.getMessage("General.IsNotPlayer")
        )
        
        val orderUUID = try {
            UUID.fromString(orderId)
        } catch (e: IllegalArgumentException) {
            throw CommandErrorException(MessageUtil.getMessage("General.InvalidOrderId"))
        }

        val order = OrderStorage.getById(orderUUID)
            ?: throw CommandErrorException(MessageUtil.getMessage("General.OrderNotFound"))

        if (order.status != OrderStatus.PENDING) {
            throw CommandErrorException(MessageUtil.getMessage("General.OrderNotPending"))
        }

        if (order.user == player.uniqueId) {
            throw CommandErrorException(MessageUtil.getMessage("General.CannotContributeToOwnOrder"))
        }

        // Find matching items in inventory
        var totalContributed = 0
        val itemsToRemove = mutableListOf<org.bukkit.inventory.ItemStack>()

        for (item in player.inventory.storageContents) {
            if (item == null) continue
            if (!order.itemMatches(item)) continue

            // Check durability for damageable items
            if (order.item.itemMeta is Damageable && item.itemMeta is Damageable) {
                val orderDamage = (order.item.itemMeta as Damageable).damage
                val itemDamage = (item.itemMeta as Damageable).damage
                if (orderDamage != itemDamage) {
                    continue
                }
            }

            val remainingNeeded = order.itemAmount - order.itemCompleted
            if (remainingNeeded <= 0) break

            val toContribute = minOf(item.amount, remainingNeeded)
            totalContributed += toContribute
            itemsToRemove.add(item.clone().apply { amount = toContribute })
            item.amount -= toContribute
        }

        if (totalContributed == 0) {
            throw CommandErrorException(MessageUtil.getMessage("OrderComplete.NoItemsFound"))
        }

        // Contribute items
        val payment = order.contributeItems(player, totalContributed)

        // Remove items from inventory
        for (itemToRemove in itemsToRemove) {
            player.inventory.removeItem(itemToRemove)
        }

        sender.sendMessage(
            MessageUtil.getMessage(
                "OrderComplete.ContributionSuccess",
                listOf(
                    net.refractored.aceemployments.util.MessageReplacement(totalContributed.toString()),
                    net.refractored.aceemployments.util.MessageReplacement(payment.toString()),
                ),
            ),
        )
    }
}

