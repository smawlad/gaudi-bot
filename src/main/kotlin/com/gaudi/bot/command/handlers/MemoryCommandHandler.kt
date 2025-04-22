package com.gaudi.bot.command.handlers

import com.gaudi.bot.api.Message
import com.gaudi.bot.api.ReplyParameters
import com.gaudi.bot.api.TelegramClient
import com.gaudi.bot.api.sendMessage
import com.gaudi.bot.command.CommandHandler
import com.gaudi.bot.memory.UserMemorySystem
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

/**
 * Command for listing a user's conversation history
 */
class MemoryCommandHandler : CommandHandler {
    override val command: String = "/memory"

    override suspend fun handle(message: Message, client: TelegramClient) {
        logger.info { "Processing /memory command" }

        val userId = message.from?.id ?: message.chat.id
        val params = ReplyParameters(message.message_id, userId)

        // Extract subcommand and arguments
        val parts = message.text?.split(" ")?.drop(1) ?: emptyList()
        val subcommand = parts.firstOrNull()?.lowercase() ?: "list"

        when (subcommand) {
            "list" -> {
                // List all threads for the user
                val threads = UserMemorySystem.listUserThreads(userId)

                if (threads.isEmpty()) {
                    client.sendMessage("You don't have any conversation history yet.", params)
                    return
                }

                val response = StringBuilder("Your conversation history:\n\n")

                threads.forEachIndexed { index, thread ->
                    val date = Instant.ofEpochMilli(thread.createdAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()

                    response.appendLine("${index + 1}. ${thread.title} (${date})")
                    response.appendLine("   ID: ${thread.id}")
                    response.appendLine()
                }

                response.appendLine("\nUse /memory show [ID] to view a specific conversation.")
                client.sendMessage(response.toString(), params)
            }

            "show" -> {
                // Show a specific thread
                val threadIdStr = parts.getOrNull(1)

                if (threadIdStr == null) {
                    client.sendMessage("Please specify a thread ID: /memory show [ID]", params)
                    return
                }

                val threadId = threadIdStr.toLongOrNull()
                if (threadId == null) {
                    client.sendMessage("Invalid thread ID. Please use a number.", params)
                    return
                }

                val thread = UserMemorySystem.getThread(threadId)

                if (thread == null || thread.userId != userId) {
                    client.sendMessage("Thread not found or you don't have access to it.", params)
                    return
                }

                val conversation = UserMemorySystem.formatThreadAsConversation(threadId)
                val params = ReplyParameters(message.message_id, userId)
                client.sendMessage(conversation, params)
            }

            "clear" -> {
                // Will be implemented to delete memory threads
                client.sendMessage("Memory clearing is not yet implemented.", params)
            }

            "rename" -> {
                // Extract thread ID and new title
                if (parts.size < 3) {
                    client.sendMessage("Usage: /memory rename [ID] [New Title]", params)
                    return
                }

                val threadId = parts[1].toLongOrNull()
                if (threadId == null) {
                    client.sendMessage("Invalid thread ID. Please use a number.", params)
                    return
                }

                val thread = UserMemorySystem.getThread(threadId)

                if (thread == null || thread.userId != userId) {
                    client.sendMessage("Thread not found or you don't have access to it.", params)
                    return
                }

                val newTitle = parts.drop(2).joinToString(" ")
                val success = UserMemorySystem.updateThreadTitle(threadId, newTitle)

                if (success) {
                    client.sendMessage("Thread title updated successfully.", params)
                } else {
                    client.sendMessage("Failed to update thread title.", params)
                }
            }

            else -> {
                client.sendMessage("""
                    Memory command usage:
                    /memory list - List all your conversations
                    /memory show [ID] - View a specific conversation
                    /memory rename [ID] [New Title] - Rename a conversation
                    /memory clear [ID] - Delete a conversation (not yet implemented)
                """.trimIndent(), params)
            }
        }
    }
}