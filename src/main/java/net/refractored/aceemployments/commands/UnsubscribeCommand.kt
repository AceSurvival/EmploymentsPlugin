package net.refractored.aceemployments.commands

import net.refractored.aceemployments.exceptions.CommandErrorException
import net.refractored.aceemployments.subscription.SubscriptionManager
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import net.refractored.aceemployments.commands.framework.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class UnsubscribeCommand {
    @PermissionAnnotation("aceemployments.subscribe")
    @DescriptionAnnotation("Unsubscribe from notifications for a specific item")
    @CommandAnnotation("employ unsubscribe", "emp unsubscribe", "employments unsubscribe")
    fun unsubscribe(sender: CommandSender) {
        val player = sender as? Player ?: throw CommandErrorException(
            MessageUtil.getMessage("General.IsNotPlayer")
        )

        val itemInHand = player.inventory.itemInMainHand

        if (itemInHand.type.isAir) {
            throw CommandErrorException(MessageUtil.getMessage("Subscribe.NoItemInHand"))
        }

        val unsubscribed = SubscriptionManager.unsubscribe(player, itemInHand)
        if (unsubscribed) {
            sender.sendMessage(
                MessageUtil.getMessage(
                    "Subscribe.Unsubscribed",
                    listOf(MessageReplacement(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(itemInHand.displayName()))),
                ),
            )
        } else {
            throw CommandErrorException(MessageUtil.getMessage("Subscribe.NotSubscribed"))
        }
    }
}

