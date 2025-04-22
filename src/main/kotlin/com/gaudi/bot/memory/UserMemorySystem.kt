package com.gaudi.bot.memory

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * User Memory System that stores conversation history and context
 * for each user across different chats
 */
object UserMemorySystem {
    // Initialize the database connection
    fun initialize(jdbcUrl: String, username: String, password: String) {
        Database.connect(jdbcUrl, driver = "org.postgresql.Driver", user = username, password = password)

        transaction {
            // Create tables if they don't exist
            SchemaUtils.create(MemoryThreads, MemoryEntries)
        }

        logger.info { "Memory system initialized" }
    }
    /**
     * Creates a new memory thread for a user
     */
    suspend fun createThread(userId: Long, chatId: Long, title: String): Long = withContext(Dispatchers.IO) {
        transaction {
            MemoryThreads.insert {
                it[MemoryThreads.userId] = userId
                it[MemoryThreads.chatId] = chatId
                it[MemoryThreads.title] = title
                it[createdAt] = System.currentTimeMillis()
                it[updatedAt] = System.currentTimeMillis()
            }[MemoryThreads.id].value
        }.toLong()
    }

    /**
     * Adds a memory entry to a thread
     */
    suspend fun addMemory(threadId: Long, role: String, content: String, metadata: Map<String, String> = emptyMap()): Long =
        withContext(Dispatchers.IO) {
            transaction {
                // Update the thread's updatedAt timestamp
                MemoryThreads.update({ MemoryThreads.id eq threadId }) {
                    it[updatedAt] = System.currentTimeMillis()
                }

                // Insert the new memory entry
                MemoryEntries.insert {
                    it[MemoryEntries.threadId] = threadId
                    it[MemoryEntries.role] = role
                    it[MemoryEntries.content] = content
                    it[MemoryEntries.metadata] = Json.encodeToString(metadata)
                    it[timestamp] = System.currentTimeMillis()
                }[MemoryEntries.id].value
            }.toLong()
        }

    /**
     * Gets all memory entries for a thread
     */
    suspend fun getThreadMemories(threadId: Long): List<MemoryEntry> = withContext(Dispatchers.IO) {
        transaction {
            MemoryEntries.selectAll()
                .where { MemoryEntries.threadId eq threadId }
                .orderBy(MemoryEntries.timestamp)
                .map {
                    MemoryEntry(
                        id = it[MemoryEntries.id].value,
                        threadId = it[MemoryEntries.threadId],
                        role = it[MemoryEntries.role],
                        content = it[MemoryEntries.content],
                        metadata = Json.decodeFromString(it[MemoryEntries.metadata]),
                        timestamp = it[MemoryEntries.timestamp]
                    )
                }
        }
    }

    /**
     * Gets the most recent thread for a user in a chat
     */
    suspend fun getLatestThread(userId: Long, chatId: Long): MemoryThread? = withContext(Dispatchers.IO) {
        transaction {
            MemoryThreads.selectAll()
                .where { (MemoryThreads.userId eq userId) and (MemoryThreads.chatId eq chatId) }
                .orderBy(MemoryThreads.updatedAt, SortOrder.DESC)
                .limit(1)
                .map {
                    MemoryThread(
                        id = it[MemoryThreads.id].value,
                        userId = it[MemoryThreads.userId],
                        chatId = it[MemoryThreads.chatId],
                        title = it[MemoryThreads.title],
                        createdAt = it[MemoryThreads.createdAt],
                        updatedAt = it[MemoryThreads.updatedAt]
                    )
                }
                .firstOrNull()
        }
    }

    /**
     * Gets a specific thread by ID
     */
    suspend fun getThread(threadId: Long): MemoryThread? = withContext(Dispatchers.IO) {
        transaction {
            MemoryThreads.selectAll()
                .where { MemoryThreads.id eq threadId }
                .map {
                    MemoryThread(
                        id = it[MemoryThreads.id].value,
                        userId = it[MemoryThreads.userId],
                        chatId = it[MemoryThreads.chatId],
                        title = it[MemoryThreads.title],
                        createdAt = it[MemoryThreads.createdAt],
                        updatedAt = it[MemoryThreads.updatedAt]
                    )
                }
                .firstOrNull()
        }
    }

    /**
     * Lists all threads for a user
     */
    suspend fun listUserThreads(userId: Long): List<MemoryThread> = withContext(Dispatchers.IO) {
        transaction {
            MemoryThreads.selectAll()
                .where { MemoryThreads.userId eq userId }
                .orderBy(MemoryThreads.updatedAt, SortOrder.DESC)
                .map {
                    MemoryThread(
                        id = it[MemoryThreads.id].value,
                        userId = it[MemoryThreads.userId],
                        chatId = it[MemoryThreads.chatId],
                        title = it[MemoryThreads.title],
                        createdAt = it[MemoryThreads.createdAt],
                        updatedAt = it[MemoryThreads.updatedAt]
                    )
                }
        }
    }

    /**
     * Updates the title of a thread
     */
    suspend fun updateThreadTitle(threadId: Long, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        transaction {
            val updatedRows = MemoryThreads.update({ MemoryThreads.id eq threadId }) {
                it[title] = newTitle
                it[updatedAt] = System.currentTimeMillis()
            }
            updatedRows > 0
        }
    }

    /**
     * Formats thread memories as a conversation for AI context
     */
    suspend fun formatThreadAsConversation(threadId: Long): String = withContext(Dispatchers.IO) {
        val memories = getThreadMemories(threadId)
        val thread = getThread(threadId)

        val sb = StringBuilder()
        sb.appendLine("# Conversation: ${thread?.title ?: "Unknown Thread"}")
        sb.appendLine("Date started: ${Instant.ofEpochMilli(thread?.createdAt ?: 0)}")
        sb.appendLine()

        memories.forEach { memory ->
            val timestamp = Instant.ofEpochMilli(memory.timestamp)
            sb.appendLine("${memory.role.uppercase()} [${timestamp}]: ${memory.content}")
            sb.appendLine()
        }

        sb.toString()
    }
}

/**
 * Database tables
 */
object MemoryThreads : LongIdTable() {
    val userId = long("user_id")
    val chatId = long("chat_id")
    val title = varchar("title", 255)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    init {
        index(true, userId, chatId, createdAt)
    }
}

object MemoryEntries : LongIdTable() {
    val threadId = long("thread_id").references(MemoryThreads.id)
    val role = varchar("role", 50)  // "user", "assistant", "system", etc.
    val content = text("content")
    val metadata = text("metadata")  // JSON string with additional data
    val timestamp = long("timestamp")

    init {
        index(false, threadId, timestamp)
    }
}

/**
 * Data classes for memory entities
 */
@Serializable
data class MemoryThread(
    val id: Long,
    val userId: Long,
    val chatId: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class MemoryEntry(
    val id: Long,
    val threadId: Long,
    val role: String,
    val content: String,
    val metadata: Map<String, String>,
    val timestamp: Long
)