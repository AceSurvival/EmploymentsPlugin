package net.refractored.aceemployments.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.refractored.aceemployments.AceEmployments
import java.util.regex.Pattern

class MessageUtil {
    companion object {
        /**
         * Converts a string with various format types to a Component
         * Supports: MiniMessage, Legacy (&), Regular hex (#RRGGBB), and &# hex (&#RRGGBB)
         */
        fun parseFormattedMessage(message: String): Component {
            var processed = message
            
            // First, check if it contains legacy color codes (&a, &b, etc.)
            val hasLegacyCodes = processed.contains(Regex("&[0-9a-fk-orA-FK-OR]"))
            
            if (hasLegacyCodes) {
                // Convert legacy codes to Component directly
                // Use legacyAmpersand() to handle & codes instead of § codes
                return LegacyComponentSerializer.legacyAmpersand().deserialize(processed)
            }
            
            // Convert &# hex format to MiniMessage hex format
            // Pattern: &#RRGGBB or &#RGB (must be at word boundary or start/end)
            processed = processed.replace(Regex("&#([0-9A-Fa-f]{6})"), "<#$1>")
            processed = processed.replace(Regex("&#([0-9A-Fa-f]{3})(?![0-9A-Fa-f])")) { matchResult ->
                val hex = matchResult.groupValues[1]
                // Expand short hex to full hex (RGB -> RRGGBB)
                val expanded = hex.map { "$it$it" }.joinToString("")
                "<#$expanded>"
            }
            
            // Convert regular hex format to MiniMessage hex format
            // Pattern: #RRGGBB or #RGB (not already in tags, and not part of a word)
            // Use word boundary to ensure we don't match hex codes in the middle of words
            processed = processed.replace(Regex("(?<!<)#([0-9A-Fa-f]{6})(?![0-9A-Fa-f>])"), "<#$1>")
            processed = processed.replace(Regex("(?<!<)#([0-9A-Fa-f]{3})(?![0-9A-Fa-f>])")) { matchResult ->
                val hex = matchResult.groupValues[1]
                val expanded = hex.map { "$it$it" }.joinToString("")
                "<#$expanded>"
            }
            
            // Try to parse as MiniMessage
            return try {
                MiniMessage.miniMessage().deserialize(processed)
            } catch (e: Exception) {
                // If parsing fails, treat as plain text
                Component.text(processed)
            }
        }
        
        fun toComponent(miniMessage: String): Component = MiniMessage.miniMessage().deserialize(miniMessage)
        
        /**
         * Serialize a Component to a MiniMessage string for use in string interpolation
         */
        fun toMiniMessage(component: Component): String = MiniMessage.miniMessage().serialize(component)
        
        /**
         * Extract plain text content from a Component, ignoring formatting and hover events
         * Useful for displaying item names in legacy-coded messages
         */
        fun toPlainText(component: Component): String {
            // Use legacy serializer to get readable text, then strip color codes
            val legacyText = LegacyComponentSerializer.legacySection().serialize(component)
            // Remove all legacy color codes (§a, §b, etc.) to get plain text
            return legacyText.replace(Regex("§[0-9a-fk-orA-FK-OR]"), "")
        }

        fun getMessage(key: String): Component = parseFormattedMessage(AceEmployments.instance.messages.getString(key) ?: (key))

        fun getMessageUnformatted(key: String): String = AceEmployments.instance.messages.getString(key) ?: (key)

        fun getMessageList(
            key: String,
            replacements: List<MessageReplacement>,
        ): List<Component> {
            var replacedMessage = getMessageUnformatted(key)
            
            // Convert \n to actual newlines
            replacedMessage = replacedMessage.replace("\\n", "\n")
            
            // Check if message contains legacy codes before replacements
            val hasLegacyCodes = replacedMessage.contains(Regex("&[0-9a-fk-orA-FK-OR]"))

            for ((index, replacement) in replacements.withIndex()) {
                if (replacement.string != null) {
                    replacedMessage = replacedMessage.replace("%$index", replacement.string)
                } else if (replacement.component != null) {
                    // If message has legacy codes, serialize Component to legacy format with & codes
                    // Otherwise, serialize to MiniMessage
                    val serialized = if (hasLegacyCodes) {
                        // Use legacyAmpersand() to serialize with & codes instead of § codes
                        LegacyComponentSerializer.legacyAmpersand().serialize(replacement.component)
                    } else {
                        MiniMessage.miniMessage().serialize(replacement.component)
                    }
                    replacedMessage = replacedMessage.replace("%$index", serialized)
                }
            }

            return replacedMessage.lines().map { line ->
                parseFormattedMessage(line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
            }
        }

        fun replaceMessage(
            miniMessage: String,
            replacements: List<MessageReplacement>,
        ): Component {
            var replacedMessage = miniMessage

            for ((index, replacement) in replacements.withIndex()) {
                if (replacement.string != null) {
                    replacedMessage = replacedMessage.replace("%$index", replacement.string)
                } else if (replacement.component != null) {
                    replacedMessage =
                        replacedMessage.replace(
                            "%$index",
                            MiniMessage
                                .miniMessage()
                                .serialize(replacement.component),
                        )
                }
            }

            return parseFormattedMessage(replacedMessage)
        }

        fun getMessage(
            key: String,
            replacements: List<MessageReplacement>,
        ): Component {
            var replacedMessage = getMessageUnformatted(key)
            
            // Check if message contains legacy codes before replacements
            val hasLegacyCodes = replacedMessage.contains(Regex("&[0-9a-fk-orA-FK-OR]"))

            for ((index, replacement) in replacements.withIndex()) {
                if (replacement.string != null) {
                    // Support both indexed (%0, %1) and named (%name%, %amount%) placeholders
                    replacedMessage = replacedMessage.replace("%$index", replacement.string)
                    // If replacement has a name, also replace named placeholders
                    if (replacement.name != null) {
                        replacedMessage = replacedMessage.replace("%${replacement.name}%", replacement.string)
                    }
                } else if (replacement.component != null) {
                    // If message has legacy codes, serialize Component to legacy format with & codes
                    // Otherwise, serialize to MiniMessage
                    val serialized = if (hasLegacyCodes) {
                        // Use legacyAmpersand() to serialize with & codes instead of § codes
                        LegacyComponentSerializer.legacyAmpersand().serialize(replacement.component)
                    } else {
                        MiniMessage.miniMessage().serialize(replacement.component)
                    }
                    replacedMessage = replacedMessage.replace("%$index", serialized)
                    if (replacement.name != null) {
                        replacedMessage = replacedMessage.replace("%${replacement.name}%", serialized)
                    }
                }
            }

            return parseFormattedMessage(replacedMessage)
        }
    }
}

class MessageReplacement(
    val string: String?,
    val component: Component?,
    val name: String? = null,
) {
    constructor(string: String, name: String? = null) : this(string, null, name)
    constructor(component: Component, name: String? = null) : this(null, component, name)
}
