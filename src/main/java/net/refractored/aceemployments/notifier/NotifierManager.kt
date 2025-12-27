package net.refractored.aceemployments.notifier

import net.refractored.aceemployments.database.Database.Companion.notifierDao
import net.refractored.aceemployments.order.Order
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

object NotifierManager {
    fun getNotifier(player: Player): PlayerNotifier {
        val notifier = notifierDao.queryForId(player.uniqueId)
        return notifier ?: PlayerNotifier(player.uniqueId, NotifierType.ALL_JOBS).also {
            notifierDao.create(it)
        }
    }

    fun setNotifier(player: Player, type: NotifierType) {
        val notifier = getNotifier(player)
        notifier.notifierType = type
        notifierDao.createOrUpdate(notifier)
    }

    fun notifyPlayer(player: Player, order: Order) {
        val notifier = getNotifier(player)
        
        when (notifier.notifierType) {
            NotifierType.ALL_JOBS -> {
                val message = MessageUtil.getMessage(
                    "Notifier.NewJobNotification",
                    listOf(
                        MessageReplacement(order.getItemInfo()),
                        MessageReplacement(order.cost.toString()),
                    ),
                )
                player.sendMessage(message)
            }
            NotifierType.NO_JOBS -> {
                // Don't notify
            }
            NotifierType.SUBSCRIPTIONS -> {
                // Handled by SubscriptionManager
            }
        }
    }

    fun notifyAllPlayers(order: Order) {
        for (player in Bukkit.getOnlinePlayers()) {
            notifyPlayer(player, order)
        }
    }
}

