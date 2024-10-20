package com.gaudi.bot.api.server

import com.gaudi.bot.ai.LMWrapper
import com.gaudi.bot.ai.ModelType
import com.gaudi.bot.api.client.TelegramClient
import com.gaudi.bot.api.client.sendMessage
import com.gaudi.bot.api.model.Update
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

fun startWebhookServer(client: TelegramClient, port: Int = 8080) {
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
                    handleUpdate(update, client)
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    logger.error(e) { "Error processing update" }
                    call.respond(HttpStatusCode.InternalServerError, "Error processing update: ${e.message}")
                }
            }
        }
    }.start(wait = true)
}

suspend fun handleUpdate(update: Update, client: TelegramClient) {
    if (update.message != null) {
        val message = update.message
        val claudeWrapper = LMWrapper(ModelType.CLAUDE)
        val modelResponse = claudeWrapper.generateResponse(message.text.toString())
        client.sendMessage(message.chat.id, modelResponse)
        logger.info { "Sent response to chat ${message.chat.id}" }
    }
}
