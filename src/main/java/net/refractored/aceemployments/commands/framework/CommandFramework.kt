package net.refractored.aceemployments.commands.framework

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.exceptions.CommandErrorException
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.PluginCommand
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method

/**
 * Custom command framework using Paper's native Commands API
 */
class CommandFramework(private val plugin: JavaPlugin) {
    private val commands = mutableMapOf<String, Any>() // mainCommand -> subCommand -> CommandInfo
    private val tabCompletions = mutableMapOf<String, TabCompletionProvider>()

    /**
     * Register a command class
     */
    fun register(commandClass: Any) {
        val methods = commandClass.javaClass.declaredMethods
        
        for (method in methods) {
            val commandAnnotation = method.getAnnotation(CommandAnnotation::class.java)
                ?: continue
            
            val permissionAnnotation = method.getAnnotation(PermissionAnnotation::class.java)
            val descriptionAnnotation = method.getAnnotation(DescriptionAnnotation::class.java)
            
            // Parse aliases to extract main command and subcommand
            for (alias in commandAnnotation.aliases) {
                val parts = alias.split(" ", limit = 2)
                if (parts.size < 2) continue
                
                val mainCommand = parts[0].lowercase()
                val subCommand = parts[1].lowercase()
                
                val info = CommandInfo(
                    commandClass = commandClass,
                    method = method,
                    aliases = commandAnnotation.aliases.toList(),
                    permission = permissionAnnotation?.permission,
                    description = descriptionAnnotation?.description ?: "",
                    subCommand = subCommand
                )
                
                // Store by main command -> subcommand
                if (!commands.containsKey(mainCommand)) {
                    commands[mainCommand] = mutableMapOf<String, CommandInfo>()
                }
                @Suppress("UNCHECKED_CAST")
                (commands[mainCommand] as MutableMap<String, CommandInfo>)[subCommand] = info
                
                // Register main command if not already registered
                if (!registeredMainCommands.contains(mainCommand)) {
                    registerMainCommand(mainCommand)
                    registeredMainCommands.add(mainCommand)
                }
            }
        }
    }
    
    private val registeredMainCommands = mutableSetOf<String>()

    /**
     * Register tab completion provider
     */
    fun registerTabCompletion(key: String, provider: TabCompletionProvider) {
        tabCompletions[key] = provider
    }

