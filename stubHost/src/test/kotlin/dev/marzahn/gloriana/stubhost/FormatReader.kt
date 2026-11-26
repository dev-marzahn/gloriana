package dev.marzahn.gloriana.stubhost

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertEquals

internal enum class PartKind { REASONING, TEXT, REFUSAL }

internal data class ReceivedPart(val kind: PartKind, val text: String)

internal data class ReceivedTokens(val input: Int, val output: Int, val total: Int?, val reasoning: Int?)

internal data class Received(val parts: List<ReceivedPart>, val tokens: ReceivedTokens) {
  companion object {
    fun of(parts: List<ReceivedPart>, tokens: ReceivedTokens) = Received(
      parts.filter { it.text.isNotEmpty() }.fold(emptyList()) { merged, part ->
        val last = merged.lastOrNull()
        if (last?.kind == part.kind) merged.dropLast(1) + last.copy(text = last.text + part.text) else merged + part
      },
      tokens,
    )
  }
}

internal sealed interface FormatReader {
  val format: ApiFormat

  fun keyHeader(key: String): Pair<String, String>

  fun request(streams: Boolean): JsonObject = buildJsonObject {
    put("model", TEST_MODEL)
    put("stream", streams)
  }

  fun readWhole(json: JsonObject): Received

  fun readStream(events: List<SseEvent>): Received

  fun errorMessage(json: JsonObject): String

  fun reported(tokens: ReceivedTokens): ReceivedTokens

  fun asReported(received: Received) = received.copy(tokens = reported(received.tokens))

  companion object {
    const val TEST_MODEL = "test-model"
    val all: List<FormatReader> = listOf(AnthropicReader, ChatCompletionsReader, ResponsesReader)
  }
}

internal fun namedEvents(events: List<SseEvent>): List<JsonObject> = events.map { event ->
  parseObject(event.data).also { assertEquals(event.name, it.text("type"), "an event's name is its data's type") }
}
