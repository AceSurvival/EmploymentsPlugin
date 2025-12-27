package net.refractored.aceemployments.gui

import com.samjakob.spigui.buttons.SGButton
import com.samjakob.spigui.menu.SGMenu
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.AMPERSAND_CHAR
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.storage.OrderStorage
import net.refractored.aceemployments.gui.GuiHelper.createButtonFromConfig
import net.refractored.aceemployments.gui.GuiHelper.loadCosmeticItems
import net.refractored.aceemployments.gui.GuiHelper.loadNavButtons
import net.refractored.aceemployments.order.Order
import net.refractored.aceemployments.order.Order.Companion.getMaxOrdersAccepted
import net.refractored.aceemployments.order.OrderStatus
import net.kyori.adventure.text.Component
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.math.ceil

class AllOrders {
    private val config = AceEmployments.instance.gui.getConfigurationSection("AllOrders")!!
    private val menuConfig = MenuLoader.loadMenuConfig("jobs_list_menu.yml")

    private val rows = config.getInt("Rows", 6)

    private val orderSlots: List<Int> = config.getIntegerList("OrderSlots")

    private var pageCount: Int = ceil(OrderStorage.getPendingOrders().size.toDouble() / orderSlots.count()).toInt().coerceAtLeast(1)

    val gui: SGMenu =
        AceEmployments.instance.spiGUI.create(
            // Me when no component support :((((
            LegacyComponentSerializer.legacy(AMPERSAND_CHAR).serialize(
                MessageUtil.toComponent(
                    MenuLoader.getMenuTitle("JobsList").replace("%current%", "1").replace("%max%", pageCount.toString())
                ),
            ),
            AceEmployments.instance.gui.getInt("AllOrders.Rows", 6),
        )

    init {
        gui.setOnPageChange { inventory ->
            inventory.clearAllButStickiedSlots()
            loadOrders(inventory.currentPage)
        }

        // Load menu items from menu.yml if available, otherwise fall back to gui.yml
        if (menuConfig != null) {
            MenuLoader.loadMenuItems(menuConfig, gui, pageCount, "JobsList")
            loadSpecialButtonsFromMenu(menuConfig, gui)
        } else {
            loadNavButtons(config, gui, pageCount)
            loadCosmeticItems(config, gui, pageCount)
            loadSpecialButtons(config, gui)
        }
        loadOrders(0)
    }
    
    private fun loadSpecialButtonsFromMenu(menuConfig: org.bukkit.configuration.file.YamlConfiguration, gui: SGMenu) {
        val itemsSection = menuConfig.getConfigurationSection("items") ?: return
        
        // Handle back-arrow button
        val backArrowSection = itemsSection.getConfigurationSection("back-arrow")
        if (backArrowSection != null) {
            val slots = backArrowSection.getIntegerList("slots")
            for (page in 0 until pageCount) {
                val offset = GuiHelper.getOffset(page, rows)
                slots.forEach { slot ->
                    val actualSlot = slot + offset
                    if (actualSlot < gui.inventory.size) {
                        val button = SGButton(MenuLoader.createItemFromMenuSection(backArrowSection, "back-arrow"))
                        button.setListener { event -> event.whoClicked.closeInventory() }
                        gui.setButton(actualSlot, button)
                        gui.stickSlot(actualSlot)
                    }
                }
            }
        }
        
        // Handle your-offers button
        val yourOffersSection = itemsSection.getConfigurationSection("your-offers")
        if (yourOffersSection != null) {
            val slots = yourOffersSection.getIntegerList("slots")
            for (page in 0 until pageCount) {
                val offset = GuiHelper.getOffset(page, rows)
                slots.forEach { slot ->
                    val actualSlot = slot + offset
                    if (actualSlot < gui.inventory.size) {
                        val button = SGButton(MenuLoader.createItemFromMenuSection(yourOffersSection, "your-offers"))
                        button.setListener { event ->
                            if (event.whoClicked is Player) {
                                event.whoClicked.closeInventory()
                                event.whoClicked.openInventory(MyOrders.getGUI(event.whoClicked as Player).inventory)
                            }
                        }
                        gui.setButton(actualSlot, button)
                        gui.stickSlot(actualSlot)
                    }
                }
            }
        }
    }

    private fun loadSpecialButtons(config: org.bukkit.configuration.ConfigurationSection, gui: SGMenu) {
        // Close button
        val closeConfig = config.getConfigurationSection("Items.CloseButton")
        if (closeConfig != null) {
            val closeButton = GuiHelper.createButtonFromConfig(closeConfig)
            closeButton.setListener { event ->
                event.whoClicked.closeInventory()
            }
            for (slot in closeConfig.getIntegerList("Slots")) {
                gui.setButton(slot, closeButton)
                gui.stickSlot(slot)
            }
        }

        // My Offers button
        val myOffersConfig = config.getConfigurationSection("Items.MyOffersButton")
        if (myOffersConfig != null) {
            val myOffersButton = GuiHelper.createButtonFromConfig(myOffersConfig)
            myOffersButton.setListener { event ->
                if (event.whoClicked is Player) {
                    event.whoClicked.closeInventory()
                    event.whoClicked.openInventory(MyOrders.getGUI(event.whoClicked as Player).inventory)
                }
            }
            for (slot in myOffersConfig.getIntegerList("Slots")) {
                gui.setButton(slot, myOffersButton)
                gui.stickSlot(slot)
            }
        }
    }

