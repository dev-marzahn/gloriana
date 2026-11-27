package dev.marzahn.gloriana.logic.connection.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal const val STREAM = "stream"

private val noFields = JsonObject(emptyMap())

internal fun JsonObject.mergedWith(custom: JsonObject): JsonObject {
  val merged = LinkedHashMap<String, JsonElement>(this)
  for ((name, value) in custom) {
    when (value) {
      JsonNull -> merged.remove(name)
      is JsonObject -> merged[name] = ((merged[name] as? JsonObject) ?: noFields).mergedWith(value)
      else -> merged[name] = value
    }
  }
  return JsonObject(merged)
}

internal enum class StreamValue {
  On,
  Off,
  NotBoolean,
}

internal fun JsonObject.streamsOr(generated: Boolean): Boolean =
  if (STREAM in this) streamValue() == StreamValue.On else generated

internal fun JsonObject.streamValue(): StreamValue {
  val stream = this[STREAM] ?: return StreamValue.Off
  val flag = (stream as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
  return when (flag) {
    true -> StreamValue.On
    false -> StreamValue.Off
    null -> StreamValue.NotBoolean
  }
}
