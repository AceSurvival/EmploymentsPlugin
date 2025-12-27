package net.refractored.aceemployments.storage

import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.order.Order
import net.refractored.aceemployments.order.OrderStatus
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages order storage using YAML files instead of database
 */
object OrderStorage {
    private val orders = ConcurrentHashMap<UUID, Order>()
    private var dataFile: File? = null
    
    /**
     * Initialize the storage system
     */
    fun init() {
        val dataFolder = File(AceEmployments.instance.customDataFolder, "Data")
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        dataFile = File(dataFolder, "jobsdata.yml")
        loadOrders()
        AceEmployments.instance.logger.info("Loaded ${orders.size} orders from YAML storage")
    }
    
    /**
     * Load all orders from the YAML file
     */
    private fun loadOrders() {
        val file = dataFile ?: return
        if (!file.exists()) {
            return
        }
        
        val config = YamlConfiguration.loadConfiguration(file)
        orders.clear()
        
        for (key in config.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                val section = config.getConfigurationSection(key) ?: continue
                
                val order = loadOrderFromSection(uuid, section)
                if (order != null) {
                    orders[uuid] = order
                }
            } catch (e: Exception) {
                AceEmployments.instance.logger.warning("Failed to load order $key: ${e.message}")
            }
        }
    }
    
    /**
     * Load a single order from a configuration section
     */
    private fun loadOrderFromSection(uuid: UUID, section: org.bukkit.configuration.ConfigurationSection): Order? {
        return try {
            val owner = UUID.fromString(section.getString("owner") ?: return null)
            val itemstack = section.getItemStack("itemstack") ?: return null
            val amount = section.getInt("amount", 1)
            val payment = section.getDouble("payment", 0.0)
            val expire = section.getLong("expire", 0)
            val claimed = section.getLong("claimed", 0)
            val assigneeStr = section.getString("assignee")
            val assignee = if (assigneeStr != null && assigneeStr != "null") UUID.fromString(assigneeStr) else null
            val statusStr = section.getString("status") ?: "PENDING"
            val status = try {
                OrderStatus.valueOf(statusStr)
            } catch (e: Exception) {
                OrderStatus.PENDING
            }
            val itemCompleted = section.getInt("itemCompleted", 0)
            val itemsReturned = section.getInt("itemsReturned", 0)
            val itemsObtained = section.getInt("itemsObtained", 0)
            val deadline = section.getLong("deadline", 0)
            val completed = section.getLong("completed", 0)
            val pickup = section.getLong("pickup", 0)
            
            // Use expire time as created time if no claimed time, otherwise use current time
            val timeCreated = if (expire > 0) {
                // Estimate created time from expire (assuming default expiration)
                java.time.Instant.ofEpochMilli(expire).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    .minusHours(7 * 24) // Default 7 days expiration
            } else {
                java.time.LocalDateTime.now()
            }
            val timeExpires = if (expire > 0) {
                java.time.Instant.ofEpochMilli(expire).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            } else {
                java.time.LocalDateTime.now().plusDays(7)
            }
            val timeClaimed = if (claimed > 0) java.time.Instant.ofEpochMilli(claimed).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() else null
            val timeDeadline = if (deadline > 0) java.time.Instant.ofEpochMilli(deadline).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() else null
            val timeCompleted = if (completed > 0) java.time.Instant.ofEpochMilli(completed).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() else null
            val timePickup = if (pickup > 0) java.time.Instant.ofEpochMilli(pickup).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() else null
            
            Order(
                uuid,
                payment,
                owner,
                assignee,
                timeCreated,
                timeExpires,
                timeClaimed,
                timeDeadline,
                timeCompleted,
                timePickup,
                status,
                itemstack,
                amount,
                itemCompleted,
                itemsReturned,
                itemsObtained
            )
        } catch (e: Exception) {
            AceEmployments.instance.logger.warning("Failed to parse order $uuid: ${e.message}")
            null
        }
    }
    
    /**
     * Save all orders to the YAML file
     */
    fun saveOrders() {
        val file = dataFile ?: return
        val config = YamlConfiguration()
        
        for ((uuid, order) in orders) {
            val section = config.createSection(uuid.toString())
            saveOrderToSection(order, section)
        }
        
        try {
            config.save(file)
        } catch (e: Exception) {
            AceEmployments.instance.logger.severe("Failed to save orders: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Save a single order to a configuration section
     * Format matches: owner, itemstack, amount, payment, expire, claimed, contributions
     */
    private fun saveOrderToSection(order: Order, section: org.bukkit.configuration.ConfigurationSection) {
        section.set("owner", order.user.toString())
        section.set("itemstack", order.item)
        section.set("amount", order.itemAmount)
        section.set("payment", order.cost)
        section.set("expire", order.timeExpires.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        section.set("claimed", order.timeClaimed?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0)
        
        // Save contributions in format: "uuid,amount"
        val contributions = mutableListOf<String>()
        // TODO: Load contributions from OrderContribution storage if needed
        section.set("contributions", contributions)
    }
    
    /**
     * Create a new order
     */
    fun create(order: Order) {
        orders[order.id] = order
        saveOrders()
    }
    
    /**
     * Update an existing order
     */
    fun update(order: Order) {
        orders[order.id] = order
        saveOrders()
    }
    
    /**
     * Delete an order
     */
    fun delete(order: Order) {
        orders.remove(order.id)
        saveOrders()
    }
    
    /**
     * Get an order by UUID
     */
    fun getById(uuid: UUID): Order? = orders[uuid]
    
    /**
     * Get all orders
     */
    fun getAll(): Collection<Order> = orders.values
    
    /**
     * Get all pending orders (excluding expired ones)
     */
    fun getPendingOrders(): List<Order> {
        return orders.values.filter { 
            it.status == OrderStatus.PENDING && !it.isOrderExpired()
        }
            .sortedByDescending { it.timeCreated }
    }
    
    /**
     * Get pending orders with pagination (excluding expired ones)
     */
    fun getPendingOrders(limit: Int, offset: Int): List<Order> {
        return getPendingOrders().drop(offset).take(limit)
    }
    
    /**
     * Get orders created by a player
     */
    fun getPlayerCreatedOrders(limit: Int, offset: Int, playerUUID: UUID): List<Order> {
        return orders.values.filter { it.user == playerUUID }
            .sortedByDescending { it.timeCreated }
            .drop(offset)
            .take(limit)
    }
    
    /**
     * Get orders accepted by a player
     */
    fun getPlayerAcceptedOrders(limit: Int, offset: Int, playerUUID: UUID): List<Order> {
        return orders.values.filter { 
            it.assignee == playerUUID && 
            (it.status == OrderStatus.CLAIMED || it.status == OrderStatus.INCOMPLETE || it.status == OrderStatus.CANCELLED)
        }
            .sortedByDescending { it.timeCreated }
            .drop(offset)
            .take(limit)
    }
    
    /**
     * Get orders by status
     */
    fun getOrdersByStatus(status: OrderStatus): List<Order> {
        return orders.values.filter { it.status == status }
    }
    
    /**
     * Count orders by status
     */
    fun countByStatus(status: OrderStatus): Int {
        return orders.values.count { it.status == status }
    }
    
    /**
     * Count all orders
     */
    fun countAll(): Long = orders.size.toLong()
}

