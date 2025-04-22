package com.gaudi.bot.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

class TelegramClient(token: String, val httpClient: HttpClient) {
    val baseUrl = "https://api.telegram.org/bot$token"
    val serializer = Json {
        ignoreUnknownKeys = true
    }
    suspend inline fun <reified T> sendRequest(method: String, params: Map<String, Any>): T {
        val response: HttpResponse = httpClient.get("$baseUrl/$method") {
            params.forEach { (key, value) -> parameter(key, value) }
        }
        return serializer.decodeFromString(response.bodyAsText())
    }
}

suspend fun TelegramClient.sendMessage(text: String, replyParameters: ReplyParameters): TelegramResponse<Message> {
    logger.info { "Sending message to chat ${replyParameters.chat_id}: $text" }
    val params = mapOf("chat_id" to replyParameters.chat_id, "text" to text, "reply_parameters" to Json.encodeToString(replyParameters))
    return sendRequest("sendMessage", params)
}

suspend fun TelegramClient.setWebhook(url: String): Boolean {
    logger.info { "Setting webhook to $url" }
    val params = mapOf("url" to url)
    val response: TelegramResponse<Boolean> = sendRequest("setWebhook", params)
    return response.result == true
}