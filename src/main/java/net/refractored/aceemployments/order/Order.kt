package net.refractored.aceemployments.order

import com.samjakob.spigui.item.ItemBuilder
import com.willfp.eco.core.items.Items
import net.kyori.adventure.text.Component
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.mail.Mail
import net.refractored.aceemployments.storage.OrderStorage
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.LocalDateTime
import java.util.*

/**
 * Represents an order that has been placed on the job board
 */
data class Order(
    val id: UUID,
    /**
     * The reward of the order if completed
     */
    var cost: Double,
    /**
     * The player's uuid who created the order
     */
    var user: UUID,
    /**
     * The player's uuid who accepted the order
     */
    var assignee: UUID?,
    /**
     * The time the order was created
     */
    var timeCreated: LocalDateTime,
    /**
     * The time the order expires
     * This is only used if the order was never claimed
     */
    var timeExpires: LocalDateTime,
    /**
     * The time the order was claimed
     */
    var timeClaimed: LocalDateTime?,
    /**
     * The time the order is due
     * This is only used if the order was claimed
     * If this is not completed in time, the order will be marked as incomplete
     */
    var timeDeadline: LocalDateTime?,
    /**
     * The time the order was completed
     */
    var timeCompleted: LocalDateTime?,
    /**
     * The time the order gets permanently removed
     */
    var timePickup: LocalDateTime?,
    /**
     * The status of the order
     * @see OrderStatus
     */
    var status: OrderStatus,
    /**
     * The item
     *
     * This ItemStack is not representative of the amount of items required to complete it.
     * @see itemAmount
     */
    var item: ItemStack,
    /**
     * The amount of items required to complete the order
     */
    var itemAmount: Int,
    /**
     * The amount of items the assignee has turned in
     */
    var itemCompleted: Int,
    /**
     * The amount of items has been returned to the assignee
     * ONLY if the order was not completed in time, or cancelled.
     */
    var itemsReturned: Int,
    /**
     * The amount of items has been obtained by the user
     * ONLY if the order was completed in time.
     */
    var itemsObtained: Int,
) {
    /**
     * Get the display name of the item
     * @return The display name of the item
     */
    fun getItemInfo(): Component =
        MessageUtil.getMessage(
            "Orders.OrderInfo",
            listOf(
                MessageReplacement(item.displayName()),
                MessageReplacement(itemAmount.toString()),
                MessageReplacement(cost.toString()), // Optional
            ),
        )

    /**
     * Get the OfflinePlayer of the owner of the order
     * @return The owner of the order
     */
    fun getOwner(): OfflinePlayer = Bukkit.getOfflinePlayer(user)

    /**
     * Get the OfflinePlayer of the assignee of the order
     * @return The assignee of the order, or null if there is no assignee
     */
    fun getAssignee(): OfflinePlayer? {
        val assigneeUUID = assignee ?: return null
        return Bukkit.getOfflinePlayer(assigneeUUID)
    }

    /**
     * Sends a message to the owner if they are online, otherwise it will be sent as a mail
     * @param message The message to send
     * @throws IllegalStateException if the order does not have an assignee
     */
    fun messageOwner(message: Component) {
        getOwner().player?.sendMessage(message) ?: Mail.createMail(user, message)
    }

    /**
     * Sends a message to the assignee if they are online, otherwise it will be sent as a mail
     * @param message The message to send
     * @throws IllegalStateException if the order does not have an assignee
     */
    fun messageAssignee(message: Component) {
        val offlineAssignee = getAssignee() ?: throw IllegalStateException("Order does not have an assignee")
        offlineAssignee.player?.sendMessage(message) ?: Mail.createMail(offlineAssignee.uniqueId, message)
    }

    /**
     * Accept the order and assign it to a player
     * @param assigneePlayer The player who accepted the order
     * @param notify Whether to notify the user and assignee, default is true
     * @throws IllegalArgumentException if the order already has an assignee
     * @throws IllegalArgumentException if the assignee is the same as the user
     * @throws IllegalArgumentException if the order is not pending
     */
    fun acceptOrder(
        assigneePlayer: Player,
        notify: Boolean = true,
    ) {
        if (assignee != null) {
            throw IllegalArgumentException("Order already has an assignee")
        }
        if (user == assigneePlayer.uniqueId) {
            throw IllegalArgumentException("Assignee cannot be the same as the user")
        }
        if (status != OrderStatus.PENDING) {
            throw IllegalArgumentException("Order is not pending")
        }
        assignee = assigneePlayer.uniqueId
        timeClaimed = LocalDateTime.now()
        timeDeadline = LocalDateTime.now().plusHours(AceEmployments.instance.config.getLong("Orders.OrderDeadline"))
        status = OrderStatus.CLAIMED
        OrderStorage.update(this)
        if (!notify) return
        val ownerMessage =
            MessageUtil.getMessage(
                "AllOrders.OrderAcceptedNotification",
                listOf(
                    MessageReplacement(getItemInfo()),
                    MessageReplacement(assigneePlayer.displayName()),
                ),
            )
        messageOwner(ownerMessage)
        messageAssignee(
            MessageUtil.getMessage(
                "AllOrders.OrderAccepted",
            ),
        )
    }

    /**
     * Remove the order from the database and refund the user
     * @throws IllegalStateException if the order is not pending
     */
    fun removeOrder() {
        if (status != OrderStatus.PENDING) {
            throw IllegalStateException("Order is not pending.")
        }
        AceEmployments.instance.eco.depositPlayer(getOwner(), cost)
        OrderStorage.delete(this)
    }

    /**
     * Complete the order and pay the assignee
     * @param pay Whether to pay the assignee, default is true
     * @param notify Whether to notify the user and assignee, default is true
     */
    fun completeOrder(
        pay: Boolean = true,
        notify: Boolean = true,
    ) {
        val assigneePlayer = getAssignee() ?: throw IllegalStateException("Order does not have an assignee")
        itemCompleted = itemAmount
        status = OrderStatus.COMPLETED
        timeCompleted = LocalDateTime.now()
        timePickup = LocalDateTime.now().plusHours(AceEmployments.instance.config.getLong("Orders.PickupDeadline"))
        OrderStorage.update(this)
        if (pay) {
            AceEmployments.instance.eco.depositPlayer(
                assigneePlayer,
                cost,
            )
        }
        if (!notify) return
        val assigneeMessage =
            MessageUtil.getMessage(
                "OrderComplete.CompletedMessageAssignee",
                listOf(
                    MessageReplacement(getItemInfo()),
                    MessageReplacement(cost.toString()),
                ),
            )
        messageAssignee(assigneeMessage)
        val ownerMessage =
            MessageUtil.getMessage(
                "OrderComplete.CompletedMessageOwner",
                listOf(
                    MessageReplacement(getItemInfo()),
                    MessageReplacement(getAssignee()?.name ?: "Unknown"),
                ),
            )
        messageOwner(ownerMessage)
    }

    /**
     * Mark the order as expired and refund the user.
     * If items were contributed, the order is kept so the owner can collect them.
     * Otherwise, the order is deleted.
     * @param notify Whether to notify the user, default is true
     */
    fun expireOrder(notify: Boolean = true) {
        if (status != OrderStatus.PENDING) {
            throw IllegalStateException("Order cannot be marked expired if its status is not pending ($status)")
        }
        // Refund the owner for unfulfilled items
        val unfulfilledAmount = itemAmount - itemCompleted
        val refundAmount = (cost / itemAmount) * unfulfilledAmount
        if (refundAmount > 0) {
            AceEmployments.instance.eco.depositPlayer(getOwner(), refundAmount)
        }
        
        // If items were contributed, keep the order so owner can collect them
        // Otherwise, delete the order
        if (itemCompleted == 0) {
            OrderStorage.delete(this)
        } else {
            // Order is kept but will be filtered from browse GUI
            // Owner can still see it in MyOrders to collect items
            OrderStorage.update(this)
        }
        
        if (notify) {
            val message =
                MessageUtil.getMessage(
                    "AllOrders.OrderExpired",
                    listOf(
                        MessageReplacement(getItemInfo()),
                    ),
                )
            messageOwner(message)
        }
    }

    /**
     * Mark the order as canceled and notifies the assignee.
     * Used whenever the order is cancelled by the owner.
     * @param notify Whether to notify the user and assignee, default is true
     */
    fun cancelOrder(
        notify: Boolean = true,
        fullRefund: Boolean = false,
    ) {
        if (status == OrderStatus.INCOMPLETE) {
            throw IllegalStateException("Order is already marked incomplete")
        }
        if (status == OrderStatus.CANCELLED) {
            throw IllegalStateException("Order is already marked cancelled")
        }
        status = OrderStatus.CANCELLED
        if (fullRefund) {
            AceEmployments.instance.eco.depositPlayer(getOwner(), cost)
        } else {
            AceEmployments.instance.eco.depositPlayer(getOwner(), (cost / 2))
        }
        if (itemCompleted == 0) {
            // No point of keeping the order if no items were turned in
            OrderStorage.delete(this)
        } else {
            OrderStorage.update(this)
        }
        if (!notify) return
        if (assignee == null) return // This should never be null, but just in case
        val assigneeMessage =
            MessageUtil.getMessage(
                "MyOrders.AssigneeMessage",
                listOf(
                    MessageReplacement(getItemInfo()),
                ),
            )
        messageAssignee(assigneeMessage)
    }

    /**
     * Mark the order as incomplete and refund the user.
     * This is used when the assignee did not complete the order in time.
     * @param notify Whether to notify the user and assignee, default is true
     */
    fun incompleteOrder(notify: Boolean = true) {
        if (status == OrderStatus.INCOMPLETE) {
            throw IllegalStateException("Order is already marked incomplete")
        }
        status = OrderStatus.INCOMPLETE
        AceEmployments.instance.eco.depositPlayer(getOwner(), cost)
        if (itemCompleted == 0) {
            // No point of keeping the order if no items were turned in
            OrderStorage.delete(this)
        } else {
            timePickup = LocalDateTime.now().plusHours(AceEmployments.instance.config.getLong("Orders.PickupDeadline"))
            OrderStorage.update(this)
        }
        if (!notify) return
        val ownerMessage =
            MessageUtil.getMessage(
                "ClaimedOrders.OrderIncomplete",
                listOf(
                    MessageReplacement(getItemInfo()),
                ),
            )
        messageOwner(ownerMessage)
        if (assignee == null) return // This should never be null, but just in case
        val assigneeMessage =
            MessageUtil.getMessage(
                "ClaimedOrders.OrderIncompleteAssignee",
                listOf(
                    MessageReplacement(getItemInfo()),
                ),
            )
        messageAssignee(assigneeMessage)
    }

    /**
     * Checks if itemstack matches the order's itemstack
     * @param itemArg The itemstack to compare
     * @return Whether the itemstack matches the order itemstack
     */
    fun itemMatches(itemArg: ItemStack): Boolean {
        if (AceEmployments.instance.ecoPlugin) {
            Items.getCustomItem(item)?.let { customItem ->
                return customItem.matches(itemArg)
            }
        }
        return item.isSimilar(itemArg)
    }

    fun isOrderExpired(): Boolean = LocalDateTime.now().isAfter(timeExpires)

    fun isOrderDeadlinePassed(): Boolean {
        val deadline = timeDeadline ?: return false
        return LocalDateTime.now().isAfter(deadline)
    }

    fun isOrderPickupPassed(): Boolean {
        val pickupTime = timePickup ?: return false
        return LocalDateTime.now().isAfter(pickupTime)
    }

    companion object {
        /**
         * Create a new order and insert it into the database
         * @param user The user who created the order
         * @param cost The reward for completing the order
         * @param item The itemstack required to complete the order
         * @param amount The amount of items required to complete the order
         * @param hours The amount of hours the order will be available for
         * @return The created order
         */
        fun createOrder(
            user: UUID,
            cost: Double,
            item: ItemStack,
            amount: Int,
            hours: Long,
            announce: Boolean = true,
        ): Order {
            val maxItems = AceEmployments.instance.config.getInt("Orders.MaximumItems")
            when {
                maxItems == -1 && amount > item.maxStackSize -> {
                    throw IllegalArgumentException("Item stack size exceeded")
                }
                maxItems != 0 && amount >= maxItems -> {
                    throw IllegalArgumentException("Max orders exceeded")
                }
            }
            // Convert config seconds to hours for validation
            val maxSeconds = AceEmployments.instance.config.getLong("PlayerJobOffers.OfferExpireTime", 604800)
            val minSeconds = AceEmployments.instance.config.getLong("Orders.MinOrdersTime", 48 * 3600) // Convert hours to seconds if needed
            val maxHours = maxSeconds / 3600
            val minHours = if (minSeconds < 1000) minSeconds else minSeconds / 3600 // Support both formats
            
            if (hours > maxHours) {
                throw net.refractored.aceemployments.exceptions.CommandErrorException(
                    net.refractored.aceemployments.util.MessageUtil.getMessage(
                        "CreateOrder.MoreThanMaxHoursConfig",
                        listOf(net.refractored.aceemployments.util.MessageReplacement(maxHours.toString()))
                    )
                )
            }
            if (hours < minHours) {
                throw net.refractored.aceemployments.exceptions.CommandErrorException(
                    net.refractored.aceemployments.util.MessageUtil.getMessage(
                        "CreateOrder.MoreThanMinHoursConfig",
                        listOf(net.refractored.aceemployments.util.MessageReplacement(minHours.toString()))
                    )
                )
            }
            item.amount = 1
            // Convert hours to seconds for storage, then back to LocalDateTime
            val expireSeconds = hours * 3600
            val expireTime = LocalDateTime.now().plusSeconds(expireSeconds)
            
            val order =
                Order(
                    UUID.randomUUID(),
                    cost,
                    user,
                    null,
                    LocalDateTime.now(),
                    expireTime,
                    null,
                    null,
                    null,
                    null,
                    OrderStatus.PENDING,
                    item,
                    amount,
                    0,
                    0,
                    0,
                )
            OrderStorage.create(order)

            if (announce && AceEmployments.instance.config.getBoolean("PlayerJobOffers.BroadcastOnCreate.Enabled", false)) {
                val messageLines = AceEmployments.instance.config.getStringList("PlayerJobOffers.BroadcastOnCreate.Message")
                if (messageLines.isNotEmpty()) {
                    val playerName = order.getOwner().name ?: "Unknown"
                    val itemName = order.item.displayName()?.let { MessageUtil.toPlainText(it) } ?: order.item.type.name
                    val amount = order.itemAmount.toString()
                    val price = String.format("%.2f", order.cost)
                    val pricePerItem = String.format("%.2f", order.cost / order.itemAmount)
                    
                    messageLines.forEach { line ->
                        var processedLine = line
                            .replace("%player%", playerName)
                            .replace("%item%", itemName)
                            .replace("%amount%", amount)
                            .replace("%price%", price)
                            .replace("%price-per-item%", pricePerItem)
                        
                        val message = MessageUtil.parseFormattedMessage(processedLine)
                        AceEmployments.instance.server.broadcast(message)
                    }
                }
//                if (AceEmployments.instance.redisChat != null && AceEmployments.instance.config.getBoolean("Redischat.RedisChatAnnounce", false)) {
//                    AceEmployments.instance.redisChat!!
//                        .dataManager
//                        .sendChatMessage(
//                            ChatMessage(
//                                ChannelAudience(),
//                                "",
//                                MiniMessage.miniMessage().serialize(message),
//                            ChannelAudience(AceEmployments.instance.config.getString("Redischat.Channel", "public")),
//                        )
//                    )
//                }
            }
            return order
        }

        /**
         * Get a specific page of the newest orders from the database
         * @param limit Number of orders per page
         * @param offset Starting point for the current page
         * @return List of newest orders for the current page
         */
        fun getPendingOrders(
            limit: Int,
            offset: Int,
        ): List<Order> {
            return OrderStorage.getPendingOrders(limit, offset)
        }

        /**
         * Gets the max orders a player can create, if a player has a permission node it will be grabbed instead.
         * If they don't have one, the config option will be used instead.
         * If the config isn't set, it will default to 1.
         * @return The max order amount.
         */
        fun getMaxOrders(player: Player): Int {
            // Check for permission-based max orders (aceemployments.offers.* gives unlimited)
            if (player.hasPermission("aceemployments.offers.*")) {
                return Int.MAX_VALUE
            }
            
            // Check custom permissions from config
            val customPerms = AceEmployments.instance.config.getConfigurationSection("CreatePermissions.custom")
            var maxFromPerms = 0
            customPerms?.getKeys(false)?.forEach { permKey ->
                val permission = "aceemployments.offers.$permKey"
                if (player.hasPermission(permission)) {
                    val amount = customPerms.getInt("$permKey.amount", 0)
                    if (amount > maxFromPerms) {
                        maxFromPerms = amount
                    }
                }
            }
            
            // Use the highest value (permissions are not additive)
            val defaultAmount = AceEmployments.instance.config.getInt("CreatePermissions.default", 3)
            return maxOf(maxFromPerms, defaultAmount).coerceAtLeast(0)
        }

        /**
         * Gets the max claimed orders a player can claim, if a player has a permission node it will be grabbed instead.
         * If they don't have one, the config option will be used instead.
         * If the config isn't set, it will default to 1.
         * @return The max order amount.
         */
        fun getMaxOrdersAccepted(player: Player): Int {
            // Check for permission-based max accepted orders
            val maxFromPerms = player.effectivePermissions
                    .filter {
                    it.permission.startsWith("aceemployments.accepted.max.")
                }.mapNotNull { it.permission.substringAfter("aceemployments.accepted.max.").toIntOrNull() }
                    .maxOrNull()

            return maxFromPerms ?: AceEmployments.instance.config.getInt("Orders.MaxOrdersAccepted", 3)
        }

        /**
         * Get a specific page of a player's created orders from the database
         * @param limit Number of orders per page
         * @param offset Starting point for the current page
         * @param playerUUID Player UUID to get orders for
         * @return List of newest orders for the current page
         */
        fun getPlayerCreatedOrders(
            limit: Int,
            offset: Int,
            playerUUID: UUID,
        ): List<Order> {
            return OrderStorage.getPlayerCreatedOrders(limit, offset, playerUUID)
        }

        /**
         * Get a specific page of a player's accepted orders from the database
         * @param limit Number of orders per page
         * @param offset Starting point for the current page
         * @param playerUUID Player UUID to get orders for
         * @return List of newest orders for the current page
         */
        fun getPlayerAcceptedOrders(
            limit: Int,
            offset: Int,
            playerUUID: UUID,
        ): List<Order> {
            return OrderStorage.getPlayerAcceptedOrders(limit, offset, playerUUID)
        }

        /**
         * If the order was never claimed, and the order expire date has passed it will be marked as expired
         */
        fun updateExpiredOrders() {
            val orders = OrderStorage.getOrdersByStatus(OrderStatus.PENDING)
                .sortedBy { it.timeCreated }
            for (order in orders) {
                if (order.isOrderExpired()) {
                order.expireOrder()
                }
            }
        }

        /**
         * Updates all orders that have passed their deadline
         */
        fun updateDeadlineOrders() {
            val orders = OrderStorage.getOrdersByStatus(OrderStatus.CLAIMED)
                .sortedBy { it.timeClaimed ?: LocalDateTime.MIN }
            for (order in orders) {
                if (order.isOrderDeadlinePassed()) {
                order.incompleteOrder()
                }
            }
        }

        /**
         * Deletes all orders that were not picked up in time
         */
        fun updatePickupDeadline() {
            val orders = OrderStorage.getOrdersByStatus(OrderStatus.CLAIMED)
                .sortedBy { it.timeClaimed ?: LocalDateTime.MIN }
            for (order in orders) {
                if (order.isOrderPickupPassed()) {
                    OrderStorage.delete(order)
                }
            }
        }
    }
}
