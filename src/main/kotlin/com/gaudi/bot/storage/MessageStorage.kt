package com.gaudi.bot.storage

import com.gaudi.bot.api.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Storage system for retaining message history from chats
 * This allows us to perform operations like summarys even when Telegram API
 * limitations prevent accessing full history.
 */
object MessageStorage {

    /**
     * Initialize the database tables for message storage
     */
    fun initialize(jdbcUrl: String, username: String, password: String) {
        Database.connect(jdbcUrl, driver = "org.postgresql.Driver", user = username, password = password)

        transaction {
            SchemaUtils.create(StoredMessages, StoredUsers, StoredChats)
        }

        logger.info { "Message storage system initialized" }
    }

    /**
     * Store a new message in the database
     */
    suspend fun storeMessage(update: Update): Long = withContext(Dispatchers.IO) {
        transaction {
            val msg = update.message ?: update.edited_message!!
            // First ensure the chat exists in our storage
            val chatId = ensureChatExists(msg.chat)

            // Then ensure the user exists (if present)
            val userId = msg.from?.let { ensureUserExists(it) }


            // Store the message
            val newMessage = StoredMessageEntity.new {
                this.updateId = update.update_id
                this.messageId = msg.message_id
                this.chatId = chatId
                this.userId = userId
                this.date = msg.date
                this.text = msg.text
                this.content = Json.encodeToString(update.message)
            }

            newMessage.id.value
        }
    }

    /**
     * Store multiple messages in a batch operation
     */
    suspend fun storeMessages(updates: List<Update>): Int = withContext(Dispatchers.IO) {
        transaction {
            // Process messages in chunks to avoid transaction issues
            updates.chunked(100).sumOf { chunk ->
                // Process each message in the chunk
                chunk.count { update ->
                    try {
                        val message = update.message!!
                        // First ensure the chat exists in our storage
                        val chatId = ensureChatExists(message.chat)

                        // Then ensure the user exists (if present)
                        val userId = message.from?.let { ensureUserExists(it) }

                        // Store the message - using insert to avoid overhead of entity creation
                        StoredMessages.insert {
                            it[messageId] = message.message_id
                            it[updateId] = update.update_id
                            it[StoredMessages.chatId] = chatId
                            it[StoredMessages.userId] = userId
                            it[date] = message.date
                            it[text] = message.text
                            it[content] = Json.encodeToString(message)
                        }
                        true
                    } catch (e: Exception) {
                        logger.error(e) { "Error storing message ${update.update_id}" }
                        false
                    }
                }
            }
        }
    }

    /**
     * Retrieve messages for a specified chat, filtered by timestamp
     */
    suspend fun getMessagesForChat(chatId: Long, fromTimestamp: Int): List<Message> = withContext(Dispatchers.IO) {
        transaction {
            // Find the stored chat ID (internal DB ID, not Telegram chat ID)
            val storedChat = StoredChatEntity.find { StoredChats.telegramId eq chatId }.firstOrNull()
                ?: return@transaction emptyList()

            // Get messages for this chat after the specified timestamp
            StoredMessageEntity.find {
                (StoredMessages.chatId eq storedChat.id) and (StoredMessages.date greaterEq fromTimestamp)
            }.orderBy(StoredMessages.date to SortOrder.ASC)
                .map {
                    // Parse the stored JSON back to Message objects
                    try {
                        Json.decodeFromString<Message>(it.content)
                    } catch (e: Exception) {
                        logger.error(e) { "Error parsing stored message ${it.id}" }
                        null
                    }
                }
                .filterNotNull()
        }
    }

    /**
     * Get message count statistics for a chat
     */
    suspend fun getMessageStats(chatId: Long): Map<String, Int> = withContext(Dispatchers.IO) {
        transaction {
            // Find the stored chat ID
            val storedChat = StoredChatEntity.find { StoredChats.telegramId eq chatId }.firstOrNull()
                ?: return@transaction emptyMap()

            // Calculate statistics
            val totalCount = StoredMessageEntity.find { StoredMessages.chatId eq storedChat.id }.count()

            // Get count for the last 24 hours
            val dayAgo = Instant.now().minusSeconds(24 * 60 * 60).epochSecond.toInt()
            val last24HoursCount = StoredMessageEntity.find {
                (StoredMessages.chatId eq storedChat.id) and (StoredMessages.date greaterEq dayAgo)
            }.count()

            // Get count for the last week
            val weekAgo = Instant.now().minusSeconds(7 * 24 * 60 * 60).epochSecond.toInt()
            val lastWeekCount = StoredMessageEntity.find {
                (StoredMessages.chatId eq storedChat.id) and (StoredMessages.date greaterEq weekAgo)
            }.count()

            mapOf(
                "total" to totalCount.toInt(),
                "last24Hours" to last24HoursCount.toInt(),
                "lastWeek" to lastWeekCount.toInt()
            )
        }
    }

