package com.gaudi.bot.ai

import com.gaudi.bot.ai.prompts.PromptManager
import com.gaudi.bot.memory.MemoryEntry
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

data class LLMConfig(
    val modelType: ModelType,
    val anthropicApiKey: String = System.getenv("ANTHROPIC_API_KEY") ?: "",
    val openAIApiKey: String = System.getenv("OPENAI_API_KEY") ?: ""
)

enum class ModelType {
    CLAUDE,
    OPENAI
}

data class LLMResponse(
    val content: String,
    val metadata: Map<String, Any> = emptyMap()
)

class LLM(private val config: LLMConfig) {
    // Create conversation manager for history handling
    private val conversationManager = ConversationManager()

    // Create client based on model type
    private val client: BaseLMClient = when (config.modelType) {
        ModelType.CLAUDE -> AnthropicClient(config.anthropicApiKey, conversationManager)
        ModelType.OPENAI -> OpenAIClient(config.openAIApiKey, conversationManager)
    }

    suspend fun generateResponse(prompt: String): String {
        val response = client.generateResponse(prompt)
        return response.content
    }

    fun resetConversation() {
        conversationManager.resetHistory()
    }

    fun loadConversation(threadMemories: List<MemoryEntry>) {
        conversationManager.loadFromThread(threadMemories)
    }
}

// Conversation manager to handle history
class ConversationManager {
    private val conversationHistory = mutableListOf<Message>()

    data class Message(val role: String, val content: String)

    // Initialize with default introduction if needed
    init {
        // You can initialize with a default message if needed
    }

    fun getMessages(): List<Message> {
        return conversationHistory.toList()
    }

    fun addUserMessage(content: String) {
        conversationHistory.add(Message("user", content))
    }

    fun addAssistantMessage(content: String) {
        conversationHistory.add(Message("assistant", content))
    }

    fun resetHistory() {
        conversationHistory.clear()
        // Re-initialize with introduction if needed
        val introduction = PromptManager.getPrompt("whoami")?.template
        if (introduction != null) {
            addAssistantMessage(introduction)
        }
    }

    fun loadFromThread(threadMemories: List<MemoryEntry>) {
        resetHistory()

        threadMemories.forEach { memory ->
            when (memory.role) {
                "user" -> addUserMessage(memory.content)
                "assistant" -> addAssistantMessage(memory.content)
                // Skip system messages as they're handled separately
            }
        }
    }

    fun toJsonArray(): JsonArray {
        return JsonArray(conversationHistory.map { message ->
            JsonObject(mapOf(
                "role" to JsonPrimitive(message.role),
                "content" to JsonPrimitive(message.content)
            ))
        })
    }
}

// Abstract base client
abstract class BaseLMClient(protected val conversationManager: ConversationManager) {
    var client = HttpClient(CIO) {
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
    protected abstract suspend fun parseResponse(response: HttpResponse): LLMResponse

    suspend fun generateResponse(prompt: String): LLMResponse {
        // Add user message to conversation history
        conversationManager.addUserMessage(prompt)

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

        val llmResponse = parseResponse(response)

        // Add assistant response to conversation history
        conversationManager.addAssistantMessage(llmResponse.content)

        return llmResponse
    }
}

// Anthropic implementation
class AnthropicClient(
    apiKey: String,
    conversationManager: ConversationManager
) : BaseLMClient(conversationManager) {
    override val baseUrl = "https://api.anthropic.com/v1/messages"
    override val model = "claude-3-7-sonnet-latest"
    override val headers = mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to "2023-06-01"
    )

    // Initialize with Gaudí's introduction if not already done in conversation manager
    init {
        if (conversationManager.getMessages().isEmpty()) {
            conversationManager.addAssistantMessage(getGaudiIntroduction())
        }
    }

    private fun getGaudiIntroduction(): String {
        return PromptManager.getPrompt("whoami")?.template ?: ""
    }

    override fun buildRequestBody(prompt: String): JsonObject {
        return JsonObject(mapOf(
            "model" to JsonPrimitive(model),
            "system" to JsonPrimitive(PromptManager.getPrompt("gaudi")?.template ?: ""),
            "messages" to conversationManager.toJsonArray(),
            "max_tokens" to JsonPrimitive(1024)
        ))
    }

    override suspend fun parseResponse(response: HttpResponse): LLMResponse {
        val responseBody = response.bodyAsText()
        val responseText = Json.parseToJsonElement(responseBody)
            .jsonObject["content"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            ?: throw RuntimeException("Invalid response format")

        return LLMResponse(responseText)
    }
}

// OpenAI implementation
class OpenAIClient(
    apiKey: String,
    conversationManager: ConversationManager
) : BaseLMClient(conversationManager) {
    override val baseUrl = "https://api.openai.com/v1/chat/completions"
    override val model = "gpt-4-turbo"
    override val headers = mapOf(
        "Authorization" to "Bearer $apiKey"
    )

    override fun buildRequestBody(prompt: String): JsonObject {
        // Create messages array from conversation history
        return JsonObject(mapOf(
            "model" to JsonPrimitive(model),
            "messages" to JsonArray(
                listOf(
                    // Add system message if available
                    JsonObject(mapOf(
                        "role" to JsonPrimitive("system"),
                        "content" to JsonPrimitive(PromptManager.getPrompt("gaudi")?.template ?: "")
                    ))
                ) + conversationManager.getMessages().map { message ->
                    JsonObject(mapOf(
                        "role" to JsonPrimitive(message.role),
                        "content" to JsonPrimitive(message.content)
                    ))
                }
            ),
            "max_tokens" to JsonPrimitive(1024)
        ))
    }

    override suspend fun parseResponse(response: HttpResponse): LLMResponse {
        val responseBody = response.bodyAsText()
        val responseText = Json.parseToJsonElement(responseBody)
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

        return LLMResponse(responseText)
    }
}