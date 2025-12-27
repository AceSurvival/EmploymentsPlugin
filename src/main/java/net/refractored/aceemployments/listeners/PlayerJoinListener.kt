package net.refractored.aceemployments.listeners

import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.mail.Mail
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoinListener : Listener {
    @EventHandler(priority = EventPriority.LOW)
    fun onJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTaskLater(
            AceEmployments.instance,
            Runnable {
                Mail.sendMail(event.player)
            },
            20L * AceEmployments.instance.config.getInt("Mail.JoinDelay"),
        )
    }
}
