package net.refractored.aceemployments.container

import com.j256.ormlite.stmt.QueryBuilder
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.database.Database.Companion.containerDao
import net.refractored.aceemployments.order.Order
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

object ContainerManager {
    fun addItems(player: Player, order: Order, amount: Int) {
        val item = order.item.clone()
        item.amount = amount

        // Try to add to inventory first
        val remaining = player.inventory.addItem(item)
        
        // Add remaining items to container
        for (entry in remaining.entries) {
            val itemStack: ItemStack = entry.value ?: continue
            val count: Int = entry.value.amount
            
            // Find existing container items for this player and match by item type
            val allContainers = getContainer(player)
            val itemToMatch = itemStack.clone()
            itemToMatch.amount = 1
            val existing = allContainers.firstOrNull { container ->
                container.item.isSimilar(itemToMatch)
            }
            
            if (existing != null) {
                existing.amount += count
                containerDao.update(existing)
            } else {
                val containerItem = itemStack.clone()
                containerItem.amount = 1
                val container = ItemContainer(
                    UUID.randomUUID(),
                    player.uniqueId,
                    containerItem,
                    count,
                )
                containerDao.create(container)
            }
        }
    }

    fun getContainer(player: Player): List<ItemContainer> {
        val queryBuilder: QueryBuilder<ItemContainer, UUID> = containerDao.queryBuilder()
        queryBuilder.where().eq("player", player.uniqueId)
        return containerDao.query(queryBuilder.prepare())
    }

    fun removeItems(player: Player, containerId: UUID, amount: Int): Boolean {
        val container = containerDao.queryForId(containerId) ?: return false
        if (container.player != player.uniqueId) return false
        
        val toRemove = minOf(amount, container.amount)
        container.amount -= toRemove
        
        if (container.amount <= 0) {
            containerDao.delete(container)
        } else {
            containerDao.update(container)
        }
        
        val item = container.item.clone()
        item.amount = toRemove
        player.inventory.addItem(item)
        
        return true
    }
}

