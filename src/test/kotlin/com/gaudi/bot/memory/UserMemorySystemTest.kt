package com.gaudi.bot.memory

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserMemorySystemTest {

    private val testDbUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    private val testDbUser = "sa"
    private val testDbPassword = ""

    @Before
    fun setup() {
        // Initialize in-memory H2 database for testing
        Database.connect(testDbUrl, driver = "org.h2.Driver", user = testDbUser, password = testDbPassword)

        // Create tables
        transaction {
            SchemaUtils.create(MemoryThreads, MemoryEntries)
        }
    }

    @After
    fun tearDown() {
        // Drop tables
        transaction {
            SchemaUtils.drop(MemoryThreads, MemoryEntries)
        }
    }

    @Test
    fun `test create thread`() = runBlocking {
        // Given
        val userId = 12345L
        val chatId = 67890L
        val title = "Test Conversation"

        // When
        val threadId = UserMemorySystem.createThread(userId, chatId, title)

        // Then
        assertTrue(threadId > 0)

        val thread = UserMemorySystem.getThread(threadId)
        assertNotNull(thread)
        assertEquals(userId, thread.userId)
        assertEquals(chatId, thread.chatId)
        assertEquals(title, thread.title)
    }

    @Test
    fun `test add memory to thread`() = runBlocking {
        // Given
        val userId = 12345L
        val chatId = 67890L
        val threadId = UserMemorySystem.createThread(userId, chatId, "Test Thread")
        val role = "user"
        val content = "Hello, bot!"
        val metadata = mapOf("username" to "testuser", "message_id" to "123")

        // When
        val entryId = UserMemorySystem.addMemory(threadId, role, content, metadata)

        // Then
        assertTrue(entryId > 0)

        val memories = UserMemorySystem.getThreadMemories(threadId)
        assertEquals(1, memories.size)
        assertEquals(role, memories[0].role)
        assertEquals(content, memories[0].content)
        assertEquals(metadata, memories[0].metadata)
    }

    @Test
    fun `test get thread memories`() = runBlocking {
        // Given
        val userId = 12345L
        val chatId = 67890L
        val threadId = UserMemorySystem.createThread(userId, chatId, "Test Thread")

        // Add multiple memories in different roles
        UserMemorySystem.addMemory(threadId, "user", "Hello, bot!", mapOf("message_id" to "1"))
        UserMemorySystem.addMemory(threadId, "assistant", "Hello, human!", mapOf("message_id" to "2"))
        UserMemorySystem.addMemory(threadId, "user", "How are you?", mapOf("message_id" to "3"))

        // When
        val memories = UserMemorySystem.getThreadMemories(threadId)

        // Then
        assertEquals(3, memories.size)
        assertEquals("user", memories[0].role)
        assertEquals("Hello, bot!", memories[0].content)
        assertEquals("assistant", memories[1].role)
        assertEquals("Hello, human!", memories[1].content)
        assertEquals("user", memories[2].role)
        assertEquals("How are you?", memories[2].content)
    }

    @Test
    fun `test get latest thread`() = runBlocking {
        // Given
        val userId = 12345L
        val chatId = 67890L

        // Create multiple threads
        val oldThreadId = UserMemorySystem.createThread(userId, chatId, "Old Thread")
        Thread.sleep(10) // Ensure different timestamps
        val newThreadId = UserMemorySystem.createThread(userId, chatId, "New Thread")

        // When
        val latestThread = UserMemorySystem.getLatestThread(userId, chatId)

        // Then
        assertNotNull(latestThread)
        assertEquals(newThreadId, latestThread.id)
        assertEquals("New Thread", latestThread.title)
    }

    @Test
    fun `test get latest thread when none exists`() = runBlocking {
        // When
        val nonExistentThread = UserMemorySystem.getLatestThread(99999L, 99999L)

        // Then
        assertNull(nonExistentThread)
    }

    @Test
    fun `test get thread by id`() = runBlocking {
        // Given
        val userId = 12345L
        val chatId = 67890L
        val title = "Test Thread"
        val threadId = UserMemorySystem.createThread(userId, chatId, title)

        // When
        val thread = UserMemorySystem.getThread(threadId)

        // Then
        assertNotNull(thread)
        assertEquals(threadId, thread.id)
        assertEquals(userId, thread.userId)
        assertEquals(chatId, thread.chatId)
        assertEquals(title, thread.title)
    }

    @Test
    fun `test get thread by id when not exists`() = runBlocking {
        // When
        val nonExistentThread = UserMemorySystem.getThread(99999L)

        // Then
        assertNull(nonExistentThread)
    }

    @Test
    fun `test list user threads`() = runBlocking {
        // Given
        val userId = 12345L
        val chatId1 = 67890L
        val chatId2 = 67891L

        // Create multiple threads for the same user
        UserMemorySystem.createThread(userId, chatId1, "Thread 1")
        UserMemorySystem.createThread(userId, chatId2, "Thread 2")
        UserMemorySystem.createThread(userId, chatId1, "Thread 3")

        // Create a thread for a different user
        UserMemorySystem.createThread(99999L, chatId1, "Other User's Thread")

        // When
        val userThreads = UserMemorySystem.listUserThreads(userId)

        // Then
        assertEquals(3, userThreads.size)
        // Should contain all the threads we created for this user
        val threadTitles = userThreads.map { it.title }.toSet()
        assertTrue(threadTitles.contains("Thread 1"))
        assertTrue(threadTitles.contains("Thread 2"))
        assertTrue(threadTitles.contains("Thread 3"))
    }

    @Test
    fun `test update thread title`() = runBlocking {
        // Given
        val userId = 12345L
        val chatId = 67890L
        val threadId = UserMemorySystem.createThread(userId, chatId, "Original Title")
        val newTitle = "Updated Title"

        // When
        val updateResult = UserMemorySystem.updateThreadTitle(threadId, newTitle)

        // Then
        assertTrue(updateResult)

        val updatedThread = UserMemorySystem.getThread(threadId)
        assertNotNull(updatedThread)
        assertEquals(newTitle, updatedThread.title)
    }

    @Test
    fun `test update thread title when thread doesn't exist`() = runBlocking {
        // When
        val updateResult = UserMemorySystem.updateThreadTitle(99999L, "New Title")

        // Then
        assertEquals(false, updateResult)
    }

    @Test
    fun `test format thread as conversation`() = runBlocking {
        // Given
        val userId = 12345L
        val chatId = 67890L
        val threadId = UserMemorySystem.createThread(userId, chatId, "Test Conversation")

        // Add messages to simulate a conversation
        UserMemorySystem.addMemory(threadId, "system", "You are a helpful assistant.")
        UserMemorySystem.addMemory(threadId, "user", "Hello, can you help me?")
        UserMemorySystem.addMemory(threadId, "assistant", "Of course! What do you need help with?")
        UserMemorySystem.addMemory(threadId, "user", "I'm trying to learn Kotlin.")

        // When
        val formattedConversation = UserMemorySystem.formatThreadAsConversation(threadId)

        // Then
        assertTrue(formattedConversation.contains("# Conversation: Test Conversation"))
        assertTrue(formattedConversation.contains("SYSTEM"))
        assertTrue(formattedConversation.contains("USER"))
        assertTrue(formattedConversation.contains("ASSISTANT"))
        assertTrue(formattedConversation.contains("You are a helpful assistant."))
        assertTrue(formattedConversation.contains("Hello, can you help me?"))
        assertTrue(formattedConversation.contains("Of course! What do you need help with?"))
        assertTrue(formattedConversation.contains("I'm trying to learn Kotlin."))
    }
}