package dev.marzahn.gloriana.logic.connection

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class JsonFault {
  CommaMissing,
  BraceMissing,
  BracketMissing,
  QuoteMissing,
  ValueMissing,
  CharacterNotAllowed,
  TextAfterTheEnd,
  Other,
}

internal object KotlinxJsonFailure {
  private val offsetInMessage = Regex("at offset (\\d+)")
  private val jsonLiterals = setOf("true", "false")
  private val jsonNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

  fun offset(error: SerializationException): Int? =
    error.message?.let(offsetInMessage::find)?.groupValues?.get(1)?.toIntOrNull()

  private class Rule(val marker: String, val fault: (atEnd: Boolean, message: String) -> JsonFault)

  private val rules = listOf(
    Rule("Expected end of the object or comma") { atEnd, _ ->
      if (atEnd) JsonFault.BraceMissing else JsonFault.CommaMissing
    },
    Rule("Expected end of the array or comma") { atEnd, _ ->
      if (atEnd) JsonFault.BracketMissing else JsonFault.CommaMissing
    },
    Rule("Expected quotation mark") { _, message ->
      if ("'EOF'" in message) JsonFault.QuoteMissing else JsonFault.CharacterNotAllowed
    },
    Rule("Cannot read Json element") { _, _ -> JsonFault.ValueMissing },
    Rule("Expected EOF after parsing") { _, _ -> JsonFault.TextAfterTheEnd },
    Rule("Unexpected leading comma") { _, _ -> JsonFault.CharacterNotAllowed },
    Rule("Trailing comma") { _, _ -> JsonFault.CharacterNotAllowed },
  )

  fun fault(error: SerializationException, text: String): JsonFault {
    val message = error.message.orEmpty()
    val atEnd = offset(error)?.let { it >= text.trimEnd().length } ?: false
    return rules.firstOrNull { it.marker in message }?.fault?.invoke(atEnd, message) ?: JsonFault.Other
  }

  fun holdsBareWord(element: JsonElement): Boolean = when (element) {
    is JsonObject -> element.values.any(::holdsBareWord)
    is JsonArray -> element.any(::holdsBareWord)
    JsonNull -> false
    is JsonPrimitive -> !element.isString && element.content !in jsonLiterals && !jsonNumber.matches(element.content)
  }
}
