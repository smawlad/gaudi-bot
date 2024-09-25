package com.gaudi.bot.api

import kotlinx.serialization.Serializable

@Serializable
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null,
    val error_code: Int? = null
)

@Serializable
data class Message(
    val message_id: Long,
    val chat: Chat,
    val date: Int,
    val text: String? = null,
    val from: User? = null
)

@Serializable
data class Chat(
    val id: Long,
    val type: String,
    val title: String? = null,
    val username: String? = null,
    val first_name: String? = null,
    val last_name: String? = null
)

@Serializable
data class User(
    val id: Long,
    val is_bot: Boolean,
    val first_name: String,
    val last_name: String? = null,
    val username: String? = null,
    val language_code: String? = null
)

@Serializable
data class Update(
    val update_id: Long,
    val message: Message? = null,
    val edited_message: Message? = null,
    val callback_query: CallbackQuery? = null
)

@Serializable
data class CallbackQuery(
    val id: String,
    val from: User,
    val message: Message? = null,
    val data: String? = null
)

@Serializable
data class PhotoSize(
    val file_id: String,
    val width: Int,
    val height: Int,
    val file_size: Int? = null
)

@Serializable
data class Document(
    val file_id: String,
    val thumb: PhotoSize? = null,
    val file_name: String? = null,
    val mime_type: String? = null,
    val file_size: Int? = null
)

@Serializable
data class Audio(
    val file_id: String,
    val duration: Int,
    val performer: String? = null,
    val title: String? = null,
    val mime_type: String? = null,
    val file_size: Int? = null
)

@Serializable
data class Video(
    val file_id: String,
    val width: Int,
    val height: Int,
    val duration: Int,
    val thumb: PhotoSize? = null,
    val mime_type: String? = null,
    val file_size: Int? = null
)

@Serializable
data class Sticker(
    val file_id: String,
    val width: Int,
    val height: Int,
    val is_animated: Boolean,
    val thumb: PhotoSize? = null,
    val emoji: String? = null,
    val set_name: String? = null
)

@Serializable
data class Location(
    val longitude: Float,
    val latitude: Float
)

@Serializable
data class Contact(
    val phone_number: String,
    val first_name: String,
    val last_name: String? = null,
    val user_id: Long? = null
)

@Serializable
data class ChatMember(
    val user: User,
    val status: String,
    val until_date: Int? = null,
    val can_be_edited: Boolean? = null,
    val can_change_info: Boolean? = null,
    val can_post_messages: Boolean? = null,
    val can_edit_messages: Boolean? = null,
    val can_delete_messages: Boolean? = null,
    val can_restrict_members: Boolean? = null,
    val can_promote_members: Boolean? = null
)