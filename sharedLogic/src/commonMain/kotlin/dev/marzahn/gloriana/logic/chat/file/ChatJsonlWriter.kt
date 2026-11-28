package dev.marzahn.gloriana.logic.chat.file

import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.files.ByteSink
import dev.marzahn.gloriana.logic.files.writeAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val LINE_BREAK = "\n".encodeToByteArray()

internal object ChatJsonlWriter {
  fun write(chat: ChatFile, sink: ByteSink) {
    sink.writeAll(bytesOf(header(chat)))
    chat.messages.forEach { message ->
      sink.writeAll(LINE_BREAK)
      sink.writeAll(bytesOf(line(message)))
    }
  }

  private fun bytesOf(line: JsonObject) = Json.encodeToString(JsonElement.serializer(), line).encodeToByteArray()

  private fun header(chat: ChatFile) = buildJsonObject {
    put("chat_metadata", JsonObject(emptyMap()))
    put("user_name", chat.userName)
    put("character_name", chat.characterName)
  }

  private fun line(message: ChatFileMessage) = buildJsonObject {
    put("name", message.speaker)
    put("is_user", message.role == MessageRole.User)
    put("is_system", message.hidden)
    put("mes", message.shown.text)
    put("extra", extra(message.shown.reasoning))
    if (message.role == MessageRole.Character) {
      put("swipe_id", message.shownAt)
      put("swipes", buildJsonArray { message.swipes.forEach { add(JsonPrimitive(it.text)) } })
      put("swipe_info", buildJsonArray { message.swipes.forEach { add(swipeInfo(it.reasoning)) } })
    }
  }

  private fun swipeInfo(reasoning: String) = buildJsonObject { put("extra", extra(reasoning)) }

  private fun extra(reasoning: String) = buildJsonObject { if (reasoning.isNotEmpty()) put("reasoning", reasoning) }
}
