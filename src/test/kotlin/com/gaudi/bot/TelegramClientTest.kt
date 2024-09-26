package com.gaudi.bot

import com.gaudi.bot.api.TelegramClient
import com.gaudi.bot.api.sendMessage
import com.gaudi.bot.api.Message
import com.gaudi.bot.api.Chat
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TelegramClientTest {

    private val token = "mock-token"
    private lateinit var client: TelegramClient
    private lateinit var mockEngine: MockEngine

    @BeforeTest
    fun setup() {
        mockEngine = MockEngine { request ->
            respond(
                content = """
                    {
                      "ok": true,
                      "result": ${Json.encodeToString(Message(1L, Chat(123456789L, "private"), 1234567890, "Hello, World!"))}
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        client = TelegramClient(token, HttpClient(mockEngine))
    }

    @Test
    fun `sendMessage sends the correct request`() = runBlocking {
        val chatId = 123456789L
        val text = "Hello, World!"

        // Call the method under test
        val response = client.sendMessage(chatId, text)

        // Assert the response contains the correct message
        assertEquals(text, response.result?.text)
        assertEquals(chatId, response.result?.chat?.id)

        // Verify the request made by the client
        val request = mockEngine.requestHistory.last()

        // Assert that the correct URL was hit
        assertEquals("https://api.telegram.org/bot$token/sendMessage", request.url.protocolWithAuthority + request.url.encodedPath)

        // Verify the request parameters
        assertEquals(chatId.toString(), request.url.parameters["chat_id"])
        assertEquals(text, request.url.parameters["text"])
    }
}