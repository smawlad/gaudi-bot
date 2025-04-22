package com.gaudi.bot.command

import com.gaudi.bot.api.Message
import com.gaudi.bot.api.TelegramClient
import com.gaudi.bot.command.handlers.ChatCommandHandler
import com.gaudi.bot.command.handlers.MemoryCommandHandler
import com.gaudi.bot.command.handlers.SetKeyCommandHandler
import com.gaudi.bot.command.handlers.SummaryCommandHandler

interface CommandHandler {
    val command: String
    suspend fun handle(message: Message, client: TelegramClient)
}

// Registry of all command handlers
object CommandRegistry {
    private val handlers = mapOf(
        "/chat" to ChatCommandHandler(),
        "/setkey" to SetKeyCommandHandler(),
        "/summary" to SummaryCommandHandler(),
        "/memory" to MemoryCommandHandler()
    )

    fun getHandler(command: String): CommandHandler? {
        // Extract the base command without arguments
        val baseCommand = command.split(" ")[0].split("@")[0].lowercase()
        return handlers[baseCommand]
    }
}