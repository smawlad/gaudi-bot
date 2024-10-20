package com.gaudi.bot.ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

enum class ModelType {
    CLAUDE,
    OPENAI
}

class LMWrapper(
    private val modelType: ModelType,
    private val anthropicApiKey: String = System.getenv("ANTHROPIC_API_KEY") ?: "",
    private val openAIApiKey: String = System.getenv("OPENAI_API_KEY") ?: ""
) {
    private val client: BaseLMClient = when (modelType) {
        ModelType.CLAUDE -> AnthropicClient(anthropicApiKey)
        ModelType.OPENAI -> OpenAIClient(openAIApiKey)
    }

    suspend fun generateResponse(prompt: String): String {
        return client.generateResponse(prompt)
    }
}

abstract class BaseLMClient(protected val apiKey: String) {
    protected val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    protected abstract val baseUrl: String
    protected abstract val model: String
    protected abstract val headers: Map<String, String>

    protected abstract fun buildRequestBody(prompt: String): JsonObject
    protected abstract suspend fun parseResponse(response: HttpResponse): String


    suspend fun generateResponse(prompt: String): String {
        val response = client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            headers {
                this@BaseLMClient.headers.forEach { (key, value) ->
                    append(key, value)
                }
            }
            setBody(buildRequestBody(prompt))
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Unexpected code ${response.status}")
        }

        return parseResponse(response)
    }
}

class AnthropicClient(apiKey: String) : BaseLMClient(apiKey) {
    override val baseUrl = "https://api.anthropic.com/v1/messages"
    override val model = "claude-3-sonnet-20240229"
    override val headers = mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to "2023-06-01"
    )

    override fun buildRequestBody(prompt: String) = JsonObject(mapOf(
        "model" to JsonPrimitive(model),
        "messages" to JsonArray(listOf(JsonObject(mapOf(
            "role" to JsonPrimitive("user"),
            "content" to JsonPrimitive(prompt)
        )))),
        "max_tokens" to JsonPrimitive(1024)
    ))

    override suspend fun parseResponse(response: HttpResponse): String {
        val responseBody = response.bodyAsText()
        return Json.parseToJsonElement(responseBody)
            .jsonObject["content"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            ?: throw RuntimeException("Invalid response format")
    }
}

class OpenAIClient(apiKey: String) : BaseLMClient(apiKey) {
    override val baseUrl = "https://api.openai.com/v1/chat/completions"
    override val model = "gpt-4-turbo"
    override val headers = mapOf(
        "Authorization" to "Bearer $apiKey"
    )

    override fun buildRequestBody(prompt: String) = JsonObject(mapOf(
        "model" to JsonPrimitive(model),
        "messages" to JsonArray(listOf(JsonObject(mapOf(
            "role" to JsonPrimitive("user"),
            "content" to JsonPrimitive(prompt)
        )))),
        "max_tokens" to JsonPrimitive(1024)
    ))

    override suspend fun parseResponse(response: HttpResponse): String {
        val responseBody = response.bodyAsText()
        return Json.parseToJsonElement(responseBody)
            .jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.content
            ?: throw RuntimeException("Invalid response format")
    }
}