    /**
     * Register the main command (e.g., "employ")
     */
    private fun registerMainCommand(mainCommand: String) {
        val command = plugin.getCommand(mainCommand) ?: run {
            // Create a custom Command implementation and register it
            val customCommand = object : Command(mainCommand) {
                override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
                    if (args.isEmpty()) {
                        // Show help or list subcommands
                        sender.sendMessage("Usage: /$mainCommand <subcommand>")
                        return true
                    }
                    
                    val subCommand = args[0].lowercase()
                    @Suppress("UNCHECKED_CAST")
                    val subCommands = commands[mainCommand] as? Map<String, CommandInfo>
                    val info = subCommands?.get(subCommand)
                    
                    if (info == null) {
                        sendComponentMessage(sender, MessageUtil.getMessage("General.InvalidCommand", 
                            listOf(net.refractored.aceemployments.util.MessageReplacement(subCommand))))
                        return true
                    }
                    
                    // Check permission
                    info.permission?.let {
                        if (!sender.hasPermission(it)) {
                            sendComponentMessage(sender, MessageUtil.getMessage("General.NoPermission"))
                            return true
                        }
                    }
                    
                    // Execute with remaining args (skip subcommand)
                    val remainingArgs = Array(args.size - 1) { args[it + 1] }
                    return try {
                        executeCommand(sender, info, remainingArgs)
                    } catch (e: CommandErrorException) {
                        sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(e.component))
                        true
                    } catch (e: Exception) {
                        val cause = e.cause
                        if (cause is CommandErrorException) {
                            sendComponentMessage(sender, cause.component)
                        } else if (e is IllegalArgumentException) {
                            // Convert IllegalArgumentException to formatted error message
                            sendComponentMessage(sender, MessageUtil.getMessage("General.UnexpectedError"))
                            e.printStackTrace()
                        } else {
                            sendComponentMessage(sender, MessageUtil.getMessage("General.UnexpectedError"))
                            e.printStackTrace()
                        }
                        true
                    }
                }
                
                override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
                    @Suppress("UNCHECKED_CAST")
                    val subCommands = commands[mainCommand] as? Map<String, CommandInfo> ?: return emptyList()
                    
                    if (args.size == 1) {
                        // Complete subcommand names - only show allowed subcommands
                        val allowedSubcommands = listOf("browse", "create", "myjobs", "reload", "delist")
                        return allowedSubcommands
                            .filter { 
                                val info = subCommands[it]
                                // Check if command exists and user has permission
                                info != null && (info.permission == null || sender.hasPermission(info.permission))
                            }
                            .filter { it.startsWith(args[0].lowercase()) }
                    }
                    
                    // Complete arguments for specific subcommand
                    val subCommand = args[0].lowercase()
                    val info = subCommands[subCommand] ?: return emptyList()
                    
                    // Check permission
                    info.permission?.let {
                        if (!sender.hasPermission(it)) {
                            return emptyList()
                        }
                    }
                    
                    // Get remaining args (skip subcommand)
                    val remainingArgs = Array(args.size - 1) { args[it + 1] }
                    return TabCompleterImpl(info, tabCompletions).onTabComplete(sender, this, alias, remainingArgs) ?: emptyList()
                }
            }
            
            customCommand.description = "AceEmployments main command"
            customCommand.usage = "/$mainCommand <subcommand>"
            
            // Register the command
            plugin.server.commandMap.register(plugin.description.name.lowercase(), customCommand)
            
            // Return the registered command
            plugin.server.commandMap.getCommand(mainCommand) ?: customCommand
        }
        
        // If command exists in plugin.yml, update executor and tab completer
        if (command is PluginCommand) {
            command.setExecutor { sender, cmd, label, args ->
                if (args.isEmpty()) {
                    sender.sendMessage("Usage: /$mainCommand <subcommand>")
                    return@setExecutor true
                }
                
                val subCommand = args[0].lowercase()
                @Suppress("UNCHECKED_CAST")
                val subCommands = commands[mainCommand] as? Map<String, CommandInfo>
                val info = subCommands?.get(subCommand)
                
                if (info == null) {
                    sendComponentMessage(sender, MessageUtil.getMessage("General.InvalidCommand", 
                        listOf(net.refractored.aceemployments.util.MessageReplacement(subCommand))))
                    return@setExecutor true
                }
                
                // Check permission
                info.permission?.let {
                    if (!sender.hasPermission(it)) {
                        sendComponentMessage(sender, MessageUtil.getMessage("General.NoPermission"))
                        return@setExecutor true
                    }
                }
                
                // Execute with remaining args (skip subcommand)
                val remainingArgs = Array(args.size - 1) { args[it + 1] }
                try {
                    executeCommand(sender, info, remainingArgs)
                } catch (e: CommandErrorException) {
                    sendComponentMessage(sender, e.component)
                    true
                } catch (e: Exception) {
                    sendComponentMessage(sender, MessageUtil.getMessage("General.UnexpectedError"))
                    e.printStackTrace()
                    true
                }
            }
            
            command.tabCompleter = org.bukkit.command.TabCompleter { sender, cmd, alias, args ->
                @Suppress("UNCHECKED_CAST")
                val subCommands = commands[mainCommand] as? Map<String, CommandInfo> ?: return@TabCompleter emptyList()
                
                if (args.size == 1) {
                    // Complete subcommand names - only show allowed subcommands
                    val allowedSubcommands = listOf("browse", "create", "myjobs", "reload", "delist")
                    return@TabCompleter allowedSubcommands
                        .filter { 
                            val info = subCommands[it]
                            // Check if command exists and user has permission
                            info != null && (info.permission == null || sender.hasPermission(info.permission))
                        }
                        .filter { it.startsWith(args[0].lowercase()) }
                }
                
                // Complete arguments for specific subcommand
                val subCommand = args[0].lowercase()
                val info = subCommands[subCommand] ?: return@TabCompleter emptyList()
                
                // Check permission
                info.permission?.let {
                    if (!sender.hasPermission(it)) {
                        return@TabCompleter emptyList()
                    }
                }
                
                // Get remaining args (skip subcommand)
                val remainingArgs = Array(args.size - 1) { args[it + 1] }
                TabCompleterImpl(info, tabCompletions).onTabComplete(sender, cmd, alias, remainingArgs) ?: emptyList()
            }
        }
    }

    /**
     * Execute a command
     */
    private fun executeCommand(sender: CommandSender, info: CommandInfo, args: Array<String>): Boolean {
        // Check permission
        info.permission?.let {
            if (!sender.hasPermission(it)) {
                sendComponentMessage(sender, MessageUtil.getMessage("General.NoPermission"))
                return true
            }
        }

        // Get method parameters
        val parameters = info.method.parameters
        val paramValues = mutableListOf<Any?>()
        
        // First parameter is always CommandSender
        paramValues.add(sender)
        
        // Parse remaining parameters from args
        var argIndex = 0
        for (i in 1 until parameters.size) {
            val param = parameters[i]
            val paramType = param.type
            
            when {
                paramType == String::class.java -> {
                    if (argIndex < args.size) {
                        paramValues.add(args[argIndex++])
                    } else {
                        // Get a better parameter name/description
                        val paramName = when {
                            param.name == "itemName" || param.name.contains("item", ignoreCase = true) -> "item name"
                            param.name == "targetPlayer" || param.name.contains("player", ignoreCase = true) -> "player name"
                            param.name == "type" -> "type"
                            else -> param.name
                        }
                        val messageKey = "General.MissingArguments"
                        val messageText = AceEmployments.instance.messages.getString(messageKey)
                        if (messageText != null) {
                            sendComponentMessage(sender, MessageUtil.getMessage(messageKey, 
                                listOf(net.refractored.aceemployments.util.MessageReplacement(paramName))))
                        } else {
                            // Fallback if message not found
                            sendComponentMessage(sender, MessageUtil.toComponent("<red>You must specify a value for the argument: $paramName"))
                        }
                        return true
                    }
                }
                paramType == Int::class.java || paramType == Int::class.javaPrimitiveType -> {
                    if (argIndex < args.size) {
                        try {
                            paramValues.add(args[argIndex++].toInt())
                        } catch (e: NumberFormatException) {
                            sendComponentMessage(sender, MessageUtil.getMessage("General.InvalidNumber",
                                listOf(net.refractored.aceemployments.util.MessageReplacement(args[argIndex - 1]))))
                            return true
                        }
                    } else {
                        val paramName = when {
                            param.name == "amount" -> "amount"
                            else -> param.name
                        }
                        val message = MessageUtil.getMessage("General.MissingArguments",
                            listOf(net.refractored.aceemployments.util.MessageReplacement(paramName)))
                        // Use Adventure API to send Component
                        if (sender is org.bukkit.entity.Player) {
                            sender.sendMessage(message)
                        } else {
                            sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(message))
                        }
                        return true
                    }
                }
                paramType == Double::class.java || paramType == Double::class.javaPrimitiveType -> {
                    if (argIndex < args.size) {
                        try {
                            paramValues.add(args[argIndex++].toDouble())
                        } catch (e: NumberFormatException) {
                            sendComponentMessage(sender, MessageUtil.getMessage("General.InvalidNumber",
                                listOf(net.refractored.aceemployments.util.MessageReplacement(args[argIndex - 1]))))
                            return true
                        }
                    } else {
                        val paramName = when {
                            param.name == "price" -> "price"
                            else -> param.name
                        }
                        val messageKey = "General.MissingArguments"
                        val messageText = AceEmployments.instance.messages.getString(messageKey)
                        if (messageText != null) {
                            sendComponentMessage(sender, MessageUtil.getMessage(messageKey,
                                listOf(net.refractored.aceemployments.util.MessageReplacement(paramName))))
                        } else {
                            // Fallback if message not found
                            sendComponentMessage(sender, MessageUtil.toComponent("<red>You must specify a value for the argument: $paramName"))
                        }
                        return true
                    }
                }
                paramType == Array<String>::class.java -> {
                    // Remaining args
                    paramValues.add(args.sliceArray(argIndex until args.size))
                    argIndex = args.size
                }
                paramType == Player::class.java -> {
                    // Require player
                    if (sender !is Player) {
                        sendComponentMessage(sender, MessageUtil.getMessage("General.IsNotPlayer"))
                        return true
                    }
                    paramValues.add(sender as Player)
                }
                else -> {
                    paramValues.add(null)
                }
            }
        }

        // Check if we have too many arguments
        if (argIndex < args.size && !parameters.any { it.type == Array<String>::class.java }) {
            sendComponentMessage(sender, MessageUtil.getMessage("General.TooManyArguments"))
            return true
        }

        // Invoke method
        try {
            info.method.isAccessible = true
            val result = info.method.invoke(info.commandClass, *paramValues.toTypedArray())
            // Method executed successfully
            return true
        } catch (e: Exception) {
            val cause = e.cause
            if (cause is CommandErrorException) {
                throw cause
            }
            // If it's an IllegalArgumentException from Order creation, convert it
            if (e is IllegalArgumentException) {
                // Check if it's a known error message
                when {
                    e.message?.contains("exceeds") == true -> {
                        throw CommandErrorException(MessageUtil.getMessage("General.UnexpectedError"))
                    }
                    e.message?.contains("Item stack size") == true -> {
                        throw CommandErrorException(MessageUtil.getMessage("CreateOrder.StackSizeExceeded",
                            listOf(net.refractored.aceemployments.util.MessageReplacement("64"))))
                    }
                    e.message?.contains("Max orders exceeded") == true -> {
                        throw CommandErrorException(MessageUtil.getMessage("CreateOrder.MaxOrdersExceeded",
                            listOf(net.refractored.aceemployments.util.MessageReplacement("9999"))))
                    }
                    else -> {
                        // Unknown IllegalArgumentException, show generic error
                        throw CommandErrorException(MessageUtil.getMessage("General.UnexpectedError"))
                    }
                }
            }
            // Re-throw CommandErrorException as-is
            if (e is CommandErrorException) {
                throw e
            }
            // For other exceptions, wrap in CommandErrorException
            throw CommandErrorException(MessageUtil.getMessage("General.UnexpectedError"))
        }
    }

    /**
     * Helper function to send Component messages to CommandSender
     */
    private fun sendComponentMessage(sender: CommandSender, component: net.kyori.adventure.text.Component) {
        // In Paper, CommandSender implements Audience, so we can send Component directly
        (sender as? Audience)?.sendMessage(component) ?: run {
            // Fallback: serialize to legacy text
            sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component))
        }
    }

    /**
     * Unregister all commands
     */
    fun unregisterAll() {
        registeredMainCommands.forEach { mainCommand ->
            try {
                val command = plugin.getCommand(mainCommand)
                command?.setExecutor(null)
                command?.tabCompleter = null
            } catch (e: Exception) {
                // Ignore
            }
        }
        commands.clear()
        tabCompletions.clear()
        registeredMainCommands.clear()
    }

    data class CommandInfo(
        val commandClass: Any,
        val method: Method,
        val aliases: List<String>,
        val permission: String?,
        val description: String,
        val subCommand: String
    )
}

