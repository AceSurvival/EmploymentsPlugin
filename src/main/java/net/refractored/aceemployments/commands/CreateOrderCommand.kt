package net.refractored.aceemployments.commands

import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.config.Presets
import net.refractored.aceemployments.storage.OrderStorage
import net.refractored.aceemployments.exceptions.CommandErrorException
import net.refractored.aceemployments.order.Order
import net.refractored.aceemployments.order.Order.Companion.getMaxOrders
import net.refractored.aceemployments.order.OrderStatus
import net.refractored.aceemployments.util.MessageReplacement
import net.refractored.aceemployments.util.MessageUtil
import net.refractored.aceemployments.commands.framework.*
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import java.util.*

class CreateOrderCommand {
    @PermissionAnnotation("aceemployments.create")
    @DescriptionAnnotation("Create a new employment/job listing.")
    @CommandAnnotation("employ create", "emp create", "employments create")
    fun createOrder(
        sender: CommandSender,
        @AutoCompleteAnnotation("materials") itemName: String,
        amount: Int,
        price: Double,
    ) {
        val player = sender as? Player ?: throw CommandErrorException(
            MessageUtil.getMessage("General.IsNotPlayer"),
        )

        if (amount < 1) {
            throw CommandErrorException(
                MessageUtil.getMessage("Commands.CreateJob.NegativeAmount"),
            )
        }

        if (price < 1) {
            throw CommandErrorException(
                MessageUtil.getMessage("Commands.CreateJob.NegativeMoneyAmount"),
            )
        }

        // Get expiration time from config (in seconds), convert to hours
        val expireSeconds = AceEmployments.instance.config.getLong("PlayerJobOffers.OfferExpireTime", 604800)
        val hours = expireSeconds / 3600 // Convert seconds to hours

        if (AceEmployments.instance.eco.getBalance(player) < price) {
            throw CommandErrorException(
                MessageUtil.getMessage("Commands.CreateJob.NotEnoughMoney"),
            )
        }

        val orders = OrderStorage.getOrdersByStatus(OrderStatus.PENDING)
            .filter { it.user == player.uniqueId }
        val maxOrders = getMaxOrders(player)

        if (orders.count() >= maxOrders) {
            throw CommandErrorException(
                MessageUtil.getMessage(
                    "Commands.CreateJob.CannotCreate",
                    listOf(MessageReplacement("$maxOrders", "limit")),
                ),
            )
        }

        val item: ItemStack =
            Presets.getPreset(itemName)
                ?: Material.getMaterial(itemName.uppercase())?.let { ItemStack(it) }
                ?: throw CommandErrorException(
                    MessageUtil.getMessage("Commands.CreateJob.InvalidMaterial", listOf(MessageReplacement(itemName, "material"))),
                )

        item.amount = 1

        if (item.type == Material.AIR) {
            throw CommandErrorException(
                MessageUtil.getMessage("Commands.CreateJob.InvalidMaterial", listOf(MessageReplacement("AIR", "material"))),
            )
        }

        if (blacklistedMaterial(item.type.name)) {
            throw CommandErrorException(
                MessageUtil.getMessage("Commands.CreateJob.InvalidMaterial", listOf(MessageReplacement(item.type.name, "material"))),
            )
        }

        val maxItems = AceEmployments.instance.config.getInt("Orders.MaximumItems", 256)

        when {
            maxItems == -1 && amount > item.maxStackSize -> {
                throw CommandErrorException(
                    MessageUtil.getMessage(
                        "CreateOrder.StackSizeExceeded",
                        listOf(
                            MessageReplacement(item.maxStackSize.toString()),
                        ),
                    ),
                )
            }
            maxItems != 0 && amount >= maxItems -> {
                throw CommandErrorException(
                    MessageUtil.getMessage(
                        "CreateOrder.MaxOrdersExceeded",
                        listOf(
                            MessageReplacement(maxItems.toString()),
                        ),
                    ),
                )
            }
        }

        item.amount = 1

        AceEmployments.instance.eco.withdrawPlayer(player, price)

        val order =
            Order.createOrder(
                player.uniqueId,
                price,
                item,
                amount,
                hours,
            )

        val itemName = order.item.displayName()?.let { MessageUtil.toPlainText(it) } ?: order.item.type.name
        val perItem = price / amount

        player.sendMessage(
            MessageUtil.getMessage(
                "Commands.CreateJob.Created",
                listOf(
                    MessageReplacement(amount.toString(), "amount"),
                    MessageReplacement(itemName, "material"),
                    MessageReplacement(price.toString(), "price"),
                    MessageReplacement(String.format("%.2f", perItem), "peritem"),
                ),
            ),
        )
    }

    private fun blacklistedMaterial(arg: String): Boolean {
        val blockedItems = AceEmployments.instance.config.getStringList("Settings.BlockedItems")
        return blockedItems.any { it.equals(arg, true) }
    }
}

