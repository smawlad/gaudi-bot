package com.gaudi.bot

import com.gaudi.bot.api.client.TelegramClient
import com.gaudi.bot.api.client.setWebhook
import com.gaudi.bot.api.server.startWebhookServer
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun main() {
    val token = System.getenv("TG_BOT_TOKEN") ?: error("TG_BOT_TOKEN environment variable is not set")
    val webhook = System.getenv("WEBHOOK_URL") ?: error("WEBHOOK_URL is not set")
    withContext(Dispatchers.Default) {
        val client = TelegramClient(token, HttpClient(CIO))
        try {
            val webhookSet = client.setWebhook(webhook)
            if (webhookSet) {
                println("Webhook set successfully")
                startWebhookServer(client)
            } else {
                println("Failed to set webhook")
            }
        } catch (e: Exception) {
            println("An error occurred: ${e.message}")
        } finally {
            client.httpClient.close()
        }
    }
}