package net.refractored.aceemployments.gui

import com.samjakob.spigui.buttons.SGButton
import com.samjakob.spigui.menu.SGMenu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.AMPERSAND_CHAR
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.storage.OrderStorage
import net.refractored.aceemployments.gui.GuiHelper.createButtonFromConfig
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

class MyOrders(
    player: Player,
) {
    private val config = AceEmployments.instance.gui.getConfigurationSection("MyOrders")!!
    private val menuConfig = MenuLoader.loadMenuConfig("your_jobs_menu.yml")

    private val rows = config.getInt("Rows", 6)

    private val orderSlots: List<Int> = config.getIntegerList("OrderSlots")

    private val pageCount: Int =
        ceil(
            OrderStorage.getAll()
                .count { it.user == player.uniqueId }
                .toLong()
                .toDouble() / orderSlots.count(),
        ).toInt().coerceAtLeast(1)

    val gui: SGMenu =
        AceEmployments.instance.spiGUI.create(
            // Me when no component support :((((
            LegacyComponentSerializer.legacy(AMPERSAND_CHAR).serialize(
                MessageUtil.toComponent(
                    MenuLoader.getMenuTitle("YourJobs").replace("%current%", "1").replace("%max%", pageCount.toString())
                ),
            ),
            config.getInt("Rows", 6),
        )

    init {
        gui.setOnPageChange { inventory ->
            inventory.clearAllButStickiedSlots()
            loadOrders(inventory.currentPage, player)
        }
        
        // Load menu items from menu.yml if available, otherwise fall back to gui.yml
        if (menuConfig != null) {
            MenuLoader.loadMenuItems(menuConfig, gui, pageCount, "YourJobs")
            loadCloseButtonFromMenu(menuConfig, gui)
        } else {
            loadNavButtons(config, gui, pageCount)
            loadCosmeticItems(config, gui, pageCount)
            loadCloseButton(config, gui)
        }
        loadOrders(0, player)
    }
    
    private fun loadCloseButtonFromMenu(menuConfig: org.bukkit.configuration.file.YamlConfiguration, gui: SGMenu) {
        val itemsSection = menuConfig.getConfigurationSection("items") ?: return
        
        val backArrowSection = itemsSection.getConfigurationSection("back-arrow")
        if (backArrowSection != null) {
            val slots = backArrowSection.getIntegerList("slots")
            for (page in 0 until pageCount) {
                val offset = GuiHelper.getOffset(page, rows)
                slots.forEach { slot ->
                    val actualSlot = slot + offset
                    if (actualSlot < gui.inventory.size) {
                        val button = SGButton(MenuLoader.createItemFromMenuSection(backArrowSection, "back-arrow", "YourJobs"))
                        button.setListener { event -> event.whoClicked.closeInventory() }
                        gui.setButton(actualSlot, button)
                        gui.stickSlot(actualSlot)
                    }
                }
            }
        }
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
        val orders = Order.getPlayerCreatedOrders(orderSlots.count(), page * orderSlots.count(), player.uniqueId)
        for ((index, slot) in orderSlots.withIndex()) {
            val button: SGButton = orders.getOrNull(index)?.let { getOrderButton(it) } ?: getFallbackButton(config)
            gui.setButton(slot + getOffset(page, rows), button)
        }
    }

    private fun getOrderButton(order: Order): SGButton {
        val item = order.item.clone()
        item.amount = minOf(order.itemAmount, item.maxStackSize)
        val itemMetaCopy = item.itemMeta
        val expireDuration = Duration.between(LocalDateTime.now(), order.timeExpires)
        // Ensure we don't show negative time (order already expired)
        val days = if (expireDuration.isNegative) 0 else expireDuration.toDays().toInt()
        val hours = if (expireDuration.isNegative) 0 else expireDuration.toHoursPart()
        val minutes = if (expireDuration.isNegative) 0 else expireDuration.toMinutesPart()
        val expireDurationText =
            MessageUtil.getMessage(
                "General.DateFormat",
                listOf(
                    MessageReplacement(days.toString()),
                    MessageReplacement(hours.toString()),
                    MessageReplacement(minutes.toString()),
                ),
            )
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

        val infoLore: MutableList<Component> = mutableListOf()

        when (order.status) {
            OrderStatus.PENDING -> {
                val isExpired = order.isOrderExpired()
                val expireDuration = Duration.between(LocalDateTime.now(), order.timeExpires)
                // Ensure we don't show negative time (order already expired)
                val days = if (expireDuration.isNegative) 0 else expireDuration.toDays().toInt()
                val hours = if (expireDuration.isNegative) 0 else expireDuration.toHoursPart()
                val minutes = if (expireDuration.isNegative) 0 else expireDuration.toMinutesPart()
                val expireDurationText =
                    MessageUtil.getMessage(
                        "General.DateFormat",
                        listOf(
                            MessageReplacement(days.toString()),
                            MessageReplacement(hours.toString()),
                            MessageReplacement(minutes.toString()),
                        ),
                    )
                
                // If expired but has items to collect, show collection message
                if (isExpired && order.itemCompleted > 0 && order.itemsObtained < order.itemCompleted) {
                    val itemsToCollect = order.itemCompleted - order.itemsObtained
                    val expiredStatusText = MessageUtil.parseFormattedMessage("&cExpired")
                    infoLore.addAll(
                        MessageUtil.getMessageList(
                            "MyOrders.OrderItemLore.Pending",
                            listOf(
                                MessageReplacement(order.cost.toString()),
                                MessageReplacement(createdDurationText),
                                MessageReplacement(expiredStatusText), // Show as expired Component
                                MessageReplacement(order.itemAmount.toString()),
                                MessageReplacement(MessageUtil.parseFormattedMessage("&cExpired")), // Expired message Component
                            ),
                        ),
                    )
                    // Add message about items to collect
                    infoLore.add(MessageUtil.parseFormattedMessage("&7&o(Expired - Click to collect &b$itemsToCollect &7items)"))
                } else {
                    infoLore.addAll(
                        MessageUtil.getMessageList(
                            "MyOrders.OrderItemLore.Pending",
                            listOf(
                                MessageReplacement(order.cost.toString()),
                                MessageReplacement(createdDurationText),
                                MessageReplacement(order.status.getComponent()),
                                MessageReplacement(order.itemAmount.toString()),
                                MessageReplacement(expireDurationText),
                            ),
                        ),
                    )
                }
            }

            OrderStatus.CLAIMED -> {
                val deadlineDuration = Duration.between(LocalDateTime.now(), order.timeDeadline ?: LocalDateTime.now())
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
                infoLore.addAll(
                    MessageUtil.getMessageList(
                        "MyOrders.OrderItemLore.Claimed",
                        listOf(
                            MessageReplacement(order.cost.toString()),
                            MessageReplacement(createdDurationText),
                            MessageReplacement(order.status.getComponent()),
                            MessageReplacement(order.itemAmount.toString()),
                            MessageReplacement(deadlineDurationText),
                            MessageReplacement(order.assignee?.let { Bukkit.getOfflinePlayer(it).name } ?: "Unknown"),
                        ),
                    ),
                )
            }

            OrderStatus.COMPLETED -> {
                val completedDuration = Duration.between(order.timeCompleted, LocalDateTime.now())
                val completedDurationText =
                    MessageUtil.getMessage(
                        "General.DatePastTense",
                        listOf(
                            MessageReplacement(completedDuration.toDays().toString()),
                            MessageReplacement(completedDuration.toHoursPart().toString()),
                            MessageReplacement(completedDuration.toMinutesPart().toString()),
                        ),
                    )
                infoLore.addAll(
                    MessageUtil.getMessageList(
                        "MyOrders.OrderItemLore.Completed",
                        listOf(
                            MessageReplacement(order.cost.toString()),
                            MessageReplacement(createdDurationText),
                            MessageReplacement(order.status.getComponent()),
                            MessageReplacement(order.itemAmount.toString()),
                            MessageReplacement(completedDurationText),
                            MessageReplacement(order.assignee?.let { Bukkit.getOfflinePlayer(it).name } ?: "Unknown"),
                        ),
                    ),
                )
            }

            else -> {
                infoLore.add(MessageUtil.toComponent("<reset><red>Status: <white>${order.status}"))
                infoLore.add(MessageUtil.toComponent("<reset>This should not be seen!"))
            }
        }

        if (itemMetaCopy.hasLore()) {
            val itemLore = itemMetaCopy.lore()!!
            itemLore.addAll(infoLore)
            itemMetaCopy.lore(itemLore)
        } else {
            itemMetaCopy.lore(infoLore)
        }

        item.itemMeta = itemMetaCopy

        val button = SGButton(item)

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
            OrderStatus.PENDING -> {
                // If order is expired but has items to collect, allow collection
                if (order.isOrderExpired() && order.itemCompleted > 0 && order.itemsObtained < order.itemCompleted) {
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
                    if (giveOrderItems(order, (event.whoClicked as Player))) {
                        event.whoClicked.closeInventory()
                        event.whoClicked.sendMessage(
                            MessageUtil.getMessage("MyOrders.OrderObtained"),
                        )
                        gui.removeButton(event.slot + getOffset(gui.currentPage, rows))
                        loadOrders(gui.currentPage, event.whoClicked as Player)
                        gui.refreshInventory(event.whoClicked)
                        return
                    }
                    event.whoClicked.sendMessage(
                        MessageUtil.getMessage("MyOrders.OrderObtained"),
                    )
                    return
                }
                // Otherwise, cancel/remove the order
                event.whoClicked.sendMessage(
                    MessageUtil.getMessage("MyOrders.OrderCancelled"),
                )
                gui.removeButton(event.slot + getOffset(gui.currentPage, rows))
                order.removeOrder()
                loadOrders(gui.currentPage, event.whoClicked as Player)
                gui.refreshInventory(event.whoClicked)
            }
            OrderStatus.CLAIMED -> {
                event.whoClicked.sendMessage(
                    MessageUtil.getMessage("MyOrders.OrderCancelled"),
                )
                gui.removeButton(event.slot + getOffset(gui.currentPage, rows))
                order.cancelOrder()
                loadOrders(gui.currentPage, event.whoClicked as Player)
                gui.refreshInventory(event.whoClicked)
            }
            OrderStatus.COMPLETED -> {
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
                if (giveOrderItems(order, (event.whoClicked as Player))) {
                    event.whoClicked.closeInventory()
                    event.whoClicked.sendMessage(
                        MessageUtil.getMessage("MyOrders.OrderAlreadyClaimed"),
                    )
                    gui.removeButton(event.slot + getOffset(gui.currentPage, rows))
                    loadOrders(gui.currentPage, event.whoClicked as Player)
                    gui.refreshInventory(event.whoClicked)
                    return
                }
                event.whoClicked.sendMessage(
                    MessageUtil.getMessage("MyOrders.OrderClaimed"),
                )
            }

            else -> return
        }
    }

    private fun giveOrderItems(
        order: Order,
        player: Player,
    ): Boolean {
        var itemsLeft = order.itemCompleted - order.itemsObtained
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
            val unaddeditems = player.inventory.addItem(item)
            itemsLeft += unaddeditems.values.sumOf { it.amount }
            if (itemsLeft == 0) break
        }
        order.itemsObtained += order.itemCompleted - itemsLeft
        OrderStorage.update(order)
        if (order.itemsObtained == order.itemCompleted) {
            player.sendMessage(
                MessageUtil.getMessage("MyOrders.OrderObtained"),
            )
            player.closeInventory()
            OrderStorage.delete(order)
            return true
        }
        return false
    }

    private fun loadCloseButton(config: org.bukkit.configuration.ConfigurationSection, gui: SGMenu) {
        val closeConfig = config.getConfigurationSection("Items.CloseButton")
        if (closeConfig != null) {
            val closeButton = createButtonFromConfig(closeConfig)
            closeButton.setListener { event ->
                event.whoClicked.closeInventory()
            }
            for (slot in closeConfig.getIntegerList("Slots")) {
                gui.setButton(slot, closeButton)
                gui.stickSlot(slot)
            }
        }
    }

    companion object {
        /**
         * Creates an instance of the MyOrders class, and returns a working gui.
         * @return The gui.
         */
        fun getGUI(player: Player): SGMenu {
            val myOrders = MyOrders(player)
            return myOrders.gui
        }
    }
}
