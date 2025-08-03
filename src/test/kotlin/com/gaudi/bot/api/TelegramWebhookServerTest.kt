package com.gaudi.bot.api

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.engine.mock.MockEngine.Companion.invoke
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString  
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebhookServerTest {

    private lateinit var mockTelegramClient: TelegramClient

    @Before
    fun setup() {
        val mockEngine = MockEngine {
            respond(
                content = """{"ok": true, "result": {"message_id": 12345, "chat": {"id": 67890, "type": "private"}, "date": ${System.currentTimeMillis() / 1000}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        // Use a regular TelegramClient that works with the mock engine
        mockTelegramClient = TelegramClient("test_token", HttpClient(mockEngine))
        
        // Set up in-memory database for memory system
        org.jetbrains.exposed.sql.Database.connect("jdbc:h2:mem:webhooktest;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver", user = "sa", password = "")
        
        // Initialize the memory system tables
        org.jetbrains.exposed.sql.transactions.transaction {
            org.jetbrains.exposed.sql.SchemaUtils.create(com.gaudi.bot.memory.MemoryThreads, com.gaudi.bot.memory.MemoryEntries)
        }
    }

    @Test
    fun `test webhook endpoint accepts update`() : Unit = runBlocking {
        testApplication {
            // Set up the test environment
            application {
                configureWebhookRouting(mockTelegramClient)
            }

            // Create a test update with a simple message that won't trigger complex processing
            val update = createTestUpdate("Hello, this is a test message")

            // Send a POST request to the webhook endpoint
            val response = client.post("/webhook") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(update))
            }

            assertTrue(response.status == HttpStatusCode.OK)
        }
    }

    @Test
    fun `test webhook handles command properly`() : Unit = runBlocking {
        testApplication {
            // Set up the test environment
            application {
                configureWebhookRouting(mockTelegramClient)
            }

            // Create updates with different commands
            val chatUpdate = createTestUpdate("/chat Tell me about Kotlin")
            val summaryUpdate = createTestUpdate("/summary Summarize this")

            // Test chat command
            val firstResponse = client.post("/webhook") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(chatUpdate))
            }

            assertTrue(firstResponse.status == HttpStatusCode.OK)

            // Test summary command
            val secondResponse = client.post("/webhook") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(summaryUpdate))
            }

            assertTrue(secondResponse.status == HttpStatusCode.OK)
        }
    }

    @Test
    fun `test webhook ignores non-command updates without mention`() = runBlocking {
        testApplication {
            // Set up the test environment
            application {
                configureWebhookRouting(mockTelegramClient)
            }

            // Create an update without a command or mention
            val regularUpdate = createTestUpdate("Just a regular message")

            // Send the update
            val response = client.post("/webhook") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(regularUpdate))
            }

            assertTrue(response.status == HttpStatusCode.OK)
        }
    }

    @Test
    fun `test webhook responds to bot mention`() = runBlocking {
        testApplication {
            // Set up the test environment with a bot username
            application {
                configureWebhookRouting(mockTelegramClient, "testbot")
            }

            // Create an update with a mention
            val mentionUpdate = createTestUpdateWithMention("Hey @testbot, how are you?")

            // Send the update
            val response = client.post("/webhook") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(mentionUpdate))
            }

            assertTrue(response.status == HttpStatusCode.OK)
        }
    }

    @Test
    fun `test webhook responds to reply to bot`() = runBlocking {
        testApplication {
            // Set up the test environment
            application {
                configureWebhookRouting(mockTelegramClient, "testbot", 987654321L)
            }

            // Create an update with a reply to the bot
            val replyUpdate = createTestUpdateWithReply("This is a reply to the bot")

            // Send the update
            val response = client.post("/webhook") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(replyUpdate))
            }

            assertTrue(response.status == HttpStatusCode.OK)
        }
    }

    @Test
    fun `test webhook handles errors gracefully`() = runBlocking {
        testApplication {
            // Set up the test environment
            application {
                configureWebhookRouting(mockTelegramClient)
            }

            // Create an invalid update to trigger error handling
            val invalidJson = """{"invalid": "json"}"""

            // Send the invalid update
            val response = client.post("/webhook") {
                contentType(ContentType.Application.Json)
                setBody(invalidJson)
            }

            // Verify we get an internal server error due to deserialization failure
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("Error processing update"))
        }
    }

    // Helper functions to create test updates

    private fun createTestUpdate(text: String): Update {
        return Update(
            update_id = 123456L,
            message = Message(
                message_id = 1L,
                chat = Chat(
                    id = 123456789L,
                    type = "private",
                    first_name = "Test",
                    last_name = "User",
                    username = "testuser"
                ),
                date = (System.currentTimeMillis() / 1000).toInt(),
                text = text,
                from = User(
                    id = 123456L,
                    is_bot = false,
                    first_name = "Test",
                    last_name = "User",
                    username = "testuser"
                )
            )
        )
    }

    private fun createTestUpdateWithMention(text: String): Update {
        return Update(
            update_id = 123456L,
            message = Message(
                message_id = 1L,
                chat = Chat(
                    id = 123456789L,
                    type = "private",
                    first_name = "Test",
                    last_name = "User",
                    username = "testuser"
                ),
                date = (System.currentTimeMillis() / 1000).toInt(),
                text = text,
                from = User(
                    id = 123456L,
                    is_bot = false,
                    first_name = "Test",
                    last_name = "User",
                    username = "testuser"
                ),
                entities = listOf(
                    MessageEntity(
                        type = "mention",
                        offset = 4,
                        length = 8
                    )
                )
            )
        )
    }

    private fun createTestUpdateWithReply(text: String): Update {
        return Update(
            update_id = 123456L,
            message = Message(
                message_id = 1L,
                chat = Chat(
                    id = 123456789L,
                    type = "private",
                    first_name = "Test",
                    last_name = "User",
                    username = "testuser"
                ),
                date = (System.currentTimeMillis() / 1000).toInt(),
                text = text,
                from = User(
                    id = 123456L,
                    is_bot = false,
                    first_name = "Test",
                    last_name = "User",
                    username = "testuser"
                ),
                reply_to_message = Message(
                    message_id = 2L,
                    chat = Chat(
                        id = 123456789L,
                        type = "private"
                    ),
                    date = (System.currentTimeMillis() / 1000).toInt() - 60,
                    from = User(
                        id = 987654321L, // Bot's ID
                        is_bot = true,
                        first_name = "Bot",
                        username = "testbot"
                    )
                )
            )
        )
    }

    // Helper extension function to configure the webhook routing
    private fun Application.configureWebhookRouting(
        client: TelegramClient,
        botUsername: String? = "testbot",
        botId: Long? = 987654321L
    ) {
        // Install ContentNegotiation for JSON serialization
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        
        // Set environment variables for testing
        System.setProperty("BOT_USERNAME", botUsername ?: "testbot")
        System.setProperty("BOT_ID", (botId ?: 987654321L).toString())
        
        val processor = MemoryEnabledMessageProcessor(client).apply {
            this.botUsername = botUsername ?: "testbot"
            this.botId = botId ?: 987654321L
        }

        routing {
            post("/webhook") {
                try {
                    val update = call.receive<Update>()
                    processor.processUpdate(update)
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error processing update: ${e.message}")
                }
            }
        }
    }
}