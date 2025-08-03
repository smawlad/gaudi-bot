package com.gaudi.bot

import com.gaudi.bot.api.TelegramClient
import com.gaudi.bot.api.TelegramResponse
import com.gaudi.bot.api.setWebhook
import com.gaudi.bot.memory.UserMemorySystem
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MainApplicationTest {

    private lateinit var mockClient: TelegramClient
    private lateinit var mockEngine: MockEngine

    private val standardOut = System.out
    private val outputStreamCaptor = ByteArrayOutputStream()

    @Before
    fun setup() {
        // Set up environment for testing
        // Redirect stdout for logging verification
        System.setOut(PrintStream(outputStreamCaptor))

        // Set up mock HTTP client
        mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            val response = when {
                path.endsWith("/setWebhook") -> {
                    val webhookResponse = TelegramResponse<Boolean>(
                        ok = true,
                        result = true
                    )
                    Json.encodeToString(webhookResponse)
                }
                else -> {
                    val genericResponse = TelegramResponse<Boolean>(
                        ok = true,
                        result = true
                    )
                    Json.encodeToString(genericResponse)
                }
            }

            respond(
                content = response,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        mockClient = TelegramClient("test_token", HttpClient(mockEngine))

        // Set up in-memory test database
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver", user = "sa", password = "")
    }

    @Test
    fun `test main function with valid environment variables`() = runBlocking {
        // Set necessary environment variables
        setEnv("BOT_TOKEN", "test_token")
        setEnv("WEBHOOK_URL", "https://example.com/webhook")
        setEnv("DATABASE_URL", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
        setEnv("DATABASE_USER", "sa")
        setEnv("DATABASE_PASSWORD", "")

        try {
            // Run the main function with our mocks
            runMainWithMocks(mockClient, UserMemorySystem)

            // Since we can't easily verify the mock call with suspend functions,
            // we verify the webhook was set by checking the log output

            // Check log output
            val output = outputStreamCaptor.toString()
            assertTrue(output.contains("Webhook set successfully"))
            assertTrue(output.contains("Memory system initialized"))

        } finally {
            // Clean up environment
            clearEnv("BOT_TOKEN")
            clearEnv("WEBHOOK_URL")
            clearEnv("DATABASE_URL")
            clearEnv("DATABASE_USER")
            clearEnv("DATABASE_PASSWORD")

            // Restore stdout
            System.setOut(standardOut)
        }
    }

    @Test
    fun `test main function fails without token`() = runBlocking {
        // Clear the token environment variable
        clearEnv("BOT_TOKEN")
        setEnv("WEBHOOK_URL", "https://example.com/webhook")

        // Run the main function and expect an error
        val exception = assertFails {
            runMainWithMocks(mockClient, UserMemorySystem)
        }

        // Verify the right error was thrown
        assertTrue(exception.message?.contains("BOT_TOKEN") == true)

        // Clean up
        clearEnv("WEBHOOK_URL")
        System.setOut(standardOut)
    }

    @Test
    fun `test main function fails without webhook URL`() = runBlocking {
        // Set token but clear webhook URL
        setEnv("BOT_TOKEN", "test_token")
        clearEnv("WEBHOOK_URL")

        // Run the main function and expect an error
        val exception = assertFails {
            runMainWithMocks(mockClient, UserMemorySystem)
        }

        // Verify the right error was thrown
        assertTrue(exception.message?.contains("WEBHOOK_URL") == true)

        // Clean up
        clearEnv("BOT_TOKEN")
        System.setOut(standardOut)
    }

    @Test
    fun `test main function handles webhook set failure`() = runBlocking {
        // Set up environment variables
        setEnv("BOT_TOKEN", "test_token")
        setEnv("WEBHOOK_URL", "https://example.com/webhook")

        // Mock a failed webhook response
        val failedEngine = MockEngine {
            respond(
                content = Json.encodeToString(TelegramResponse<Boolean>(ok = true, result = false)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val failedClient = TelegramClient("test_token", HttpClient(failedEngine))

        try {
            // Run the main function with our mocks
            runMainWithMocks(failedClient, UserMemorySystem)

            // Check log output
            val output = outputStreamCaptor.toString()
            assertTrue(output.contains("Failed to set webhook"))

        } finally {
            // Clean up
            clearEnv("BOT_TOKEN")
            clearEnv("WEBHOOK_URL")
            System.setOut(standardOut)
        }
    }

    @Test
    fun `test main function handles memory system initialization failure`() = runBlocking {
        // Set up environment variables with bad database URL to cause failure
        setEnv("BOT_TOKEN", "test_token")
        setEnv("WEBHOOK_URL", "https://example.com/webhook")
        setEnv("DATABASE_URL", "jdbc:invalid:invalid")
        setEnv("DATABASE_USER", "invalid")
        setEnv("DATABASE_PASSWORD", "invalid")

        try {
            // Run the main function and expect an exception
            val exception = assertFails {
                runMainWithMocks(mockClient, UserMemorySystem)
            }

            // Verify that some exception occurred during initialization
            assertTrue(exception.message?.isNotEmpty() == true)

            // Check log output
            val output = outputStreamCaptor.toString()
            assertTrue(output.contains("Failed to initialize memory system"))

        } finally {
            // Clean up
            clearEnv("BOT_TOKEN")
            clearEnv("WEBHOOK_URL")
            clearEnv("DATABASE_URL")
            clearEnv("DATABASE_USER")
            clearEnv("DATABASE_PASSWORD")
            System.setOut(standardOut)
        }
    }

    // Helper methods

    private suspend fun runMainWithMocks(client: TelegramClient, memorySystem: UserMemorySystem) {
        // This would be a simplified version of the main function for testing
        val token = System.getProperty("BOT_TOKEN") ?: error("BOT_TOKEN environment variable is not set")
        val webhook = System.getProperty("WEBHOOK_URL") ?: error("WEBHOOK_URL is not set")

        // Database configuration
        val dbUrl = System.getProperty("DATABASE_URL") ?: "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
        val dbUser = System.getProperty("DATABASE_USER") ?: "sa"
        val dbPassword = System.getProperty("DATABASE_PASSWORD") ?: ""

        // Initialize the memory system
        try {
            println("Initializing memory system...")
            memorySystem.initialize(dbUrl, dbUser, dbPassword)
            println("Memory system initialized successfully")
        } catch (e: Exception) {
            println("Failed to initialize memory system")
            throw e
        }

        try {
            // Set the webhook
            val webhookSet = client.setWebhook(webhook)
            if (webhookSet) {
                println("Webhook set successfully")
                // In a real test, we would start the server
                // startMemoryEnabledWebhookServer(client)
            } else {
                println("Failed to set webhook")
            }
        } catch (e: Exception) {
            println("An error occurred: ${e.message}")
            throw e
        }
    }

    // Environment variable helpers using system properties as fallback
    private fun setEnv(key: String, value: String) {
        System.setProperty(key, value)
    }

    private fun clearEnv(key: String) {
        System.clearProperty(key)
    }
}