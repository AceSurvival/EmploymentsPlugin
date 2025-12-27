package net.refractored.aceemployments.commands

import net.refractored.aceemployments.exceptions.CommandErrorException
import net.refractored.aceemployments.gui.MyOrders
import net.refractored.aceemployments.util.MessageUtil
import net.refractored.aceemployments.commands.framework.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class MyJobsCommand {
    @PermissionAnnotation("aceemployments.myjobs")
    @DescriptionAnnotation("View your job listings.")
    @CommandAnnotation("employ myjobs", "emp myjobs", "employments myjobs")
    fun myJobs(sender: CommandSender) {
        val player = sender as? Player ?: throw CommandErrorException(
            MessageUtil.getMessage("General.IsNotPlayer"),
        )
        player.openInventory(MyOrders.getGUI(player).inventory)
    }
}

