package net.refractored.aceemployments.commands

import net.refractored.aceemployments.exceptions.CommandErrorException
import net.refractored.aceemployments.gui.AllOrders
import net.refractored.aceemployments.util.MessageUtil
import net.refractored.aceemployments.commands.framework.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class BrowseCommand {
    @PermissionAnnotation("aceemployments.browse")
    @DescriptionAnnotation("Browse available job listings.")
    @CommandAnnotation("employ browse", "emp browse", "employments browse")
    fun browse(sender: CommandSender) {
        val player = sender as? Player ?: throw CommandErrorException(
            MessageUtil.getMessage("General.IsNotPlayer"),
        )
        player.openInventory(AllOrders.getGUI().inventory)
    }
}

