package net.refractored.aceemployments.tracker

import com.j256.ormlite.stmt.QueryBuilder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.storage.OrderStorage
import net.refractored.aceemployments.order.Order
import net.refractored.aceemployments.order.OrderStatus
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

object ActionBarTracker {
    private val trackingPlayers = mutableMapOf<UUID, UUID>() // Player UUID -> Order UUID

    fun startTracking(player: Player, orderId: UUID) {
        trackingPlayers[player.uniqueId] = orderId
    }

    fun stopTracking(player: Player) {
        trackingPlayers.remove(player.uniqueId)
    }

    fun start() {
        Bukkit.getScheduler().runTaskTimer(
            AceEmployments.instance,
            Runnable {
                for ((playerId, orderId) in trackingPlayers.toMap()) {
                    val player = Bukkit.getPlayer(playerId) ?: continue
                    val order = OrderStorage.getById(orderId) ?: continue

                    if (order.status != OrderStatus.PENDING) {
                        stopTracking(player)
                        continue
                    }

                    val progress = order.itemCompleted.toDouble() / order.itemAmount.toDouble()
                    val percentage = (progress * 100).toInt()

                    val message = MessageUtil.toComponent(
                        "<green>Job Progress: <white>${order.itemCompleted}/${order.itemAmount} <green>($percentage%)"
                    )

                    player.sendActionBar(message)
                }
            },
            0L,
            20L, // Update every second
        )
    }
}

