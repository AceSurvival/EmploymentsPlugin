package net.refractored.aceemployments.database

import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.DaoManager
import com.j256.ormlite.field.DataPersisterManager
import com.j256.ormlite.jdbc.JdbcConnectionSource
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource
import com.j256.ormlite.logger.LoggerFactory
import com.j256.ormlite.logger.NullLogBackend.NullLogBackendFactory
import com.j256.ormlite.table.TableUtils
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.container.ItemContainer
import net.refractored.aceemployments.mail.Mail
import net.refractored.aceemployments.notifier.PlayerNotifier
import net.refractored.aceemployments.order.Order
import net.refractored.aceemployments.order.OrderContribution
import net.refractored.aceemployments.serializers.ComponentSerializers
import net.refractored.aceemployments.serializers.ItemstackSerializers
import net.refractored.aceemployments.serializers.LocalDateTimeSerializers
import net.refractored.aceemployments.subscription.GoalSubscription
import java.util.*

/**
 * A static class used for database operations.
 */
class Database {
    companion object {
        /**
         * The connection source for the database.
         */
        @JvmStatic
        lateinit var connectionSource: JdbcConnectionSource
            private set

        /**
         * The order DAO, used for database operations on orders.
         */
        @JvmStatic
        lateinit var orderDao: Dao<Order, UUID>
            private set

        /**
         * The mail DAO, used for database operations on mail.
         */
        @JvmStatic
        lateinit var mailDao: Dao<Mail, UUID>
            private set

        /**
         * The contribution DAO, used for database operations on order contributions.
         */
        @JvmStatic
        lateinit var contributionDao: Dao<OrderContribution, UUID>
            private set

        /**
         * The subscription DAO, used for database operations on goal subscriptions.
         */
        @JvmStatic
        lateinit var subscriptionDao: Dao<GoalSubscription, UUID>
            private set

        /**
         * The notifier DAO, used for database operations on player notifiers.
         */
        @JvmStatic
        lateinit var notifierDao: Dao<PlayerNotifier, UUID>
            private set

        /**
         * The container DAO, used for database operations on item containers.
         */
        @JvmStatic
        lateinit var containerDao: Dao<ItemContainer, UUID>
            private set

        /**
         * Initializes the database with values from the config.
         * This should be called once.
         * Call before any other database operations, and after the config has been loaded.
         */
        @JvmStatic
        fun init() {
            AceEmployments.instance.logger.info("Initializing database...")
            LoggerFactory.setLogBackendFactory(NullLogBackendFactory())

            val dbUrl = AceEmployments.instance.config.getString("Database.url") ?: "file"
            
            connectionSource =
                if (dbUrl.equals("file", true) || 
                    dbUrl == "jdbc:mysql://DATABASE_IP:PORT/DATABASE_NAME" ||
                    dbUrl.isEmpty()) {
                    // Auto-create SQLite database
                    val dbPath = AceEmployments.instance.customDataFolder.toPath().resolve("database.db")
                    AceEmployments.instance.logger.info("Using SQLite database: $dbPath")
                    JdbcPooledConnectionSource(
                        "jdbc:sqlite:$dbPath",
                    )
                } else {
                    // Use configured MySQL database
                    JdbcPooledConnectionSource(
                        dbUrl,
                        AceEmployments.instance.config.getString("Database.user") ?: "",
                        AceEmployments.instance.config.getString("Database.password") ?: "",
                    )
                }

            @Suppress("UNCHECKED_CAST")
            orderDao = DaoManager.createDao(connectionSource, Order::class.java) as Dao<Order, UUID>

            TableUtils.createTableIfNotExists(connectionSource, Order::class.java)

            @Suppress("UNCHECKED_CAST")
            mailDao = DaoManager.createDao(connectionSource, Mail::class.java) as Dao<Mail, UUID>

            TableUtils.createTableIfNotExists(connectionSource, Mail::class.java)

            @Suppress("UNCHECKED_CAST")
            contributionDao = DaoManager.createDao(connectionSource, OrderContribution::class.java) as Dao<OrderContribution, UUID>

            TableUtils.createTableIfNotExists(connectionSource, OrderContribution::class.java)

            @Suppress("UNCHECKED_CAST")
            subscriptionDao = DaoManager.createDao(connectionSource, GoalSubscription::class.java) as Dao<GoalSubscription, UUID>

            TableUtils.createTableIfNotExists(connectionSource, GoalSubscription::class.java)

            @Suppress("UNCHECKED_CAST")
            notifierDao = DaoManager.createDao(connectionSource, PlayerNotifier::class.java) as Dao<PlayerNotifier, UUID>

            TableUtils.createTableIfNotExists(connectionSource, PlayerNotifier::class.java)

            @Suppress("UNCHECKED_CAST")
            containerDao = DaoManager.createDao(connectionSource, ItemContainer::class.java) as Dao<ItemContainer, UUID>

            TableUtils.createTableIfNotExists(connectionSource, ItemContainer::class.java)

            DataPersisterManager.registerDataPersisters(ItemstackSerializers.getSingleton())

            DataPersisterManager.registerDataPersisters(ComponentSerializers.getSingleton())

            DataPersisterManager.registerDataPersisters(LocalDateTimeSerializers.getSingleton())

            System.setProperty("com.j256.ormlite.logger.type", "LOCAL")
            System.setProperty("com.j256.ormlite.logger.level", "ERROR")
            System.setProperty(LoggerFactory.LOG_TYPE_SYSTEM_PROPERTY, "LOCAL")
            AceEmployments.instance.logger.info("Database initialized")
        }
    }
}
