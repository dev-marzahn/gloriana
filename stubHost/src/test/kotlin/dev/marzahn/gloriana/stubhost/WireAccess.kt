package dev.marzahn.gloriana.stubhost

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal fun parseSse(text: String): List<SseEvent> = text.split("\n\n").filter(String::isNotBlank).map { block ->
  val lines = block.lines()
  SseEvent(
    name = lines.firstOrNull { it.startsWith("event: ") }?.removePrefix("event: "),
    data = lines.single { it.startsWith("data: ") }.removePrefix("data: "),
  )
}

internal fun parseObject(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

internal fun JsonObject.obj(name: String): JsonObject = getValue(name).jsonObject

internal fun JsonObject.objects(name: String): List<JsonObject> = getValue(name).jsonArray.map { it.jsonObject }

internal fun JsonObject.text(name: String): String = assertNotNull(textOrNull(name), "$name in $this")

internal fun JsonObject.textOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.content.toInt()

internal class EventCursor(private val events: List<JsonObject>) {
  private var at = 0

  val next: JsonObject? get() = events.getOrNull(at)
  val nextType: String? get() = next?.text("type")

  fun take(type: String): JsonObject {
    val event = assertNotNull(events.getOrNull(at), "the stream ended where $type was due")
    assertEquals(type, event.text("type"), "event $at")
    at++
    return event
  }

  fun takeWhile(type: String): List<JsonObject> = buildList { while (nextType == type) add(take(type)) }

  fun end() = assertEquals(events.size, at, "events after the stream's end")
}
