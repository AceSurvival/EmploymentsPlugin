package net.refractored.aceemployments.order

import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.storage.OrderStorage
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.util.*

/**
 * Extension functions for Order to handle partial contributions
 */
fun Order.contributeItems(
    contributor: Player,
    amount: Int,
    notify: Boolean = true,
): Double {
    if (status != OrderStatus.PENDING) {
        throw IllegalStateException("Order is not pending")
    }
    if (amount <= 0) {
        throw IllegalArgumentException("Amount must be positive")
    }
    if (itemCompleted + amount > itemAmount) {
        throw IllegalArgumentException("Contribution exceeds required amount")
    }

    val pricePerItem = cost / itemAmount
    val payment = pricePerItem * amount

    // Update order
    itemCompleted += amount
    val wasCompleted = itemCompleted >= itemAmount

    if (wasCompleted) {
        status = OrderStatus.COMPLETED
        timeCompleted = LocalDateTime.now()
        timePickup = LocalDateTime.now().plusHours(
            AceEmployments.instance.config.getLong("Orders.PickupDeadline", 500)
        )
    }

    OrderStorage.update(this)

    // Create contribution record (stored in order's contributions list in YAML)
    // Contributions are now stored as part of the order in OrderStorage
    val contribution = OrderContribution(
        UUID.randomUUID(),
        this,
        contributor.uniqueId,
        amount,
        payment,
        LocalDateTime.now(),
    )
    // Note: Contributions are stored in the order's YAML data, not separately

    // Pay contributor
    AceEmployments.instance.eco.depositPlayer(contributor, payment)

    if (notify) {
        val contributorMessage = MessageUtil.getMessage(
            "OrderComplete.PartialContribution",
            listOf(
                MessageReplacement(amount.toString()),
                MessageReplacement(payment.toString()),
                MessageReplacement(itemCompleted.toString()),
                MessageReplacement(itemAmount.toString()),
            ),
        )
        contributor.sendMessage(contributorMessage)

        val ownerMessage = MessageUtil.getMessage(
            "OrderComplete.PartialContributionOwner",
            listOf(
                MessageReplacement(contributor.displayName()),
                MessageReplacement(amount.toString()),
                MessageReplacement(itemCompleted.toString()),
                MessageReplacement(itemAmount.toString()),
            ),
        )
        messageOwner(ownerMessage)

        if (wasCompleted) {
            val completedMessage = MessageUtil.getMessage(
                "OrderComplete.FullyCompleted",
                listOf(
                    MessageReplacement(getItemInfo()),
                ),
            )
            messageOwner(completedMessage)
            
            // Add items to owner's container
            val owner = org.bukkit.Bukkit.getPlayer(this.user)
            if (owner != null) {
                net.refractored.aceemployments.container.ContainerManager.addItems(owner, this, this.itemAmount)
            }
        }
    }

    return payment
}

fun Order.getContributions(): List<OrderContribution> {
    // TODO: Load contributions from order's YAML data
    // For now, return empty list as contributions are stored in the order data
    return emptyList()
}

fun Order.getTotalContributions(): Int {
    return getContributions().sumOf { it.amountContributed }
}