    /**
     * Clears all non-stickied slots, and loads the orders for the requested page.
     * @param page The page to load orders for.
     */
    private fun loadOrders(page: Int) {
        gui.clearAllButStickiedSlots()
        val orders = Order.getPendingOrders(orderSlots.count(), page * orderSlots.count())
        for ((index, slot) in orderSlots.withIndex()) {
            val button: SGButton = orders.getOrNull(index)?.let { getOrderButton(it) } ?: GuiHelper.getFallbackButton(config)
            gui.setButton(slot + GuiHelper.getOffset(page, rows), button)
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

        // Format order display to match screenshot
        val pricePerItem = order.cost / order.itemAmount
        val statusText = if (order.status == OrderStatus.PENDING) {
            MessageUtil.toComponent("<green>Not Started")
        } else {
            MessageUtil.toComponent("<red>${order.status.name}")
        }
        
        // Serialize Components to MiniMessage strings for interpolation
        val statusTextStr = MessageUtil.toMiniMessage(statusText)
        val expireDurationTextStr = MessageUtil.toMiniMessage(expireDurationText)
        val itemDisplayNameStr = MessageUtil.toMiniMessage(item.displayName())
        
        val orderItemLore = mutableListOf<Component>()
        orderItemLore.add(MessageUtil.toComponent("<green>Job Offer for: <white>${order.getOwner().name ?: "Unknown"} <reset>| $statusTextStr"))
        orderItemLore.add(MessageUtil.toComponent(""))
        orderItemLore.add(MessageUtil.toComponent("<white>>> Requested Item: <aqua>${order.itemAmount}x $itemDisplayNameStr"))
        orderItemLore.add(MessageUtil.toComponent("<white>>> Job Payment: <green>$${String.format("%.2f", order.cost)} <gray>($${String.format("%.2f", pricePerItem)} per item)"))
        orderItemLore.add(MessageUtil.toComponent("<white>>> Items Fulfilled: <yellow>${order.itemCompleted}/${order.itemAmount}"))
        orderItemLore.add(MessageUtil.toComponent("<white>>> Job Expires in: <green>$expireDurationTextStr"))
        orderItemLore.add(MessageUtil.toComponent(""))
        orderItemLore.add(MessageUtil.toComponent("<white>• Click to contribute to this offer."))

        if (itemMetaCopy.hasLore()) {
            val itemLore = itemMetaCopy.lore()!!
            itemLore.addAll(orderItemLore)
            itemMetaCopy.lore(itemLore)
        } else {
            itemMetaCopy.lore(orderItemLore)
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
        if (order.user == event.whoClicked.uniqueId) {
            event.whoClicked.closeInventory()
            event.whoClicked.sendMessage(
                MessageUtil.getMessage("General.CannotAcceptOwnOrder"),
            )
            return
        }
        if (order.status != OrderStatus.PENDING) {
            event.whoClicked.closeInventory()
            event.whoClicked.sendMessage(
                MessageUtil.getMessage("General.OrderAlreadyClaimed"),
            )
            return
        }
        if (order.isOrderExpired()) {
            event.whoClicked.closeInventory()
            event.whoClicked.sendMessage(
                MessageUtil.getMessage("General.OrderExpired"),
            )
            return
        }
        AceEmployments.instance.essentials?.let {
            if (AceEmployments.instance.config.getBoolean("Essentials.UseIgnoreList")) {
                val player =
                    it.users.load(
                        event.whoClicked.uniqueId,
                    )
                val owner =
                    it.users.load(
                        Bukkit.getOfflinePlayer(order.user).uniqueId,
                    )
                if (owner.isIgnoredPlayer(player) || player.isIgnoredPlayer(owner)) {
                    event.whoClicked.closeInventory()
                    event.whoClicked.sendMessage(
                        MessageUtil.getMessage("General.Ignored"),
                    )
                    return
                }
            }
        }
        val orders = OrderStorage.getOrdersByStatus(OrderStatus.CLAIMED)
            .filter { it.assignee == event.whoClicked.uniqueId }
        val maxOrdersAccepted = getMaxOrdersAccepted(event.whoClicked as Player)
        if (orders.count() > maxOrdersAccepted) {
            event.whoClicked.closeInventory()
            event.whoClicked.sendMessage(
                MessageUtil.getMessage(
                    "AllOrders.MaxOrdersAccepted",
                    listOf(
                        MessageReplacement(
                            "$maxOrdersAccepted",
                        ),
                    ),
                ),
            )
        }

        order.acceptOrder(event.whoClicked as Player)
        event.whoClicked.closeInventory()
    }

    companion object {
        /**
         * Creates an instance of the AllOrders class, and returns a working gui.
         * @return The gui.
         */
        fun getGUI(): SGMenu {
            val allOrders = AllOrders()
            return allOrders.gui
        }
    }
}
