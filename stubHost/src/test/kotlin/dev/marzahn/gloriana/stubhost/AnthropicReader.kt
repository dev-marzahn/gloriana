package dev.marzahn.gloriana.stubhost

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

internal data object AnthropicReader : FormatReader {
  private const val THINKING = "thinking"
  private const val TEXT = "text"

  override val format = AnthropicMessages

  override fun keyHeader(key: String) = "x-api-key" to key

  override fun reported(tokens: ReceivedTokens) = tokens.copy(total = null, reasoning = null)

  override fun readWhole(json: JsonObject): Received {
    checkMessage(json)
    val blocks = json.objects("content").map { block ->
      val type = block.text("type")
      if (type == THINKING) assertTrue(block.text("signature").isNotEmpty(), "a thinking block is signed")
      type to block.text(if (type == THINKING) THINKING else TEXT)
    }
    val usage = json.obj("usage")
    return received(blocks, json.text("stop_reason"), usage.int("input_tokens"), usage.int("output_tokens"))
  }

  override fun readStream(events: List<SseEvent>): Received {
    val cursor = EventCursor(namedEvents(events))
    val opening = cursor.take("message_start").obj("message")
    checkMessage(opening)
    assertEquals(emptyList(), opening.objects("content"))
    assertEquals(JsonNull, opening["stop_reason"])
    assertEquals(0, opening.obj("usage").int("output_tokens"))
    cursor.take("ping")
    val blocks = generateSequence(0) { it + 1 }
      .takeWhile { cursor.nextType == "content_block_start" }
      .map { index -> readBlock(cursor, index) }
      .toList()
    val ending = cursor.take("message_delta")
    cursor.take("message_stop")
    cursor.end()
    return received(
      blocks,
      ending.obj("delta").text("stop_reason"),
      opening.obj("usage").int("input_tokens"),
      ending.obj("usage").int("output_tokens"),
    )
  }

  override fun errorMessage(json: JsonObject): String {
    assertEquals("error", json.text("type"))
    return json.obj("error").text("message")
  }

  private fun readBlock(cursor: EventCursor, index: Int): Pair<String, String> {
    val start = cursor.take("content_block_start")
    assertEquals(index, start.int("index"))
    val type = start.obj("content_block").text("type")
    val deltas = cursor.takeWhile("content_block_delta").map {
      assertEquals(index, it.int("index"))
      it.obj("delta")
    }
    val text = when (type) {
      THINKING -> {
        assertEquals("signature_delta", deltas.last().text("type"), "thinking ends with its signature")
        deltas.dropLast(1).joinToString("") { delta(it, "thinking_delta", THINKING) }
      }
      TEXT -> deltas.joinToString("") { delta(it, "text_delta", TEXT) }
      else -> fail("block type $type")
    }
    assertEquals(index, cursor.take("content_block_stop").int("index"))
    return type to text
  }

  private fun delta(delta: JsonObject, type: String, field: String): String {
    assertEquals(type, delta.text("type"))
    return delta.text(field)
  }

  private fun checkMessage(json: JsonObject) {
    assertEquals("message", json.text("type"))
    assertEquals("assistant", json.text("role"))
    assertEquals(FormatReader.TEST_MODEL, json.text("model"))
  }

  private fun received(blocks: List<Pair<String, String>>, stopReason: String, input: Int, output: Int): Received {
    assertTrue(stopReason in setOf("end_turn", "refusal"), "stop_reason $stopReason")
    val textKind = if (stopReason == "refusal") PartKind.REFUSAL else PartKind.TEXT
    val parts = blocks.map { (type, text) ->
      ReceivedPart(if (type == THINKING) PartKind.REASONING else textKind, text)
    }
    return Received.of(parts, ReceivedTokens(input, output, total = null, reasoning = null))
  }
}
