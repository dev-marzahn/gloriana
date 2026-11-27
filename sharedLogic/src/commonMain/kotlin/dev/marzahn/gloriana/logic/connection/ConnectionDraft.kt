package dev.marzahn.gloriana.logic.connection

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class ConnectionDraft(
  val fields: ConnectionFields,
  val key: KeyInput = KeyInput.Kept,
  val headers: List<HeaderDraft> = emptyList(),
)

data class HeaderDraft(val name: String, val value: HeaderValueInput)

sealed interface KeyInput {
  data object Kept : KeyInput

  data object Removed : KeyInput

  class Entered(val value: String) : KeyInput {
    override fun toString() = "Entered(•••)"
  }
}

sealed interface HeaderValueInput {
  data class Kept(val storedName: String) : HeaderValueInput

  class Entered(val value: String) : HeaderValueInput {
    override fun toString() = "Entered(•••)"
  }
}

sealed interface CustomBodyText {
  data class Object(val body: JsonObject) : CustomBodyText

  data class NotJson(val offset: Int?) : CustomBodyText

  data object NotAnObject : CustomBodyText

  companion object {
    fun parse(text: String): CustomBodyText = if (text.isBlank()) {
      Object(JsonObject(emptyMap()))
    } else {
      try {
        Json.parseToJsonElement(text).asBody()
      } catch (error: SerializationException) {
        NotJson(KotlinxJsonErrorOffset.of(error))
      }
    }

    private fun JsonElement.asBody(): CustomBodyText = if (this is JsonObject) Object(this) else NotAnObject
  }
}
