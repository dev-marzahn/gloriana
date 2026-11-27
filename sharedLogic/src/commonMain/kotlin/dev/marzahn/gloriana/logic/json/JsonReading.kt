package dev.marzahn.gloriana.logic.json

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.string(key: String): String? = this[key].asString()

internal fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

internal fun JsonObject.strings(key: String): List<String> =
  (this[key] as? JsonArray).orEmpty().mapNotNull { it.asString() }

internal fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.objects(key: String): List<JsonObject> =
  (this[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

internal fun JsonObject.holds(key: String): Boolean = this[key].let { it != null && it != JsonNull }

internal fun JsonObject?.orNone(): JsonObject = this ?: JsonObject(emptyMap())

internal fun parsedObject(text: String): JsonObject? = try {
  Json.parseToJsonElement(text) as? JsonObject
} catch (_: SerializationException) {
  null
}
