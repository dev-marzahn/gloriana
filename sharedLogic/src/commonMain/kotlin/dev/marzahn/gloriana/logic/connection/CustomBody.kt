package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.json.asBoolean
import dev.marzahn.gloriana.logic.json.orNone
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val STREAM = "stream"

internal fun JsonObject.mergedWith(custom: JsonObject): JsonObject {
  val merged = LinkedHashMap<String, JsonElement>(this)
  for ((name, value) in custom) {
    when (value) {
      JsonNull -> merged.remove(name)
      is JsonObject -> merged[name] = (merged[name] as? JsonObject).orNone().mergedWith(value)
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

internal val ConnectionFields.streamValue: StreamValue
  get() = JsonObject(mapOf(STREAM to JsonPrimitive(streaming))).mergedWith(customBody).streamValue()

internal fun JsonObject.streamValue(): StreamValue {
  val stream = this[STREAM] ?: return StreamValue.Off
  return when (stream.asBoolean()) {
    true -> StreamValue.On
    false -> StreamValue.Off
    null -> StreamValue.NotBoolean
  }
}
