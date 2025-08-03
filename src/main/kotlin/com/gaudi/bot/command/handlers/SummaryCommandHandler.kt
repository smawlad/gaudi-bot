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
import com.gaudi.bot.memory.MessageStorage
import com.gaudi.bot.util.UserKeyManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

class SummaryCommandHandler : CommandHandler {
    override val command: String = "/summary"

    // Exposed for testing
    var llm: LLM? = null

    override suspend fun handle(message: Message, client: TelegramClient) {
        logger.info { "Processing /summary command" }

        val chatId = message.chat.id
        val userId = message.from?.id ?: return

        val replyParams = ReplyParameters(message.message_id, chatId)

        // Extract optional parameters
        val params = parseParameters(message.text ?: "")
        val hoursToSummarize = params["hours"]?.toIntOrNull() ?: 24

        // Inform the user that we're generating the summary
        client.sendMessage("Generating a summary of the last $hoursToSummarize hours of chat history... This may take a moment.", replyParams)

        try {
            // Calculate the timestamp for N hours ago
            val currentTime = Instant.now()
            val cutoffTime = currentTime.minus(hoursToSummarize.toLong(), ChronoUnit.HOURS)
            val cutoffUnixTime = cutoffTime.epochSecond.toInt()

            // First try to get messages from our stored database
            logger.info { "Fetching stored chat history since $cutoffTime" }
            var chatHistory = MessageStorage.getMessagesForChat(chatId, cutoffUnixTime)
            if (chatHistory.isEmpty()) {
                client.sendMessage("There have been no messages in the last $hoursToSummarize hours.", replyParams)
                return
            }

            // Format the chat history for summarization
            val formattedHistory = formatChatHistory(chatHistory)
            logger.info { "Retrieved ${chatHistory.size} messages for summarization" }

            // Get user's API key if they've set one, otherwise use default
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

            // Reset conversation for summary (we don't want to keep history for this)
            llm!!.resetConversation()

            // Create prompt for summarization
            val prompt = PromptManager.getPrompt("summary")!!.format(
                "chat_content" to formattedHistory,
            )

            // Generate summary using LM
            val summary = llm!!.generateResponse(prompt)

            // Send the summary to the chat
            client.sendMessage("*Last $hoursToSummarize Hours*\n\n$summary", replyParams)

        } catch (e: Exception) {
            logger.error(e) { "Error generating chat summary: ${e.message}" }
            client.sendMessage( "Sorry, I encountered an error while generating the summary. Please try again later.", replyParams)
        }
    }

    private fun parseParameters(commandText: String): Map<String, String> {
        val params = mutableMapOf<String, String>()

        // Remove the command itself
        val text = commandText.replace(Regex("^/summary(@\\w+)?"), "").trim()

        // Look for hours parameter: /summary hours=12
        val hoursMatch = Regex("hours=(\\d+)").find(text)
        if (hoursMatch != null) {
            params["hours"] = hoursMatch.groupValues[1]
        }

        return params
    }

    private fun formatChatHistory(messages: List<Message>): String {
        val sb = StringBuilder()

        messages.forEach { message ->
            val senderName = message.from?.let {
                if (it.username != null) "@${it.username}" else "${it.first_name} ${it.last_name ?: ""}"
            } ?: "Unknown User"

            val timestamp = Instant.ofEpochSecond(message.date.toLong())
            val timeStr = timestamp.toString().substring(11, 16) // Extract HH:MM

            sb.appendLine("[$timeStr] $senderName: ${message.text ?: "[Media or non-text content]"}")
        }

        return sb.toString()
    }
}
