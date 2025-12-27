package net.refractored.aceemployments.gui

import com.samjakob.spigui.buttons.SGButton
import com.samjakob.spigui.menu.SGMenu
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.AMPERSAND_CHAR
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.container.ContainerManager
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class ContainerGUI(player: Player) {
    private val gui: SGMenu = AceEmployments.instance.spiGUI.create(
        LegacyComponentSerializer.legacy(AMPERSAND_CHAR).serialize(
            MessageUtil.toComponent("<green>Item Container")
        ),
        6,
    )

    init {
        loadItems(player)
    }

    private fun loadItems(player: Player) {
        val containers = ContainerManager.getContainer(player)
        
        for ((index, container) in containers.withIndex()) {
            if (index >= 45) break // Max 45 slots
            
            val item = container.item.clone()
            item.amount = container.amount
            
            val meta = item.itemMeta
            val lore = meta.lore() ?: mutableListOf()
            lore.add(MessageUtil.toComponent("<gray>Amount: <white>${container.amount}"))
            lore.add(MessageUtil.toComponent("<green>Click to retrieve"))
            meta.lore(lore)
            item.itemMeta = meta

            val button = SGButton(item)
            button.setListener { event: InventoryClickEvent ->
                if (event.whoClicked is Player) {
                    ContainerManager.removeItems(event.whoClicked as Player, container.id, container.amount)
                    loadItems(event.whoClicked as Player)
                    gui.refreshInventory(event.whoClicked)
                }
            }
            
            gui.setButton(index, button)
        }
    }

    companion object {
        fun getGUI(player: Player): SGMenu {
            return ContainerGUI(player).gui
        }
    }
}