/**
 * Type alias for tab completion provider
 */
typealias TabCompletionProvider = (sender: CommandSender, args: Array<String>) -> List<String>

/**
 * Tab completer implementation
 */
class TabCompleterImpl(
    private val commandInfo: CommandFramework.CommandInfo,
    private val tabCompletions: Map<String, TabCompletionProvider>
) : org.bukkit.command.TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        val method = commandInfo.method
        val parameters = method.parameters
        
        // Find which parameter we're completing
        // parameters[0] = CommandSender
        // parameters[1] = first argument (itemName) - completed when args.size = 0 or args = [""]
        // parameters[2] = second argument (amount) - completed when args.size = 1 with non-empty first arg
        // parameters[3] = third argument (price) - completed when args.size = 2 with non-empty first two args
        // Count non-empty args to determine which parameter we're on
        val nonEmptyArgsCount = args.count { it.isNotEmpty() }
        val paramIndex = nonEmptyArgsCount + 1
        
        if (paramIndex < 1 || paramIndex >= parameters.size) {
            return emptyList()
        }
        
        val param = parameters[paramIndex]
        val autoCompleteAnnotation = param.getAnnotation(AutoCompleteAnnotation::class.java)
        
        if (autoCompleteAnnotation != null) {
            val provider = tabCompletions[autoCompleteAnnotation.key]
            if (provider != null) {
                // Pass all args (including empty ones) to the provider
                // The provider should handle filtering internally
                val stringArgs = Array(args.size) { args[it] }
                return provider.invoke(sender, stringArgs)
            }
            return emptyList()
        }
        
        // If no annotation, try to complete based on parameter type
        val paramType = param.type
        when {
            paramType == String::class.java -> {
                // For delist command, complete player names
                if (commandInfo.subCommand == "delist") {
                    val currentInput = args.lastOrNull() ?: ""
                    return org.bukkit.Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.startsWith(currentInput, ignoreCase = true) }
                        .sorted()
                }
            }
            paramType == Int::class.java || paramType == Int::class.javaPrimitiveType -> {
                // For amount parameter, provide some common number suggestions
                val currentInput = args.lastOrNull()?.trim() ?: ""
                val suggestions = listOf("1", "16", "32", "64", "128", "256", "512", "1024")
                if (currentInput.isEmpty()) {
                    return suggestions
                }
                // Filter suggestions that start with current input
                return suggestions.filter { it.startsWith(currentInput) }
            }
            paramType == Double::class.java || paramType == Double::class.javaPrimitiveType -> {
                // For price parameter, provide some common price suggestions
                val currentInput = args.lastOrNull()?.trim() ?: ""
                val suggestions = listOf("1", "10", "50", "100", "500", "1000", "5000", "10000")
                if (currentInput.isEmpty()) {
                    return suggestions
                }
                // Filter suggestions that start with current input
                return suggestions.filter { it.startsWith(currentInput) }
            }
            paramType == String::class.java -> {
                // For string parameters without annotation, return empty (let user type)
                return emptyList()
            }
        }
        
        return emptyList()
    }
}

