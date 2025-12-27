package net.refractored.aceemployments.order

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import net.refractored.aceemployments.serializers.LocalDateTimeSerializers
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.util.*

/**
 * Represents a contribution to an order by a player
 * Allows multiple players to contribute to the same order
 */
@DatabaseTable(tableName = "aceemployments_contributions")
data class OrderContribution(
    @DatabaseField(id = true)
    val id: UUID,
    
    @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = "order_id")
    val order: Order,
    
    @DatabaseField
    val contributor: UUID,
    
    @DatabaseField
    var amountContributed: Int,
    
    @DatabaseField
    var paymentReceived: Double,
    
    @DatabaseField(persisterClass = LocalDateTimeSerializers::class)
    val timeContributed: LocalDateTime,
) {
    // Default constructor removed - Order is now a data class without default constructor
    // Contributions are stored as part of the order in YAML format
    
    fun getContributorPlayer(): org.bukkit.OfflinePlayer = 
        org.bukkit.Bukkit.getOfflinePlayer(contributor)
}

