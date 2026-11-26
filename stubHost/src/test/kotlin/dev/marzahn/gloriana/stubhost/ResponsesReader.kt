package dev.marzahn.gloriana.stubhost

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

internal data object ResponsesReader : FormatReader {
  private val summaryText = PartGrammar(
    partEvents = "response.reasoning_summary_part",
    indexField = "summary_index",
    textEvents = "response.reasoning_summary_text",
    textField = "text",
    partType = "summary_text",
  )
  private val outputText = PartGrammar(
    partEvents = "response.content_part",
    indexField = "content_index",
    textEvents = "response.output_text",
    textField = "text",
    partType = "output_text",
  )
  private val refusal = PartGrammar(
    partEvents = "response.content_part",
    indexField = "content_index",
    textEvents = "response.refusal",
    textField = "refusal",
    partType = "refusal",
  )

  override val format = OpenAiResponses
  override fun keyHeader(key: String) = "Authorization" to "Bearer $key"

  override fun reported(tokens: ReceivedTokens) = tokens

  override fun readWhole(json: JsonObject): Received {
    checkResponse(json, "completed")
    val parts = json.objects("output").flatMap { item ->
      assertEquals("completed", item.text("status"))
      when (item.text("type")) {
        "reasoning" -> item.objects("summary").map { part(it, summaryText, PartKind.REASONING) }
        "message" -> {
          assertEquals("assistant", item.text("role"))
          item.objects("content").map { content ->
            if (content.text("type") == refusal.partType) {
              part(content, refusal, PartKind.REFUSAL)
            } else {
              assertEquals(JsonArray(emptyList()), content["annotations"])
              assertEquals(JsonArray(emptyList()), content["logprobs"])
              part(content, outputText, PartKind.TEXT)
            }
          }
        }
        else -> fail("item type ${item.text("type")}")
      }
    }
    val usage = json.obj("usage")
    assertEquals(0, usage.obj("input_tokens_details").int("cached_tokens"))
    val tokens = ReceivedTokens(
      input = usage.int("input_tokens"),
      output = usage.int("output_tokens"),
      total = usage.int("total_tokens"),
      reasoning = usage.obj("output_tokens_details").int("reasoning_tokens"),
    )
    return Received.of(parts, tokens)
  }

  private fun part(json: JsonObject, grammar: PartGrammar, kind: PartKind): ReceivedPart {
    assertEquals(grammar.partType, json.text("type"))
    return ReceivedPart(kind, json.text(grammar.textField))
  }

  override fun readStream(events: List<SseEvent>): Received {
    val data = namedEvents(events)
    assertEquals(data.indices.toList(), data.map { it.int("sequence_number") })
    val cursor = EventCursor(data)
    checkResponse(cursor.take("response.created").obj("response"), "in_progress")
    checkResponse(cursor.take("response.in_progress").obj("response"), "in_progress")
    val items = generateSequence(0) { it + 1 }
      .takeWhile { cursor.nextType == "response.output_item.added" }
      .map { readItem(cursor, it) }
      .toList()
    val completed = cursor.take("response.completed").obj("response")
    cursor.end()
    assertEquals(items, completed.objects("output"), "the completed response holds every streamed item")
    return readWhole(completed)
  }

  override fun errorMessage(json: JsonObject): String = json.obj("error").text("message")

  private fun checkResponse(response: JsonObject, status: String) {
    assertEquals("response", response.text("object"))
    assertEquals(status, response.text("status"))
    assertEquals(FormatReader.TEST_MODEL, response.text("model"))
    if (status == "in_progress") {
      assertEquals(emptyList(), response.objects("output"))
      assertEquals(JsonNull, response["usage"])
    }
  }

  private fun readItem(cursor: EventCursor, outputIndex: Int): JsonObject {
    val added = cursor.take("response.output_item.added")
    assertEquals(outputIndex, added.int("output_index"))
    val item = added.obj("item")
    assertEquals("in_progress", item.text("status"))
    val grammar = when (item.text("type")) {
      "reasoning" -> summaryText.also { assertEquals(emptyList(), item.objects("summary")) }
      "message" -> {
        assertEquals(emptyList(), item.objects("content"))
        val partType = assertNotNull(cursor.next, "a message's first part").obj("part").text("type")
        if (partType == refusal.partType) refusal else outputText
      }
      else -> fail("item type ${item.text("type")}")
    }
    grammar.read(cursor, item.text("id"), outputIndex)
    val done = cursor.take("response.output_item.done")
    assertEquals(outputIndex, done.int("output_index"))
    assertEquals(item.text("id"), done.obj("item").text("id"))
    return done.obj("item")
  }

  private data class PartGrammar(
    val partEvents: String,
    val indexField: String,
    val textEvents: String,
    val textField: String,
    val partType: String,
  ) {
    fun read(cursor: EventCursor, itemId: String, outputIndex: Int) {
      fun located(event: JsonObject) = event.also {
        assertEquals(itemId, it.text("item_id"))
        assertEquals(outputIndex, it.int("output_index"))
        assertEquals(0, it.int(indexField))
      }
      val opening = located(cursor.take("$partEvents.added")).obj("part")
      assertEquals(partType, opening.text("type"))
      assertEquals("", opening.text(textField))
      val text = cursor.takeWhile("$textEvents.delta").map(::located).joinToString("") { it.text("delta") }
      assertEquals(text, located(cursor.take("$textEvents.done")).text(textField))
      val closing = located(cursor.take("$partEvents.done")).obj("part")
      assertEquals(partType, closing.text("type"))
      assertEquals(text, closing.text(textField))
    }
  }
}
