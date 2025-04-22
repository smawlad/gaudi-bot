package com.gaudi.bot.api

import com.gaudi.bot.command.CommandRegistry
import com.gaudi.bot.listener.MessageListener
import com.gaudi.bot.memory.UserMemorySystem
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

fun startMemoryEnabledWebhookServer(client: TelegramClient, port: Int = 8080) {
    val messageProcessor = MemoryEnabledMessageProcessor(client)

    // Start the background message listener for storing messages
    MessageListener.startPeriodicFlushing()

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        routing {
            post("/webhook") {
                try {
                    val update = call.receive<Update>()
                    logger.info { "Received update: $update" }

                    // Store the update in the message storage system for summary feature
                    MessageListener.processUpdate(update)

                    // Process the update for command handling and response
                    messageProcessor.processUpdate(update)

                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    logger.error(e) { "Error processing update" }
                    call.respond(HttpStatusCode.InternalServerError, "Error processing update: ${e.message}")
                }
            }
        }
    }.start(wait = true)
}

/**
 * Enhanced message processor that supports memory and conversation persistence
 */
class MemoryEnabledMessageProcessor(private val client: TelegramClient) {
    var botUsername: String = System.getenv("BOT_USERNAME")
    var botId: Long = System.getenv("BOT_ID").toLong()

    suspend fun processUpdate(update: Update) {
        val message = update.message ?: return
        if (message.text == null) return

        // todo: fix below, hardcode block for now
        //if (System.getenv("BLOCK_LIST").split(" ").contains(message.from?.id.toString())) { return }
        // if (message.from?.id == 741461185L) return


        // First check if this is a direct command
        if (message.isCommand()) {
            processCommand(message)
            return
        }

        // Only continue if the bot is mentioned or replied to
        if (shouldBotRespond(message)) {
            // If this is a reply to the bot, treat it as a continuation of conversation
            if (isReplyToBot(message)) {
                handleConversationContinuation(message)
            } else {
                // Default conversation behavior when bot is mentioned but no command
                handleDefaultMention(message)
            }
        }
    }

    private suspend fun processCommand(message: Message) {
        val commandText = message.getCommand() ?: return
        val handler = CommandRegistry.getHandler(commandText)

        val params = ReplyParameters(message.message_id, message.chat.id)

        if (handler != null) {
            logger.info { "Processing command: $commandText" }
            handler.handle(message, client)
        } else {
            // Unknown command
            client.sendMessage("Sorry, I don't recognize that command. Available commands are: /chat, /summary, /memory, /setkey", params)
        }
    }

    private fun shouldBotRespond(message: Message): Boolean {
        return isReplyToBot(message) || mentionsBot(message)
    }

    private fun isReplyToBot(message: Message): Boolean {
        // Check if message is a reply to this bot
        return message.reply_to_message?.from?.id == botId
    }
    private fun mentionsBot(message: Message): Boolean {
        // Check if the message mentions this bot
        return message.isMentioningUser(botUsername)
    }

    private suspend fun handleConversationContinuation(message: Message) {
        logger.info { "Processing conversation continuation" }
        val userId = message.from?.id ?: return
        val chatId = message.chat.id

        // Get the latest thread for this user in this chat
        val thread = UserMemorySystem.getLatestThread(userId, chatId)

        if (thread == null) {
            // No previous thread found, treat as a new /chat command
            val fakeChatCommand = message.copy(
                text = "/chat ${message.text}"
            )
            CommandRegistry.getHandler("/chat")?.handle(fakeChatCommand, client)
            return
        }

//        // Add the user message to the thread
//        UserMemorySystem.addMemory(
//            threadId = thread.id,
//            role = "user",
//            content = message.text ?: "",
//            metadata = mapOf(
//                "username" to (message.from.username ?: "unknown"),
//                "first_name" to message.from.first_name,
//                "message_id" to message.message_id.toString()
//            )
//        )

        // Create a fakeChatCommand to reuse the existing /chat handler logic
        val fakeChatCommand = message.copy(
            text = "/chat ${message.text}"
        )
        CommandRegistry.getHandler("/chat")?.handle(fakeChatCommand, client)
    }

    private suspend fun handleDefaultMention(message: Message) {
        logger.info { "Bot was mentioned without a command" }
        val params = ReplyParameters(message.message_id, message.chat.id)
        client.sendMessage(
            """
            Hello! I'm Gaudí. You can interact with me using:
            
            • /chat [message] - Start a conversation
            • /summary - Summarize chat content from the last 24 hours
            • /summary hours=12 - Summarize with custom time window
            • /memory - Manage your conversation history
            • /setkey - Set your own API key
            
            You can also reply to my messages to continue our conversation.
            """.trimIndent(), params)
    }
}