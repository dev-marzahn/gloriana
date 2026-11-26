package dev.marzahn.gloriana.stubhost

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal data object ChatCompletionsReader : FormatReader {
  private val partFields = mapOf(
    "reasoning_content" to PartKind.REASONING,
    "content" to PartKind.TEXT,
    "refusal" to PartKind.REFUSAL,
  )

  override val format = ChatCompletions

  override fun keyHeader(key: String) = "Authorization" to "Bearer $key"

  override fun reported(tokens: ReceivedTokens) = tokens.copy(reasoning = null)

  override fun request(streams: Boolean): JsonObject = buildJsonObject {
    put("model", FormatReader.TEST_MODEL)
    put("stream", streams)
    if (streams) putJsonObject("stream_options") { put("include_usage", true) }
  }

  override fun readWhole(json: JsonObject): Received {
    assertEquals("chat.completion", json.text("object"))
    assertEquals(FormatReader.TEST_MODEL, json.text("model"))
    val choice = json.objects("choices").single()
    assertEquals(0, choice.int("index"))
    assertEquals("stop", choice.text("finish_reason"))
    val message = choice.obj("message")
    assertEquals("assistant", message.text("role"))
    assertEquals(setOf("role", "content", "refusal"), message.keys - "reasoning_content")
    val parts = partFields.mapNotNull { (field, kind) -> message.textOrNull(field)?.let { ReceivedPart(kind, it) } }
    return Received.of(parts, tokens(json.obj("usage")))
  }

  override fun readStream(events: List<SseEvent>): Received {
    events.forEach { assertNull(it.name, "Chat Completions events are unnamed") }
    assertEquals("[DONE]", events.last().data)
    val chunks = events.dropLast(1).map { parseObject(it.data) }
    chunks.forEach {
      assertEquals("chat.completion.chunk", it.text("object"))
      assertEquals(FormatReader.TEST_MODEL, it.text("model"))
    }
    val usageChunk = chunks.lastOrNull()?.takeIf { it.objects("choices").isEmpty() }
    val replyChunks = if (usageChunk == null) chunks else chunks.dropLast(1)
    replyChunks.forEach { assertEquals(JsonNull, it["usage"], "only the last chunk carries usage") }
    val choices = replyChunks.map { it.objects("choices").single() }
    choices.forEach { assertEquals(0, it.int("index")) }

    val opening = choices.first()
    assertEquals(parseObject("""{"role":"assistant","content":""}"""), opening.obj("delta"))
    assertEquals(JsonNull, opening["finish_reason"])
    val closing = choices.last()
    assertEquals("stop", closing.text("finish_reason"))
    assertEquals(JsonObject(emptyMap()), closing.obj("delta"))

    val parts = choices.drop(1).dropLast(1).map { choice ->
      assertEquals(JsonNull, choice["finish_reason"])
      val delta = choice.obj("delta")
      val field = delta.keys.single()
      ReceivedPart(assertNotNull(partFields[field], "delta field $field"), delta.text(field))
    }
    return Received.of(parts, tokens(assertNotNull(usageChunk, "the usage chunk").obj("usage")))
  }

  override fun errorMessage(json: JsonObject): String = json.obj("error").text("message")

  fun usageChunkCount(events: List<SseEvent>): Int =
    events.dropLast(1).map { parseObject(it.data) }.count { it.objects("choices").isEmpty() }

  private fun tokens(usage: JsonObject) = ReceivedTokens(
    usage.int("prompt_tokens"),
    usage.int("completion_tokens"),
    usage.int("total_tokens"),
    reasoning = null,
  )
}
