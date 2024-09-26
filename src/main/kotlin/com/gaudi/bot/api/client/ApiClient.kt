package com.gaudi.bot.api.client

import com.gaudi.bot.api.model.Message
import com.gaudi.bot.api.model.TelegramResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json

class TelegramClient(token: String, val httpClient: HttpClient) {
    val baseUrl = "https://api.telegram.org/bot$token"
    val serializer = Json {
        ignoreUnknownKeys = true
    }
    suspend inline fun <reified T> sendRequest(method: String, params: Map<String, String>): T {
        val response: HttpResponse = httpClient.get("$baseUrl/$method") {
            params.forEach { (key, value) -> parameter(key, value) }
        }
        return serializer.decodeFromString(response.bodyAsText())
    }
}

suspend fun TelegramClient.sendMessage(chatId: Long, text: String): TelegramResponse<Message> {
    val params = mapOf("chat_id" to chatId.toString(), "text" to text)
    return sendRequest("sendMessage", params)
}

suspend fun TelegramClient.setWebhook(url: String): Boolean {
    val params = mapOf("url" to url)
    val response: TelegramResponse<Boolean> = sendRequest("setWebhook", params)
    return response.result == true
}