package net.refractored.aceemployments.commands

import net.refractored.aceemployments.exceptions.CommandErrorException
import net.refractored.aceemployments.notifier.NotifierManager
import net.refractored.aceemployments.notifier.NotifierType
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import net.refractored.aceemployments.commands.framework.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class NotifierCommand {
    @PermissionAnnotation("aceemployments.notifier")
    @DescriptionAnnotation("Set your notification preferences")
    @CommandAnnotation("employ notifier", "emp notifier", "employments notifier")
    fun setNotifier(sender: CommandSender, type: String) {
        val player = sender as? Player ?: throw CommandErrorException(
            MessageUtil.getMessage("General.IsNotPlayer")
        )

        val notifierType = when (type.uppercase()) {
            "ALL", "ALL_JOBS" -> NotifierType.ALL_JOBS
            "NONE", "NO_JOBS" -> NotifierType.NO_JOBS
            "SUBSCRIPTIONS", "SUB" -> NotifierType.SUBSCRIPTIONS
            else -> throw CommandErrorException(
                MessageUtil.getMessage(
                    "Notifier.InvalidType",
                    listOf(MessageReplacement("ALL, NONE, SUBSCRIPTIONS")),
                ),
            )
        }

        NotifierManager.setNotifier(player, notifierType)
        sender.sendMessage(
            MessageUtil.getMessage(
                "Notifier.Set",
                listOf(MessageReplacement(notifierType.name)),
            ),
        )
    }
}

