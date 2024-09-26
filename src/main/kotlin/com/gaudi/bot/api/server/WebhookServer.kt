package com.gaudi.bot.api.server

import com.gaudi.bot.api.client.TelegramClient
import com.gaudi.bot.api.client.sendMessage
import com.gaudi.bot.api.model.Update
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
                    println(update)
                    handleUpdate(update, client)
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    println(e)
                    call.respond(HttpStatusCode.InternalServerError, "Error processing update: ${e.message}")
                }
            }
        }
    }.start(wait = true)
}

suspend fun handleUpdate(update: Update, client: TelegramClient) {
    if (update.message != null) {
        val message = update.message
        // You can add logic here to respond to the message using client.sendMessage()
        client.sendMessage(message.chat.id, text = "Hi!")
    }
}
