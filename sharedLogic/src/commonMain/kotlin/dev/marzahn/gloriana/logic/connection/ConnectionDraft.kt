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

fun Connection.asDraft(): ConnectionDraft =
  ConnectionDraft(fields, KeyInput.Kept, headers.map { HeaderDraft(it.name, HeaderValueInput.Kept(it.name)) })

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

  data class NotJson(val position: TextPosition?, val fault: JsonFault) : CustomBodyText

  data object NotAnObject : CustomBodyText

  companion object {
    val Empty = Object(JsonObject(emptyMap()))

    fun parse(text: String): CustomBodyText = if (text.isBlank()) {
      Empty
    } else {
      try {
        Json.parseToJsonElement(text).asBody()
      } catch (error: SerializationException) {
        NotJson(
          KotlinxJsonFailure.offset(error)?.let { TextPosition.of(text, it) },
          KotlinxJsonFailure.fault(error, text),
        )
      }
    }

    private fun JsonElement.asBody(): CustomBodyText = when {
      KotlinxJsonFailure.holdsBareWord(this) -> NotJson(position = null, JsonFault.CharacterNotAllowed)
      this is JsonObject -> Object(this)
      else -> NotAnObject
    }
  }
}

private val PrettyJson = Json { prettyPrint = true }

val ConnectionFields.customBodyText: String
  get() = if (customBody.isEmpty()) "" else PrettyJson.encodeToString(JsonObject.serializer(), customBody)

fun ConnectionFields.withCustomBody(body: CustomBodyText.Object): ConnectionFields = copy(customBody = body.body)

data class TextPosition(val line: Int, val column: Int) {
  companion object {
    fun of(text: String, offset: Int): TextPosition {
      val before = text.take(offset)
      return TextPosition(line = before.count { it == '\n' } + 1, column = offset - (before.lastIndexOf('\n') + 1) + 1)
    }
  }
}
