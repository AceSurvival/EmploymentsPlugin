package net.refractored.aceemployments.webhook

import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.order.Order
import org.bukkit.Bukkit
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object DiscordWebhook {
    fun sendJobCreated(order: Order) {
        if (!AceEmployments.instance.config.getBoolean("Discord.Enabled", false)) return

        val webhookUrl = AceEmployments.instance.config.getString("Discord.WebhookURL") ?: return
        if (webhookUrl.isEmpty()) return

        Bukkit.getScheduler().runTaskAsynchronously(AceEmployments.instance, Runnable {
            try {
                val url = URL(webhookUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val json = """
                    {
                        "embeds": [{
                            "title": "New Job Created",
                            "description": "${order.getOwner().name} created a new job",
                            "fields": [
                                {
                                    "name": "Item",
                                    "value": "${order.item.displayName()} x${order.itemAmount}",
                                    "inline": true
                                },
                                {
                                    "name": "Payment",
                                    "value": "$${order.cost}",
                                    "inline": true
                                },
                                {
                                    "name": "Expires",
                                    "value": "${order.timeExpires}",
                                    "inline": false
                                }
                            ],
                            "color": 3066993
                        }]
                    }
                """.trimIndent()

                val outputStream: OutputStream = connection.outputStream
                outputStream.write(json.toByteArray(StandardCharsets.UTF_8))
                outputStream.flush()
                outputStream.close()

                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                AceEmployments.instance.logger.warning("Failed to send Discord webhook: ${e.message}")
            }
        })
    }
}

