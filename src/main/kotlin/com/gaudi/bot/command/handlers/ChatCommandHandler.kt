package com.gaudi.bot.command.handlers

import com.gaudi.bot.ai.LLM
import com.gaudi.bot.ai.LLMConfig
import com.gaudi.bot.ai.ModelType
import com.gaudi.bot.ai.prompts.PromptManager
import com.gaudi.bot.api.Message
import com.gaudi.bot.api.ReplyParameters
import com.gaudi.bot.api.TelegramClient
import com.gaudi.bot.api.sendMessage
import com.gaudi.bot.command.CommandHandler
import com.gaudi.bot.memory.UserMemorySystem
import com.gaudi.bot.storage.UserKeyManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * Enhanced ChatCommandHandler that maintains conversation context
 */
class ChatCommandHandler : CommandHandler {
    override val command: String = "/chat"
    var llm: LLM? = null

    override suspend fun handle(message: Message, client: TelegramClient) {
        logger.info { "Processing /chat command with memory" }

        val userId = message.from?.id ?: return
        val chatId = message.chat.id
        val query = message.text?.replace(command, "")?.trim() ?: ""

        val params = ReplyParameters(message.message_id, chatId)

        if (query.isEmpty()) {
            client.sendMessage("Please provide a question or topic after the /chat command.", params)
            return
        }

        // Get or create a thread for this conversation
        var thread = UserMemorySystem.getLatestThread(userId, chatId)

        // If no thread exists or the last thread is older than 6 hours, create a new one
        if (thread == null || System.currentTimeMillis() - thread.updatedAt > 6 * 60 * 60 * 1000) {
            val threadTitle = "Chat on ${LocalDateTime.now()}"
            val threadId = UserMemorySystem.createThread(userId, chatId, threadTitle)
            thread = UserMemorySystem.getThread(threadId)

            // Add a system message to establish context
            UserMemorySystem.addMemory(
                threadId = threadId,
                role = "system",
                content = PromptManager.getPrompt("gaudi")!!.template
            )
        }

        if (thread == null) {
            client.sendMessage("Sorry, I encountered an error managing your conversation history.", params)
            return
        }

        // Add the user message to the thread
        UserMemorySystem.addMemory(
            threadId = thread.id,
            role = "user",
            content = query,
            metadata = mapOf(
                "username" to (message.from.username ?: "unknown"),
                "first_name" to message.from.first_name,
                "message_id" to message.message_id.toString()
            )
        )

        // Get user's API key if they've set one
        val userKey = UserKeyManager.getDecryptedKey(userId.toString())
        val modelType = if (userKey.isNullOrBlank()) ModelType.CLAUDE else ModelType.OPENAI

        // Create the LLM config
        val config = if (modelType == ModelType.OPENAI && !userKey.isNullOrBlank()) {
            LLMConfig(modelType, openAIApiKey = userKey)
        } else {
            LLMConfig(modelType)
        }

        // Initialize the LLM with configuration
        llm = LLM(config)

        try {
            // Get the thread memories
            val memories = UserMemorySystem.getThreadMemories(thread.id)

            // Load the conversation history into LLM from memory
            llm!!.loadConversation(memories)

            // Generate response - history is handled by the LLM internally now
            val response = llm!!.generateResponse(query)

            // Store the assistant's response in the memory thread
            UserMemorySystem.addMemory(
                threadId = thread.id,
                role = "assistant",
                content = response,
                metadata = mapOf("model" to modelType.toString())
            )

            client.sendMessage(response, params)
        } catch (e: Exception) {
            logger.error(e) { "Error generating chat response" }
            client.sendMessage("Sorry, I encountered an error processing your request. Please try again later.", params)
        }
    }
}