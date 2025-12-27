package net.refractored.aceemployments.commands

import net.refractored.aceemployments.exceptions.CommandErrorException
import net.refractored.aceemployments.subscription.SubscriptionManager
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import net.refractored.aceemployments.commands.framework.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SubscribeCommand {
    @PermissionAnnotation("aceemployments.subscribe")
    @DescriptionAnnotation("Subscribe to notifications for a specific item")
    @CommandAnnotation("employ subscribe", "emp subscribe", "employments subscribe")
    fun subscribe(sender: CommandSender) {
        val player = sender as? Player ?: throw CommandErrorException(
            MessageUtil.getMessage("General.IsNotPlayer")
        )

        val itemInHand = player.inventory.itemInMainHand

        if (itemInHand.type.isAir) {
            throw CommandErrorException(MessageUtil.getMessage("Subscribe.NoItemInHand"))
        }

        SubscriptionManager.subscribe(player, itemInHand)
        val itemName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(itemInHand.displayName())
        sender.sendMessage(
            MessageUtil.getMessage(
                "Subscribe.Subscribed",
                listOf(MessageReplacement(itemName)),
            ),
        )
    }
}

