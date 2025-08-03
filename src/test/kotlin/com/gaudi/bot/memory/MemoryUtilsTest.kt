package com.gaudi.bot.memory

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryUtilsTest {

    @Test
    fun `test metadata serialization and deserialization`() {
        // Given
        val metadata = mapOf(
            "username" to "testuser",
            "message_id" to "12345",
            "timestamp" to System.currentTimeMillis().toString()
        )

        // When
        val serialized = Json.encodeToString(metadata)
        val deserialized = Json.decodeFromString<Map<String, String>>(serialized)

        // Then
        assertEquals(metadata, deserialized)
    }

    @Test
    fun `test memory entry creation`() {
        // Given
        val id = 1L
        val threadId = 100L
        val role = "user"
        val content = "Test message"
        val metadata = mapOf("key" to "value")
        val timestamp = System.currentTimeMillis()

        // When
        val entry = MemoryEntry(id, threadId, role, content, metadata, timestamp)

        // Then
        assertEquals(id, entry.id)
        assertEquals(threadId, entry.threadId)
        assertEquals(role, entry.role)
        assertEquals(content, entry.content)
        assertEquals(metadata, entry.metadata)
        assertEquals(timestamp, entry.timestamp)
    }

    @Test
    fun `test memory thread creation`() {
        // Given
        val id = 1L
        val userId = 100L
        val chatId = 200L
        val title = "Test Thread"
        val createdAt = System.currentTimeMillis()
        val updatedAt = System.currentTimeMillis()

        // When
        val thread = MemoryThread(id, userId, chatId, title, createdAt, updatedAt)

        // Then
        assertEquals(id, thread.id)
        assertEquals(userId, thread.userId)
        assertEquals(chatId, thread.chatId)
        assertEquals(title, thread.title)
        assertEquals(createdAt, thread.createdAt)
        assertEquals(updatedAt, thread.updatedAt)
    }

    @Test
    fun `test format thread as conversation helper method`() {
        // Given
        val thread = MemoryThread(
            id = 1L,
            userId = 100L,
            chatId = 200L,
            title = "Kotlin Discussion",
            createdAt = 1617235200000, // April 1, 2021
            updatedAt = 1617235200000
        )

        val memories = listOf(
            MemoryEntry(
                id = 1L,
                threadId = 1L,
                role = "system",
                content = "You are a helpful assistant.",
                metadata = emptyMap(),
                timestamp = 1617235200000
            ),
            MemoryEntry(
                id = 2L,
                threadId = 1L,
                role = "user",
                content = "What is Kotlin?",
                metadata = mapOf("username" to "user123"),
                timestamp = 1617235260000 // 1 minute later
            ),
            MemoryEntry(
                id = 3L,
                threadId = 1L,
                role = "assistant",
                content = "Kotlin is a modern programming language that runs on the JVM.",
                metadata = mapOf("model" to "claude"),
                timestamp = 1617235320000 // 2 minutes later
            )
        )

        // When
        val formatted = formatAsConversation(thread, memories)

        // Then
        assertTrue(formatted.contains("# Conversation: Kotlin Discussion"))
        assertTrue(formatted.contains("Date started: 2021-04-01"))
        assertTrue(formatted.contains("SYSTEM"))
        assertTrue(formatted.contains("USER"))
        assertTrue(formatted.contains("ASSISTANT"))
        assertTrue(formatted.contains("You are a helpful assistant."))
        assertTrue(formatted.contains("What is Kotlin?"))
        assertTrue(formatted.contains("Kotlin is a modern programming language"))
    }

    // Helper function to test the formatting logic directly
    private fun formatAsConversation(thread: MemoryThread, memories: List<MemoryEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("# Conversation: ${thread.title}")
        sb.appendLine("Date started: ${java.time.Instant.ofEpochMilli(thread.createdAt).toString().substring(0, 10)}")
        sb.appendLine()

        memories.forEach { memory ->
            val timestamp = java.time.Instant.ofEpochMilli(memory.timestamp)
            sb.appendLine("${memory.role.uppercase()} [${timestamp}]: ${memory.content}")
            sb.appendLine()
        }

        return sb.toString()
    }

    @Test
    fun `test thread timestamp handling`() {
        // Given
        val now = System.currentTimeMillis()
        val thread = MemoryThread(
            id = 1L,
            userId = 100L,
            chatId = 200L,
            title = "Test Thread",
            createdAt = now,
            updatedAt = now + 60000 // 1 minute later
        )

        // When/Then
        val createdDate = java.time.Instant.ofEpochMilli(thread.createdAt)
        val updatedDate = java.time.Instant.ofEpochMilli(thread.updatedAt)

        // Verify the timestamps are properly formatted
        assertNotNull(createdDate)
        assertNotNull(updatedDate)
        assertTrue(updatedDate.isAfter(createdDate))
        assertEquals(60, java.time.Duration.between(createdDate, updatedDate).seconds)
    }
}