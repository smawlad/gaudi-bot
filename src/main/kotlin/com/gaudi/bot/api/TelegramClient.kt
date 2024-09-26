package com.gaudi.bot.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json

class TelegramClient(token: String, val httpClient: HttpClient) {
    val baseUrl = "https://api.telegram.org/bot$token"

    suspend inline fun <reified T> sendRequest(method: String, params: Map<String, String>): T {
        val response: HttpResponse = httpClient.get("$baseUrl/$method") {
            params.forEach { (key, value) -> parameter(key, value) }
        }
        return Json.decodeFromString(response.bodyAsText())
    }
}

suspend fun TelegramClient.sendMessage(chatId: Long, text: String): TelegramResponse<Message> {
    val params = mapOf("chat_id" to chatId.toString(), "text" to text)
    return sendRequest("sendMessage", params)
}