package com.gaudi.bot.command.handlers

import com.gaudi.bot.api.Message
import com.gaudi.bot.api.ReplyParameters
import com.gaudi.bot.api.TelegramClient
import com.gaudi.bot.api.sendMessage
import com.gaudi.bot.command.CommandHandler
import com.gaudi.bot.storage.UserKeyManager
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class SetKeyCommandHandler : CommandHandler {
    override val command: String = "/setkey"

    override suspend fun handle(message: Message, client: TelegramClient) {
        logger.info { "Processing /setkey command" }

        val params = ReplyParameters(message.message_id, message.chat.id)
        // Ensure this is a private chat for security
        if (message.chat.type != "private") {
            client.sendMessage("⚠️ For security reasons, please use private messages to set your API key.", params)
            return
        }

        // Extract the key from the message
        val apiKey = message.text?.replace("/setkey", "")?.trim()

        if (apiKey.isNullOrBlank()) {
            client.sendMessage(
                """
                Please provide your API key after the command:
                
                /setkey YOUR_API_KEY
                
                Your key will be encrypted before storage and only used for your requests.
                """.trimIndent(), params)
            return
        }

        val userId = message.from?.id?.toString() ?: ""
        if (userId.isBlank()) {
            client.sendMessage( "Could not identify user ID.", params)
            return
        }

        try {
            // Encrypt and store the key
            val encryptedKey = UserKeyManager.encryptAndStoreKey(userId, apiKey)
            if (encryptedKey) {
                client.sendMessage("✅ Your API key has been securely stored. It will be used for your future requests.", params)
            } else {
                client.sendMessage("❌ There was a problem storing your API key. Please try again later.", params)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error storing API key for user $userId" }
            client.sendMessage("❌ There was an error processing your request. Please try again later.", params)
        }
    }
}