package net.refractored.aceemployments.gui

import com.samjakob.spigui.buttons.SGButton
import com.samjakob.spigui.menu.SGMenu
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.AMPERSAND_CHAR
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.storage.OrderStorage
import net.refractored.aceemployments.gui.GuiHelper.getFallbackButton
import net.refractored.aceemployments.gui.GuiHelper.getOffset
import net.refractored.aceemployments.gui.GuiHelper.loadCosmeticItems
import net.refractored.aceemployments.gui.GuiHelper.loadNavButtons
import net.refractored.aceemployments.order.Order
import net.refractored.aceemployments.order.OrderStatus
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ceil

class ClaimedOrders(
    player: Player,
) {
    private val config = AceEmployments.instance.gui.getConfigurationSection("ClaimedOrders")!!

    private val rows = config.getInt("Rows", 6)

    private val orderSlots: List<Int> = config.getIntegerList("OrderSlots")

    private val pageCount: Int =
        ceil(
            OrderStorage.getAll()
                .count { 
                    it.assignee == player.uniqueId && 
                    (it.status == OrderStatus.CLAIMED || it.status == OrderStatus.INCOMPLETE)
                }
                .toLong()
                .toDouble() / orderSlots.count(),
        ).toInt().coerceAtLeast(1)

    val gui: SGMenu =
        AceEmployments.instance.spiGUI.create(
            // Me when no component support :((((
            LegacyComponentSerializer.legacy(AMPERSAND_CHAR).serialize(
                MessageUtil.replaceMessage(
                    config.getString("Title")!!,
                    listOf(
                        // I only did this for consistency in the lang.yml
                        MessageReplacement("{currentPage}"),
                        MessageReplacement("{maxPage}"),
                    ),
                ),
            ),
            config.getInt("Rows", 6),
        )

    init {
        gui.setOnPageChange { inventory ->
            inventory.clearAllButStickiedSlots()
            loadOrders(inventory.currentPage, player)
        }
        loadNavButtons(config, gui, pageCount)
        loadCosmeticItems(config, gui, pageCount)
        loadOrders(0, player)
    }

    /**
     * Clears all non-stickied slots, and loads the orders for the requested page.
     * @param page The page to load orders for.
     */
    private fun loadOrders(
        page: Int,
        player: Player,
    ) {
        gui.clearAllButStickiedSlots()
        val orders = Order.getPlayerAcceptedOrders(orderSlots.count(), page * orderSlots.count(), player.uniqueId)
        for ((index, slot) in orderSlots.withIndex()) {
            val button: SGButton = orders.getOrNull(index)?.let { getOrderButton(it) } ?: getFallbackButton(config)
            gui.setButton(slot + getOffset(page, rows), button)
        }
    }

    private fun getOrderButton(order: Order): SGButton {
        val displayItem = order.item.clone()
        displayItem.amount =
            if (order.itemAmount <= displayItem.maxStackSize) {
                order.itemAmount
            } else {
                displayItem.maxStackSize
            }
        val itemMetaCopy = displayItem.itemMeta
        val deadlineDuration = Duration.between(LocalDateTime.now(), order.timeDeadline ?: LocalDateTime.now())
        val createdDuration = Duration.between(order.timeCreated, LocalDateTime.now())
        val createdDurationText =
            MessageUtil.getMessage(
                "General.DatePastTense",
                listOf(
                    MessageReplacement(createdDuration.toDays().toString()),
                    MessageReplacement(createdDuration.toHoursPart().toString()),
                    MessageReplacement(createdDuration.toMinutesPart().toString()),
                ),
            )
        // Ensure we don't show negative time (deadline already passed)
        val days = if (deadlineDuration.isNegative) 0 else deadlineDuration.toDays().toInt()
        val hours = if (deadlineDuration.isNegative) 0 else deadlineDuration.toHoursPart()
        val minutes = if (deadlineDuration.isNegative) 0 else deadlineDuration.toMinutesPart()
        val deadlineDurationText =
            MessageUtil.getMessage(
                "General.DateFormat",
                listOf(
                    MessageReplacement(days.toString()),
                    MessageReplacement(hours.toString()),
                    MessageReplacement(minutes.toString()),
                ),
            )
        val infoLore =
            if (order.status != OrderStatus.INCOMPLETE) {
                MessageUtil.getMessageList(
                    "ClaimedOrders.OrderItemLore",
                    listOf(
                        MessageReplacement(order.cost.toString()),
                        MessageReplacement(Bukkit.getOfflinePlayer(order.user).name ?: "Unknown"),
                        MessageReplacement(createdDurationText),
                        MessageReplacement(deadlineDurationText),
                        MessageReplacement(order.itemAmount.toString()),
                        MessageReplacement(order.itemCompleted.toString()),
                    ),
                )
            } else {
                MessageUtil.getMessageList(
                    "ClaimedOrders.OrderItemLoreIncomplete",
                    listOf(
                        MessageReplacement(order.cost.toString()),
                        MessageReplacement(Bukkit.getOfflinePlayer(order.user).name ?: "Unknown"),
                        MessageReplacement(createdDurationText),
                        MessageReplacement(order.itemAmount.toString()),
                        MessageReplacement(order.itemsReturned.toString()),
                    ),
                )
            }

        if (itemMetaCopy.hasLore()) {
            val itemLore = itemMetaCopy.lore()!!
            itemLore.addAll(infoLore)
            itemMetaCopy.lore(itemLore)
        } else {
            itemMetaCopy.lore(infoLore)
        }

        displayItem.itemMeta = itemMetaCopy

        val button = SGButton(displayItem)

        button.setListener { event: InventoryClickEvent ->
            clickOrder(event, order)
        }
        return button
    }

    /**
     * Handles the click event for an order.
     * @param event The click event.
     * @param order The order.
     */
    private fun clickOrder(
        event: InventoryClickEvent,
        order: Order,
    ) {
        when (order.status) {
            OrderStatus.CLAIMED -> {
                gui.removeButton(event.slot + getOffset(gui.currentPage, rows))
                loadOrders(gui.currentPage, event.whoClicked as Player)
                order.status = OrderStatus.INCOMPLETE
                order.incompleteOrder()
            }
            OrderStatus.INCOMPLETE -> {
                val inventorySpaces =
                    event.whoClicked.inventory.storageContents.count {
                        it == null || (it.isSimilar(order.item) && it.amount < it.maxStackSize)
                    }
                if (inventorySpaces == 0) {
                    event.whoClicked.closeInventory()
                    event.whoClicked.sendMessage(
                        MessageUtil.getMessage("General.InventoryFull"),
                    )
                    return
                }
                if (order.itemCompleted == order.itemsReturned) {
                    event.whoClicked.closeInventory()
                    event.whoClicked.sendMessage(
                        MessageUtil.getMessage("ClaimedOrders.OrderAlreadyRefunded"),
                    )
                    return
                }
                if (giveRefundableItems(order, (event.whoClicked as Player))) {
                    event.whoClicked.closeInventory()
                    event.whoClicked.sendMessage(
                        MessageUtil.getMessage("ClaimedOrders.OrderFullyRefunded"),
                    )
                    gui.removeButton(event.slot + getOffset(gui.currentPage, rows))
                    OrderStorage.delete(order)
                    gui.refreshInventory(event.whoClicked)
                } else {
                    event.whoClicked.closeInventory()
                    event.whoClicked.sendMessage(
                        MessageUtil.getMessage(
                            "ClaimedOrders.OrderPartiallyRefunded",
                            listOf(
                                MessageReplacement((order.itemCompleted - order.itemsReturned).toString()),
                            ),
                        ),
                    )
                }
            }

            else -> return
        }
        event.whoClicked.closeInventory()
    }

    /**
     * Refunds the items to the assignee of the order
     * This also updates the order itemsReturned
     * @return true if all items have been refunded
     */
    private fun giveRefundableItems(
        order: Order,
        player: Player,
    ): Boolean {
        var itemsLeft = order.itemCompleted - order.itemsReturned
        while (itemsLeft > 0) {
            if (player.inventory.storageContents.count {
                    it == null || (it.isSimilar(order.item) && it.amount < it.maxStackSize)
                } == 0
            ) {
                break
            }
            val itemAmount =
                if (itemsLeft < order.item.maxStackSize) {
                    itemsLeft
                } else {
                    order.item.maxStackSize
                }
            itemsLeft -= itemAmount
            val item =
                order.item.clone().apply {
                    amount = itemAmount
                }
            val excessItems = player.inventory.addItem(item)
            itemsLeft += excessItems.values.sumOf { it.amount }
        }
        order.itemsReturned = order.itemCompleted - itemsLeft
        OrderStorage.update(order)
        return (order.itemsReturned == order.itemCompleted)
    }

    companion object {
        /**
         * Creates an instance of the ClaimedOrders class, and returns a working gui.
         * @return The gui.
         */
        fun getGUI(player: Player): SGMenu {
            val claimedOrders = ClaimedOrders(player)
            return claimedOrders.gui
        }
    }
}
