package net.refractored.aceemployments.gui

import com.samjakob.spigui.buttons.SGButton
import com.samjakob.spigui.menu.SGMenu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File

object MenuLoader {
    /**
     * Loads a menu configuration from a YAML file
     * @param menuFileName The name of the menu file (e.g., "jobs_list_menu.yml")
     * @return The loaded configuration, or null if the file doesn't exist
     */
    fun loadMenuConfig(menuFileName: String): YamlConfiguration? {
        val menusFolder = File(AceEmployments.instance.customDataFolder, "Menus")
        val menuFile = File(menusFolder, menuFileName)
        
        if (!menuFile.exists()) {
            AceEmployments.instance.logger.warning("Menu file not found: $menuFileName")
            return null
        }
        
        return YamlConfiguration.loadConfiguration(menuFile)
    }
    
    /**
     * Gets the title for a menu from messages.yml
     * @param menuName The menu name key (e.g., "JobsList")
     * @return The title string (with %current% and %max% placeholders)
     */
    fun getMenuTitle(menuName: String): String {
        val titleKey = "GUI.Names.$menuName"
        return AceEmployments.instance.messages.getString(titleKey) ?: "Menu"
    }
    
    /**
     * Loads cosmetic items from a menu configuration
     * @param menuConfig The menu configuration
     * @param gui The GUI menu to add items to
     * @param pageCount The number of pages
     * @param menuContext The menu context (e.g., "JobsList", "YourJobs") for loading default items
     */
    fun loadMenuItems(menuConfig: YamlConfiguration, gui: SGMenu, pageCount: Int, menuContext: String = "JobsList") {
        val itemsSection = menuConfig.getConfigurationSection("items") ?: return
        
        itemsSection.getKeys(false).forEach { itemKey ->
            val itemSection = itemsSection.getConfigurationSection(itemKey) ?: return@forEach
            
            val materialStr = itemSection.getString("material") ?: return@forEach
            val material = parseMaterial(materialStr) ?: return@forEach
            
            val amount = itemSection.getInt("amount", 1)
            val modelData = itemSection.getInt("model_data", 0)
            
            // Check if this is a default item - if it doesn't have a "name" field, it's default
            val hasName = itemSection.contains("name")
            val hasLore = itemSection.contains("lore")
            
            // Get name and lore from messages.yml if it's a default item, otherwise from menu config
            val name: String
            val lore: List<String>
            
            if (!hasName || !hasLore) {
                // This is a default item - load from messages.yml
                val menuName = getMenuNameFromKey(itemKey)
                val nameKey = "GUI.$menuContext.$menuName.Name"
                val loreKey = "GUI.$menuContext.$menuName.Lore"
                name = AceEmployments.instance.messages.getString(nameKey) ?: " "
                lore = AceEmployments.instance.messages.getStringList(loreKey)
            } else {
                name = itemSection.getString("name") ?: " "
                lore = itemSection.getStringList("lore")
            }
            
            val glowing = itemSection.getBoolean("glowing", false)
            val slots = itemSection.getIntegerList("slots")
            
            val item = createItemStack(material, amount, modelData, name, lore, glowing)
            val button = SGButton(item)
            
            // Handle special buttons
            when (itemKey.lowercase()) {
                "next-page" -> {
                    button.setListener { event -> gui.nextPage(event.whoClicked) }
                }
                "previous-page" -> {
                    button.setListener { event -> gui.previousPage(event.whoClicked) }
                }
                "back-arrow" -> {
                    button.setListener { event -> event.whoClicked.closeInventory() }
                }
            }
            
            // Set button in all slots across all pages
            for (page in 0 until pageCount) {
                val offset = page * (gui.inventory.size / 9) * 9
                slots.forEach { slot ->
                    val actualSlot = slot + offset
                    if (actualSlot < gui.inventory.size) {
                        gui.setButton(actualSlot, button)
                        gui.stickSlot(actualSlot)
                    }
                }
            }
        }
    }
    
