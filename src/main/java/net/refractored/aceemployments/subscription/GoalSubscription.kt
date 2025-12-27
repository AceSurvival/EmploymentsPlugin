package net.refractored.aceemployments.subscription

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import net.refractored.aceemployments.serializers.ItemstackSerializers
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Represents a player's subscription to a specific item type
 * When a job is created with this item, the subscriber is notified
 */
@DatabaseTable(tableName = "aceemployments_subscriptions")
data class GoalSubscription(
    @DatabaseField(id = true)
    val id: UUID,
    
    @DatabaseField
    val player: UUID,
    
    @DatabaseField(persisterClass = ItemstackSerializers::class)
    val item: ItemStack,
) {
    constructor() : this(
        UUID.randomUUID(),
        UUID.randomUUID(),
        org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE),
    )
}

