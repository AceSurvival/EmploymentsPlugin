package net.refractored.aceemployments.commands

import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.util.MessageUtil
import net.refractored.aceemployments.commands.framework.*
import org.bukkit.command.CommandSender

class ReloadCommand {
    @PermissionAnnotation("aceemployments.admin.reload")
    @DescriptionAnnotation("Reloads plugin configuration")
    @CommandAnnotation("employ reload", "emp reload", "employments reload")
    fun reload(sender: CommandSender) {
        AceEmployments.instance.reload()
        sender.sendMessage(MessageUtil.getMessage("Commands.Reload.ReloadedPlugin"))
    }
}
