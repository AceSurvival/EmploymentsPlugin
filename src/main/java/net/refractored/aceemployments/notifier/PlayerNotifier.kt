package net.refractored.aceemployments.notifier

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import java.util.*

/**
 * Represents a player's notification preferences
 */
enum class NotifierType {
    ALL_JOBS,      // Notify for all jobs
    NO_JOBS,       // Don't notify for any jobs
    SUBSCRIPTIONS  // Only notify for subscribed items
}

/**
 * Stores player notification preferences
 */
@DatabaseTable(tableName = "aceemployments_notifiers")
data class PlayerNotifier(
    @DatabaseField(id = true)
    val player: UUID,
    
    @DatabaseField
    var notifierType: NotifierType,
) {
    constructor() : this(
        UUID.randomUUID(),
        NotifierType.ALL_JOBS,
    )
}

