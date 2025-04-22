package com.gaudi.bot

import com.gaudi.bot.api.TelegramClient
import com.gaudi.bot.api.setWebhook
import com.gaudi.bot.api.startMemoryEnabledWebhookServer
import com.gaudi.bot.memory.UserMemorySystem
import com.gaudi.bot.storage.MessageStorage
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.*

private val logger = KotlinLogging.logger {}

suspend fun main() {
    // Load environment variables
    val token = System.getenv("BOT_TOKEN") ?: error("BOT_TOKEN environment variable is not set")
    val webhook = System.getenv("WEBHOOK_URL") ?: error("WEBHOOK_URL is not set")

    // Database configuration
    val dbUrl = System.getenv("DATABASE_URL") ?: error("DATABASE_URL environment variable is not set")
    val dbUser = System.getenv("DATABASE_USER") ?: error("DATABASE_USER environment variable is not set")
    val dbPassword = System.getenv("DATABASE_PASSWORD") ?: error("DATABASE_PASSWORD environment variable is not set")

    withContext(Dispatchers.Default) {
        // Initialize the memory system
        try {
            logger.info { "Initializing memory system..." }
            UserMemorySystem.initialize(dbUrl, dbUser, dbPassword)
            logger.info { "Memory system initialized successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize memory system" }
            throw e  // Rethrow to terminate the application
        }

        // Initialize the message storage system for summarys
        try {
            logger.info { "Initializing message storage system..." }
            MessageStorage.initialize(dbUrl, dbUser, dbPassword)
            logger.info { "Message storage system initialized successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize message storage system" }
            throw e  // Rethrow to terminate the application
        }

        // Initialize the Telegram client
        val client = TelegramClient(token, HttpClient(CIO))

        try {
            // Set the webhook
            val webhookSet = client.setWebhook(webhook)
            if (webhookSet) {
                logger.info { "Webhook set successfully" }
                // Start the server with memory-enabled handlers
                startMemoryEnabledWebhookServer(client)
            } else {
                logger.error { "Failed to set webhook" }
            }
        } catch (e: Exception) {
            logger.error(e) { "An error occurred" }
        } finally {
            client.httpClient.close()
        }
    }
}