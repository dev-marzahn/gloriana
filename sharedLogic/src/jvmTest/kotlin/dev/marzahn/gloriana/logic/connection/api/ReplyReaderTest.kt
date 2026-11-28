package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.MASK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplyReaderTest {
  private val noSecrets = Scrubber(emptySet())

  private fun StreamReader.readAll(vararg data: String): List<ModelEvent> = data.flatMap(::read) + ended()

  private fun hostError(body: String) = ModelEvent.Failed(ModelApiError.HostError(status = null, body = body))

  @Test
  fun anthropicPromptTokensAddCacheCreationAndCacheReads() {
    val events = AnthropicMessagesAdapter.streamReader(noSecrets).readAll(
      """{"type":"message_start","message":{"usage":
        {"input_tokens":5,"cache_creation_input_tokens":7,"cache_read_input_tokens":11}}}""",
      """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":3}}""",
      """{"type":"message_stop"}""",
    )

    assertEquals(listOf(ModelEvent.Usage(23, 3), ModelEvent.Finish(FinishReason.Completed)), events)
  }

  @Test
  fun aStreamThatEndsWithoutItsLastEventKeepsTheReasonAndUsageItSaw() {
    val chat = ChatCompletionsAdapter.streamReader(noSecrets).readAll(
      """{"choices":[{"delta":{"content":"Hi"},"finish_reason":"length"}]}""",
      """{"choices":[],"usage":{"prompt_tokens":4,"completion_tokens":1}}""",
    )
    val bare = ResponsesAdapter.streamReader(noSecrets)
      .readAll("""{"type":"response.output_text.delta","delta":"Hi"}""")

    assertEquals(
      listOf(ModelEvent.TextDelta("Hi"), ModelEvent.Usage(4, 1), ModelEvent.Finish(FinishReason.MaxTokens)),
      chat,
    )
    assertEquals(listOf(ModelEvent.TextDelta("Hi"), ModelEvent.Finish(FinishReason.Unfinished)), bare)
  }

  @Test
  fun chatReasoningArrivesAsReasoningContentOrReasoning() {
    val events = ChatCompletionsAdapter.streamReader(noSecrets).readAll(
      """{"choices":[{"delta":{"reasoning":"Thinking."}}]}""",
      """{"choices":[{"delta":{"reasoning_content":" More."}}]}""",
      "[DONE]",
    )

    assertEquals("Thinking. More.", events.reasoning())
  }

  @Test
  fun anErrorInAChatStreamFailsTheReplyWhateverItsShape() {
    listOf("""{"error":"overloaded"}""", """{"error":{"message":"overloaded"}}""").forEach { data ->
      val events = ChatCompletionsAdapter.streamReader(noSecrets)
        .readAll("""{"choices":[{"delta":{"content":"Hi"}}]}""", data)

      assertEquals(listOf(ModelEvent.TextDelta("Hi"), hostError(data)), events, data)
    }
  }

  @Test
  fun responsesErrorEventsAndIncompleteRepliesEndTheReply() {
    val error = """{"type":"error","code":"server_error","message":"broke"}"""
    val limited = """{"type":"response.incomplete","response":{"status":"incomplete",
      "incomplete_details":{"reason":"max_output_tokens"},"usage":{"input_tokens":2,"output_tokens":9}}}"""
    val filtered = """{"type":"response.incomplete","response":{"status":"incomplete",
      "incomplete_details":{"reason":"content_filter"}}}"""

    assertEquals(listOf(hostError(error)), ResponsesAdapter.streamReader(noSecrets).readAll(error))
    assertEquals(
      listOf(ModelEvent.Usage(2, 9), ModelEvent.Finish(FinishReason.MaxTokens)),
      ResponsesAdapter.streamReader(noSecrets).readAll(limited),
    )
    val filteredEvents = ResponsesAdapter.streamReader(noSecrets).readAll(filtered)
    assertEquals(listOf(ModelEvent.Finish(FinishReason.Unfinished)), filteredEvents)
  }

  @Test
  fun responsesReasoningTextIsReasoning() {
    val events = ResponsesAdapter.streamReader(noSecrets).readAll(
      """{"type":"response.reasoning_text.delta","delta":"Raw thought."}""",
      """{"type":"response.completed","response":{"status":"completed"}}""",
    )

    assertEquals(listOf(ModelEvent.ReasoningDelta("Raw thought."), ModelEvent.Finish(FinishReason.Completed)), events)
  }

  @Test
  fun aResponsesEndWithoutItsResponseFailsAndOneWithoutAStatusTakesTheEventsOwn() {
    val bare = """{"type":"response.completed"}"""
    val noStatus = """{"type":"response.completed","response":{"usage":{"input_tokens":1,"output_tokens":2}}}"""

    assertEquals(listOf(hostError(bare)), ResponsesAdapter.streamReader(noSecrets).readAll(bare))
    assertEquals(
      listOf(ModelEvent.Usage(1, 2), ModelEvent.Finish(FinishReason.Completed)),
      ResponsesAdapter.streamReader(noSecrets).readAll(noStatus),
    )
  }

  @Test
  fun anErrorInTheStreamIsScrubbed() {
    val keyed = Scrubber(setOf("sk-9"))
    val data = """{"type":"error","error":{"message":"bad key sk-9"}}"""

    val events = AnthropicMessagesAdapter.streamReader(keyed).readAll(data)

    assertEquals(listOf(hostError("""{"type":"error","error":{"message":"bad key $MASK"}}""")), events)
  }

  @Test
  fun anErrorInAWholeReplyIsScrubbedAndAFailedResponsesReplyIsOne() {
    val keyed = Scrubber(setOf("sk-9"))
    val chat = """{"error":{"message":"no credits for sk-9"}}"""
    val responses = """{"id":"r","status":"failed","error":null,"output":[],"note":"sk-9"}"""

    assertEquals(
      listOf(hostError("""{"error":{"message":"no credits for $MASK"}}""")),
      ChatCompletionsAdapter.wholeReply(chat, keyed),
    )
    assertEquals(
      listOf(hostError("""{"id":"r","status":"failed","error":null,"output":[],"note":"$MASK"}""")),
      ResponsesAdapter.wholeReply(responses, keyed),
    )
  }

  @Test
  fun nothingIsReadAfterTheLastEvent() {
    val events = AnthropicMessagesAdapter.streamReader(noSecrets).readAll(
      """{"type":"message_stop"}""",
      """{"type":"content_block_delta","delta":{"type":"text_delta","text":"late"}}""",
    )

    assertEquals(listOf(ModelEvent.Finish(FinishReason.Completed)), events)
  }

  @Test
  fun aWholeReplyHoldingAnErrorIsAnErrorReply() {
    assertTrue(ChatCompletionsAdapter.isErrorReply(json("""{"error":{"message":"no credits"}}""")))
    assertTrue(AnthropicMessagesAdapter.isErrorReply(json("""{"type":"error","error":{"type":"overloaded_error"}}""")))
    assertFalse(ResponsesAdapter.isErrorReply(json("""{"id":"resp_1","status":"completed","error":null}""")))
    assertTrue(ResponsesAdapter.isErrorReply(json("""{"id":"resp_1","status":"failed","error":null}""")))
    assertFalse(ChatCompletionsAdapter.isErrorReply(json("""{"status":"failed","choices":[]}""")))
  }

  @Test
  fun aWholeChatReplyCutAtTheLimitFinishesAtMaxTokens() {
    val reply = json("""{"choices":[{"message":{"content":"Hi"},"finish_reason":"length"}]}""")

    val events = ChatCompletionsAdapter.whole(reply)

    assertEquals(listOf(ModelEvent.TextDelta("Hi"), ModelEvent.Finish(FinishReason.MaxTokens)), events)
  }
}
