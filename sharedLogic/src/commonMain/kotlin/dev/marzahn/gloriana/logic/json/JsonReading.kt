package dev.marzahn.gloriana.logic.json

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

internal fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayAt(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

internal fun JsonObject.objects(key: String): List<JsonObject> = arrayAt(key).filterIsInstance<JsonObject>()

internal fun JsonObject.holds(key: String): Boolean = this[key].let { it != null && it != JsonNull }

internal fun JsonObject?.orNone(): JsonObject = this ?: JsonObject(emptyMap())

internal fun parsedObject(text: String): JsonObject? = try {
  Json.parseToJsonElement(text) as? JsonObject
} catch (_: SerializationException) {
  null
}