    /**
     * Parses a material string, handling player heads with texture
     */
    private fun parseMaterial(materialStr: String): Material? {
        // Handle player head format: PLAYER_HEAD:texture
        val parts = materialStr.split(":")
        val materialName = parts[0]
        
        return try {
            Material.valueOf(materialName)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    
    /**
     * Creates an ItemStack from the given parameters
     */
    private fun createItemStack(
        material: Material,
        amount: Int,
        modelData: Int,
        name: String,
        lore: List<String>,
        glowing: Boolean
    ): ItemStack {
        val item = ItemStack(material, amount)
        if (item.type == Material.AIR) return item
        
        val meta = item.itemMeta ?: return item
        meta.setCustomModelData(modelData)
        
        val nameComponent = MessageUtil.parseFormattedMessage(name)
            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
        meta.displayName(nameComponent)
        
        val loreComponents = lore.map { line ->
            MessageUtil.parseFormattedMessage(line)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
        }
        meta.lore(loreComponents)
        
        if (glowing) {
            meta.setEnchantmentGlintOverride(true)
        }
        
        item.itemMeta = meta
        return item
    }
    
    /**
     * Gets the menu name key from an item key
     */
    private fun getMenuNameFromKey(itemKey: String): String {
        // Map item keys to message keys
        return when (itemKey.lowercase()) {
            "information" -> "Information"
            "back-arrow", "back" -> "Back"
            "next-page" -> "NextPage"
            "previous-page" -> "PreviousPage"
            "your-offers" -> "MyJobs"
            "reissue-job" -> "ReissueJob"
            "cancel-job" -> "CancelJob"
            "claim-items" -> "ClaimItems"
            "submit-items" -> "SubmitItems"
            "current-job" -> "CurrentJob"
            else -> itemKey.replace("-", "").replaceFirstChar { it.uppercaseChar() }
        }
    }
    
    /**
     * Creates an ItemStack from a menu section
     */
    fun createItemFromMenuSection(itemSection: ConfigurationSection, itemKey: String, menuContext: String = "JobsList"): ItemStack {
        val materialStr = itemSection.getString("material") ?: return ItemStack(Material.AIR)
        val material = parseMaterial(materialStr) ?: return ItemStack(Material.AIR)
        val amount = itemSection.getInt("amount", 1)
        val modelData = itemSection.getInt("model_data", 0)
        val glowing = itemSection.getBoolean("glowing", false)
        
        // Check if this is a default item - if it doesn't have a "name" field, it's default
        val hasName = itemSection.contains("name")
        val hasLore = itemSection.contains("lore")
        
        val name: String
        val lore: List<String>
        
        if (!hasName || !hasLore) {
            // This is a default item - load from messages.yml
            val menuName = getMenuNameFromKey(itemKey)
            val nameKey = "GUI.$menuContext.$menuName.Name"
            val loreKey = "GUI.$menuContext.$menuName.Lore"
            name = AceEmployments.instance.messages.getString(nameKey) ?: " "
            lore = AceEmployments.instance.messages.getStringList(loreKey)
        } else {
            name = itemSection.getString("name") ?: " "
            lore = itemSection.getStringList("lore")
        }
        
        return createItemStack(material, amount, modelData, name, lore, glowing)
    }
    
    /**
     * Ensures menu files exist in the Menus folder, auto-generating from resources if missing
     */
    fun ensureMenuFiles() {
        val menusFolder = File(AceEmployments.instance.customDataFolder, "Menus")
        if (!menusFolder.exists()) {
            menusFolder.mkdirs()
        }
        
        val menuFiles = listOf(
            "jobs_list_menu.yml",
            "your_jobs_menu.yml",
            "manage_job_offer_menu.yml",
            "fulfill_job_offer_menu.yml",
            "job_offer_inventory.yml"
        )
        
        menuFiles.forEach { menuFile ->
            val targetFile = File(menusFolder, menuFile)
            if (!targetFile.exists()) {
                AceEmployments.instance.getResource(menuFile)?.let { input ->
                    try {
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        AceEmployments.instance.logger.info("Auto-generated menu file: $menuFile")
                    } catch (e: Exception) {
                        AceEmployments.instance.logger.warning("Failed to auto-generate menu file $menuFile: ${e.message}")
                    }
                } ?: run {
                    AceEmployments.instance.logger.warning("Menu file $menuFile not found in plugin resources")
                }
            }
        }
    }
}

