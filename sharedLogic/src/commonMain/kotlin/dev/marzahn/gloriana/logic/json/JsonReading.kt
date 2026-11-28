package dev.marzahn.gloriana.logic.json

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

internal val NoFields = JsonObject(emptyMap())

internal fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayAt(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

internal fun JsonObject.objects(key: String): List<JsonObject> = arrayAt(key).filterIsInstance<JsonObject>()

internal fun JsonObject.holds(key: String): Boolean = this[key].let { it != null && it != JsonNull }

internal fun JsonObject?.orNone(): JsonObject = this ?: NoFields

internal sealed interface JsonParse {
  data class Parsed(val element: JsonElement) : JsonParse

  data class Failed(val error: SerializationException) : JsonParse
}

internal fun parseJson(text: String): JsonParse = try {
  JsonParse.Parsed(Json.parseToJsonElement(text))
} catch (error: SerializationException) {
  JsonParse.Failed(error)
}

internal fun parsedElement(text: String): JsonElement? = when (val parse = parseJson(text)) {
  is JsonParse.Parsed -> parse.element
  is JsonParse.Failed -> null
}

internal fun parsedObject(text: String): JsonObject? = parsedElement(text) as? JsonObject

internal fun <T> Json.decodedOrNull(deserializer: DeserializationStrategy<T>, text: String): T? = try {
  decodeFromString(deserializer, text)
} catch (_: SerializationException) {
  null
}

internal inline fun <reified T> Json.decodedOrNull(text: String): T? =
  decodedOrNull(serializersModule.serializer<T>(), text)
