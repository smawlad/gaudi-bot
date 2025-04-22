package com.gaudi.bot.listener

import com.gaudi.bot.api.Update
import com.gaudi.bot.storage.MessageStorage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = KotlinLogging.logger {}

/**
 * Listens for incoming messages and stores them in the database
 * This enables features like chat history summarys
 */
object MessageListener {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val messageBuffer = mutableListOf<Update>()
    private val bufferMutex = Mutex()
    private const val BUFFER_SIZE = 100

    /**
     * Process an update from Telegram by extracting and storing any messages
     */
    fun processUpdate(update: Update) {
        scope.launch {
            try {
                bufferMessages(update)
            } catch (e: Exception) {
                logger.error(e) { "Error processing update for message storage" }
            }
        }
    }

    /**
     * Add a message to the buffer and flush if needed
     */
    private suspend fun bufferMessages(update: Update) {
        bufferMutex.withLock {
            messageBuffer.add(update)

            // If buffer reaches threshold, flush to database
            if (messageBuffer.size >= BUFFER_SIZE) {
                flushBuffer()
            }
        }
    }

    /**
     * Force an immediate flush of the buffer
     */
    suspend fun flushBuffer() {
        bufferMutex.withLock {
            if (messageBuffer.isNotEmpty()) {
                try {
                    val messagesCount = MessageStorage.storeMessages(messageBuffer)
                    logger.info { "Stored $messagesCount messages in the database" }
                    messageBuffer.clear()
                } catch (e: Exception) {
                    logger.error(e) { "Error storing messages in batch" }
                }
            }
        }
    }

    /**
     * Start periodic flushing task to ensure messages are stored even if
     * buffer never reaches threshold
     */
    fun startPeriodicFlushing(intervalMs: Long = 5000) {
        scope.launch {
            while (true) {
                try {
                    kotlinx.coroutines.delay(intervalMs)
                    flushBuffer()
                } catch (e: Exception) {
                    logger.error(e) { "Error in periodic buffer flushing" }
                }
            }
        }
    }
}