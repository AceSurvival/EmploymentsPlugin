package net.refractored.aceemployments

import com.earth2me.essentials.Essentials
import com.samjakob.spigui.SpiGUI
import net.milkbowl.vault.economy.Economy
import net.refractored.aceemployments.commands.*
import net.refractored.aceemployments.commands.framework.CommandFramework
import net.refractored.aceemployments.config.Presets
import net.refractored.aceemployments.database.Database
import net.refractored.aceemployments.listeners.PlayerJoinListener
import net.refractored.aceemployments.mail.Mail
import net.refractored.aceemployments.order.Order
import org.bstats.bukkit.Metrics
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File

/**
 * The main plugin class
 */
class AceEmployments : JavaPlugin() {
    /**
     * The plugin's GUI manager
     */
    lateinit var spiGUI: SpiGUI
        private set

    /**
     * Economy Provider
     */
    lateinit var eco: Economy
        private set

    /**
     * Essentials
     */
    var essentials: Essentials? = null
        private set

    /**
     * Returns true if eco is loaded
     */
    var ecoPlugin: Boolean = false
        private set

//    /**
//     * Returns true if redis is loaded
//     */
//    var redisChat: RedisChat? = null
//        private set

    /**
     * Returns true if ItemsAdder is loaded
     */
    var itemsAdder: Boolean = false
        private set

    /**
     * The command framework
     */
    private lateinit var commandFramework: CommandFramework

    /**
     * The messages configuration
     */
    lateinit var messages: FileConfiguration
        private set

    /**
     * The gui configuration
     */
    lateinit var gui: FileConfiguration
        private set

    /**
     * The preset configuration
     */
    lateinit var presets: FileConfiguration
        private set

    private lateinit var cleanDatabase: BukkitTask

    /**
     * Custom data folder to use plugins/AceMCCore/AceEmployments
     */
    lateinit var customDataFolder: File
        private set

    override fun onEnable() {
        // Set the instance
        instance = this

        // Initialize custom data folder
        val pluginsFolder = File(server.pluginsFolder.parentFile, "plugins")
        val aceMCCoreFolder = File(pluginsFolder, "AceMCCore")
        val aceEmploymentsFolder = File(aceMCCoreFolder, "AceEmployments")
        if (!aceEmploymentsFolder.exists()) {
            aceEmploymentsFolder.mkdirs()
        }
        customDataFolder = aceEmploymentsFolder

        spiGUI = SpiGUI(this)

        val pluginId = 22844
        val metrics: Metrics = Metrics(this, pluginId)

        // Ensure all config files exist and auto-generate if missing
        ensureConfigFile("config.yml")
        ensureConfigFile("messages.yml")
        ensureConfigFile("gui.yml")
        ensureConfigFile("presets.yml")
        
        // Ensure menu files exist
        net.refractored.aceemployments.gui.MenuLoader.ensureMenuFiles()

        // Load config from custom folder
        val configFile = File(customDataFolder, "config.yml")
        if (configFile.exists()) {
            config.load(configFile)
        } else {
            saveDefaultConfig()
        }

        // Load messages config
        messages = YamlConfiguration.loadConfiguration(File(customDataFolder, "messages.yml"))
        // Load gui config
        gui = YamlConfiguration.loadConfiguration(File(customDataFolder, "gui.yml"))
        // Load preset config
        presets = YamlConfiguration.loadConfiguration(File(customDataFolder, "presets.yml"))

        // Load presets
        Presets.refreshPresets()

        // Initialize the order storage (YAML-based)
        net.refractored.aceemployments.storage.OrderStorage.init()

        server.servicesManager.getRegistration(Economy::class.java)?.let {
            eco = it.provider
        } ?: run {
            logger.warning("A economy plugin not found! Disabling plugin.")
            server.pluginManager.disablePlugin(this)
            return
        }

        server.pluginManager.getPlugin("Essentials")?.let {
            essentials = (it as Essentials)
            logger.info("Hooked into Essentials")
        } ?: run {
            if (instance.config.getBoolean("Essentials.UseEssentialsMail") || instance.config.getBoolean("Essentials.UseIgnoreList")) {
                logger.warning("Essentials config options are enabled but Essentials is not found!")
                logger.warning("Please install Essentials or disable these options in the config.yml.")
                logger.warning("https://essentialsx.net/downloads.html")
            }
        }

        server.pluginManager.getPlugin("eco")?.let {
            ecoPlugin = true
            logger.info("Hooked into eco")
        }

        server.pluginManager.getPlugin("ItemsAdder")?.let {
            itemsAdder = true
            logger.info("Hooked into ItemsAdder")
        }

//        server.pluginManager.getPlugin("RedisChat")?.let {
//            redisChat = (it as RedisChat)
//            logger.info("Hooked into RedisChat")
//        }

        // Create command framework
        commandFramework = CommandFramework(this)

        // Register tab completions
        commandFramework.registerTabCompletion("presets") { sender, args ->
            Presets.getPresets().keys.toList()
        }

        commandFramework.registerTabCompletion("materials") { sender, args ->
            // Get the last argument (current input)
            val currentInput = args.lastOrNull()?.lowercase() ?: ""

            val config = instance.config
            val blacklistedMaterials = config.getStringList("Orders.BlacklistedMaterials")
            val additionalBlacklistedMaterials = config.getStringList("Orders.BlacklistedCreateMaterials")
            val blacklist =
                (blacklistedMaterials + additionalBlacklistedMaterials)
                    .map { it.lowercase() }
                    .toSet()

            val materialSuggestions =
                Material.entries
                    .asSequence()
                    .map { it.name.lowercase() }
                    .filterNot { name -> name in blacklist }
                    .toMutableSet()

            val presetSuggestions = Presets.getPresets().keys

            (materialSuggestions + presetSuggestions)
                .filter { it.startsWith(currentInput, ignoreCase = true) }
                .sorted()
                .take(50) // Limit to 50 suggestions for performance
                .toList()
        }

        // Register commands
        commandFramework.register(CreateOrderCommand())
        commandFramework.register(BrowseCommand())
        commandFramework.register(MyJobsCommand())
        commandFramework.register(ReloadCommand())
        commandFramework.register(DelistCommand())
        commandFramework.register(ContributeCommand())
        commandFramework.register(SubscribeCommand())
        commandFramework.register(UnsubscribeCommand())
        commandFramework.register(NotifierCommand())
        commandFramework.register(ContainerCommand())

        // Register listeners
        server.pluginManager.registerEvents(PlayerJoinListener(), this)

        cleanDatabase =
            server.scheduler.runTaskTimer(
                this,
                Runnable {
                    net.refractored.aceemployments.order.Order.updateExpiredOrders()
                    net.refractored.aceemployments.order.Order.updateDeadlineOrders()
                    net.refractored.aceemployments.order.Order.updatePickupDeadline()
                    Mail.purgeMail()
                },
                20L,
                40L,
            )

        logger.info("AceEmployments has been enabled!")
    }

