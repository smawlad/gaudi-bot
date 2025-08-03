package com.gaudi.bot.api

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramClientTest {

    private lateinit var mockEngine: MockEngine
    private lateinit var client: TelegramClient
    private val testToken = "test_token"

    @Before
    fun setup() {
        mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            val responseContent = when {
                path.endsWith("/sendMessage") -> {
                    val chatId = request.url.parameters["chat_id"] ?: "0"
                    val text = request.url.parameters["text"] ?: ""
                    createMockSendMessageResponse(chatId.toLong(), text)
                }
                path.endsWith("/setWebhook") -> {
                    val url = request.url.parameters["url"] ?: ""
                    createMockSetWebhookResponse(url.isNotEmpty())
                }
                else -> {
                    """{"ok":false,"error_code":404,"description":"Not Found"}"""
                }
            }

            respond(
                content = responseContent,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        client = TelegramClient(testToken, HttpClient(mockEngine))
    }

    @Test
    fun `test send message`() = runBlocking {
        // Given
        val messageId = 42L
        val chatId = 123456789L
        val text = "Hello, World!"

        val params = ReplyParameters(messageId, chatId)

        // When
        val response = client.sendMessage(text, params)

        // Then
        assertTrue(response.ok)
        assertEquals(chatId, response.result?.chat?.id)
        assertEquals(text, response.result?.text)

        // Verify the request was correct
        val request = mockEngine.requestHistory.last()
        assertEquals("${client.baseUrl}/sendMessage", request.url.toString().split("?")[0])
        assertEquals(chatId.toString(), request.url.parameters["chat_id"])
        assertEquals(text, request.url.parameters["text"])
    }

    @Test
    fun `test set webhook with valid URL`() = runBlocking {
        // Given
        val webhookUrl = "https://example.com/webhook"

        // When
        val result = client.setWebhook(webhookUrl)

        // Then
        assertTrue(result)

        // Verify the request was correct
        val request = mockEngine.requestHistory.last()
        assertEquals("${client.baseUrl}/setWebhook", request.url.toString().split("?")[0])
        assertEquals(webhookUrl, request.url.parameters["url"])
    }

    @Test
    fun `test set webhook with invalid URL`() = runBlocking {
        // Given
        val webhookUrl = ""

        // When
        val result = client.setWebhook(webhookUrl)

        // Then
        assertFalse(result)
    }

    private fun createMockSendMessageResponse(chatId: Long, text: String): String {
        val message = Message(
            message_id = 1L,
            chat = Chat(
                id = chatId,
                type = "private",
                username = "test_user",
                first_name = "Test",
                last_name = "User"
            ),
            date = (System.currentTimeMillis() / 1000).toInt(),
            text = text,
            from = User(
                id = 123456L,
                is_bot = true,
                first_name = "Test Bot",
                username = "test_bot"
            )
        )

        val response = TelegramResponse(
            ok = true,
            result = message
        )

        return Json.encodeToString(response)
    }

    private fun createMockSetWebhookResponse(success: Boolean): String {
        val response = TelegramResponse<Boolean>(
            ok = true,
            result = success
        )

        return Json.encodeToString(response)
    }
}