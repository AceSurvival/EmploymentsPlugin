package net.refractored.aceemployments.commands

import net.refractored.aceemployments.exceptions.CommandErrorException
import net.refractored.aceemployments.gui.ContainerGUI
import net.refractored.aceemployments.util.MessageUtil
import net.refractored.aceemployments.commands.framework.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ContainerCommand {
    @PermissionAnnotation("aceemployments.container")
    @DescriptionAnnotation("Open your item container")
    @CommandAnnotation("employ container", "emp container", "employments container")
    fun container(sender: CommandSender) {
        val player = sender as? Player ?: throw CommandErrorException(
            MessageUtil.getMessage("General.IsNotPlayer")
        )
        player.openInventory(ContainerGUI.getGUI(player).inventory)
    }
}

