package net.refractored.aceemployments.subscription

import com.j256.ormlite.stmt.QueryBuilder
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.database.Database.Companion.subscriptionDao
import net.refractored.aceemployments.notifier.NotifierManager
import net.refractored.aceemployments.order.Order
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

object SubscriptionManager {
    fun subscribe(player: Player, item: ItemStack): GoalSubscription {
        val subscription = GoalSubscription(
            UUID.randomUUID(),
            player.uniqueId,
            item.clone().apply { amount = 1 },
        )
        subscriptionDao.create(subscription)
        return subscription
    }

    fun unsubscribe(player: Player, item: ItemStack): Boolean {
        val queryBuilder: QueryBuilder<GoalSubscription, UUID> = subscriptionDao.queryBuilder()
        queryBuilder
            .where()
            .eq("player", player.uniqueId)
            .and()
            .eq("item", item.clone().apply { amount = 1 })
        val subscriptions = subscriptionDao.query(queryBuilder.prepare())
        if (subscriptions.isEmpty()) return false
        subscriptionDao.delete(subscriptions.first())
        return true
    }

    fun getSubscriptions(player: Player): List<GoalSubscription> {
        val queryBuilder: QueryBuilder<GoalSubscription, UUID> = subscriptionDao.queryBuilder()
        queryBuilder.where().eq("player", player.uniqueId)
        return subscriptionDao.query(queryBuilder.prepare())
    }

    fun notifySubscribers(order: Order) {
        val queryBuilder: QueryBuilder<GoalSubscription, UUID> = subscriptionDao.queryBuilder()
        val subscriptions = subscriptionDao.query(queryBuilder.prepare())

        for (subscription in subscriptions) {
            if (!order.itemMatches(subscription.item)) continue

            val player = Bukkit.getPlayer(subscription.player as UUID) ?: continue
            val notifier = NotifierManager.getNotifier(player)
            
            if (notifier.notifierType == net.refractored.aceemployments.notifier.NotifierType.SUBSCRIPTIONS ||
                notifier.notifierType == net.refractored.aceemployments.notifier.NotifierType.ALL_JOBS
            ) {
                val message = MessageUtil.getMessage(
                    "Subscription.NewJobNotification",
                    listOf(
                        MessageReplacement(order.getItemInfo()),
                        MessageReplacement(order.cost.toString()),
                    ),
                )
                player.sendMessage(message)
            }
        }
    }
}

