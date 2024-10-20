package com.gaudi.bot

import com.gaudi.bot.api.client.TelegramClient
import com.gaudi.bot.api.client.setWebhook
import com.gaudi.bot.api.server.startWebhookServer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

suspend fun main() {
    val token = System.getenv("TG_BOT_TOKEN") ?: error("TG_BOT_TOKEN environment variable is not set")
    val webhook = System.getenv("WEBHOOK_URL") ?: error("WEBHOOK_URL is not set")
    withContext(Dispatchers.Default) {
        val client = TelegramClient(token, HttpClient(CIO))
        try {
            val webhookSet = client.setWebhook(webhook)
            if (webhookSet) {
                logger.info { "Webhook set successfully" }
                startWebhookServer(client)
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