    /**
     * Get most active users in a chat
     */
    suspend fun getMostActiveUsers(chatId: Long, limit: Int = 5): List<Pair<User, Int>> = withContext(Dispatchers.IO) {
        transaction {
            // Find the stored chat ID
            val storedChat = StoredChatEntity.find { StoredChats.telegramId eq chatId }.firstOrNull()
                ?: return@transaction emptyList()

            // Use SQL grouping to count messages per user
            val userCounts = StoredMessages
                .innerJoin(StoredUsers, { userId }, { id })
                .slice(StoredUsers.id, StoredUsers.content, StoredMessages.id.count())
                .select { StoredMessages.chatId eq storedChat.id }
                .groupBy(StoredUsers.id, StoredUsers.content)
                .orderBy(StoredMessages.id.count(), SortOrder.DESC)
                .limit(limit)
                .map {
                    val user = try {
                        Json.decodeFromString<User>(it[StoredUsers.content])
                    } catch (e: Exception) {
                        logger.error(e) { "Error parsing stored user" }
                        null
                    }
                    val count = it[StoredMessages.id.count()].toInt()
                    if (user != null) Pair(user, count) else null
                }
                .filterNotNull()

            userCounts
        }
    }

    // Helper function to ensure a chat exists in the database
    private fun Transaction.ensureChatExists(chat: Chat): EntityID<Long> {
        val existingChat = StoredChatEntity.find { StoredChats.telegramId eq chat.id }.firstOrNull()

        return existingChat?.id ?: StoredChatEntity.new {
            telegramId = chat.id
            type = chat.type
            title = chat.title
            username = chat.username
            content = Json.encodeToString(chat)
        }.id
    }

    // Helper function to ensure a user exists in the database
    private fun Transaction.ensureUserExists(user: User): EntityID<Long> {
        val existingUser = StoredUserEntity.find { StoredUsers.telegramId eq user.id }.firstOrNull()

        return existingUser?.id ?: StoredUserEntity.new {
            telegramId = user.id
            isBot = user.is_bot
            firstName = user.first_name
            lastName = user.last_name
            username = user.username
            content = Json.encodeToString(user)
        }.id
    }
}

// Database tables
object StoredMessages : LongIdTable() {
    val updateId = long("update_id")
    val messageId = long("telegram_message_id")
    val chatId = reference("chat_id", StoredChats)
    val userId = reference("user_id", StoredUsers).nullable()
    val date = integer("date")
    val text = text("text").nullable()
    val content = text("content")  // Full message as JSON

    init {
        uniqueIndex(updateId, chatId, messageId)
        index(false, chatId, date)
    }
}

class StoredMessageEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<StoredMessageEntity>(StoredMessages)

    var updateId by StoredMessages.updateId
    var messageId by StoredMessages.messageId
    var chatId by StoredMessages.chatId
    var userId by StoredMessages.userId
    var date by StoredMessages.date
    var text by StoredMessages.text
    var content by StoredMessages.content
}

object StoredUsers : LongIdTable() {
    val telegramId = long("telegram_user_id").uniqueIndex()
    val isBot = bool("is_bot")
    val firstName = varchar("first_name", 255)
    val lastName = varchar("last_name", 255).nullable()
    val username = varchar("username", 255).nullable()
    val content = text("content")  // Full user as JSON
}

class StoredUserEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<StoredUserEntity>(StoredUsers)

    var telegramId by StoredUsers.telegramId
    var isBot by StoredUsers.isBot
    var firstName by StoredUsers.firstName
    var lastName by StoredUsers.lastName
    var username by StoredUsers.username
    var content by StoredUsers.content
}

object StoredChats : LongIdTable() {
    val telegramId = long("telegram_chat_id").uniqueIndex()
    val type = varchar("type", 50)
    val title = varchar("title", 255).nullable()
    val username = varchar("username", 255).nullable()
    val content = text("content")  // Full chat as JSON
}

class StoredChatEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<StoredChatEntity>(StoredChats)

    var telegramId by StoredChats.telegramId
    var type by StoredChats.type
    var title by StoredChats.title
    var username by StoredChats.username
    var content by StoredChats.content
}