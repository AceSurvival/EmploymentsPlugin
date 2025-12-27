package net.refractored.aceemployments.config

import net.refractored.aceemployments.AceEmployments
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object Presets {
    @JvmStatic
    private val preset = mutableMapOf<String, ItemStack>()

    /**
     * Gets a preset from the loaded presets
     * @param name The name of the preset
     * @return The itemstack for the preset, or null if it does not exist
     */
    @JvmStatic
    fun getPreset(name: String): ItemStack? = preset[name]

    /**
     * Gets a read-only map of all the presets loaded.
     * @return The mutable preset list
     */
    @JvmStatic
    fun getPresets() = preset.toMap()

    /**
     * Create a new preset and adds it to the config & map
     * @param name The name of the preset
     * @param item The itemstack for the preset
     */
    @JvmStatic
    fun createPreset(
        name: String,
        item: ItemStack,
    ) {
        if (preset.containsKey(name)) {
            throw IllegalArgumentException("Preset already exists.")
        }
        if (Material.entries.any { it.name.equals(name, true) }) {
            throw IllegalArgumentException("Presets cannot be the same name as a existing minecraft material! ($name)")
        }
        AceEmployments.instance.presets.set(name, item)
        AceEmployments.instance.presets.save(AceEmployments.instance.customDataFolder.resolve("presets.yml"))
        preset[name] = item
    }

    /**
     * Removes a preset from the config & map
     * @return The itemstack that was created
     * @param name The name of the preset
     */
    @JvmStatic
    fun removePreset(name: String) {
        if (!preset.containsKey(name)) {
            throw IllegalArgumentException("Preset does not exist.")
        }

        AceEmployments.instance.presets.set(name, null)
        AceEmployments.instance.presets.save(AceEmployments.instance.customDataFolder.resolve("presets.yml"))
        preset.remove(name)
    }

    /**
     * Deletes all presets in the map and populates it with the presets in the config.
     */
    @JvmStatic
    fun refreshPresets() {
        preset.clear()
        val config = AceEmployments.instance.presets
        val section = config.getConfigurationSection("")
        val keys = section!!.getKeys(false)
        if (keys.isEmpty()) return
        for (key in keys) {
            if (Material.entries.any { it.name.equals(key, true) }) {
                AceEmployments.instance.logger.severe("Presets cannot be the same name as a existing minecraft material! ($key)")
                continue
            }
            createPreset(key, config.getItemStack(key)!!)
        }
    }
}
