package net.refractored.aceemployments.container

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import net.refractored.aceemployments.serializers.ItemstackSerializers
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Represents a player's personal item container
 * Items from completed jobs are stored here
 */
@DatabaseTable(tableName = "aceemployments_containers")
data class ItemContainer(
    @DatabaseField(id = true)
    val id: UUID,
    
    @DatabaseField
    val player: UUID,
    
    @DatabaseField(persisterClass = ItemstackSerializers::class)
    val item: ItemStack,
    
    @DatabaseField
    var amount: Int,
) {
    constructor() : this(
        UUID.randomUUID(),
        UUID.randomUUID(),
        org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE),
        0,
    )
}