    override fun onDisable() {
        if (this::commandFramework.isInitialized) {
            commandFramework.unregisterAll()
        }
        if (this::cleanDatabase.isInitialized) {
            cleanDatabase.cancel()
        }

        logger.info("AceEmployments has been disabled!")
    }

    /**
     * Reload the plugin configuration
     * This reloads absolutely everything: config, messages, guis, presets, menu files, etc.
     */
    fun reload() {
        logger.info("Reloading AceEmployments plugin...")
        
        // Ensure all config files exist before reloading
        ensureConfigFile("config.yml")
        ensureConfigFile("messages.yml")
        ensureConfigFile("gui.yml")
        ensureConfigFile("presets.yml")
        
        // Ensure menu files exist (will auto-generate if missing)
        net.refractored.aceemployments.gui.MenuLoader.ensureMenuFiles()
        
        // Reload config.yml
        val configFile = File(customDataFolder, "config.yml")
        if (configFile.exists()) {
            config.load(configFile)
            logger.info("Reloaded config.yml")
        } else {
            saveDefaultConfig()
            logger.info("Generated default config.yml")
        }
        
        // Reload messages.yml (lang file)
        messages = YamlConfiguration.loadConfiguration(File(customDataFolder, "messages.yml"))
        logger.info("Reloaded messages.yml")
        
        // Reload gui.yml
        gui = YamlConfiguration.loadConfiguration(File(customDataFolder, "gui.yml"))
        logger.info("Reloaded gui.yml")
        
        // Reload presets.yml
        presets = YamlConfiguration.loadConfiguration(File(customDataFolder, "presets.yml"))
        logger.info("Reloaded presets.yml")
        
        // Refresh presets cache
        Presets.refreshPresets()
        logger.info("Refreshed presets cache")
        
        // Note: OrderStorage doesn't need reloading as orders are persisted to disk
        // and loaded on startup. Reloading would lose any in-memory changes.
        
        logger.info("AceEmployments plugin reloaded successfully!")
    }
    
    /**
     * Ensures a config file exists in the custom data folder, auto-generating it from resources if missing
     * @param fileName The name of the config file to ensure exists
     */
    private fun ensureConfigFile(fileName: String) {
        val configFile = File(customDataFolder, fileName)
        if (!configFile.exists()) {
            getResource(fileName)?.let { input ->
                try {
                    configFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    logger.info("Auto-generated missing config file: $fileName")
                } catch (e: Exception) {
                    logger.warning("Failed to auto-generate config file $fileName: ${e.message}")
                }
            } ?: run {
                logger.warning("Config file $fileName not found in plugin resources, cannot auto-generate")
            }
        }
    }

    companion object {
        /**
         * The plugin's instance
         */
        lateinit var instance: AceEmployments
            private set
    }
}
