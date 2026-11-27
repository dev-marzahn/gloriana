package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.json.holds
import dev.marzahn.gloriana.logic.json.long
import dev.marzahn.gloriana.logic.json.objectAt
import dev.marzahn.gloriana.logic.json.objects
import dev.marzahn.gloriana.logic.json.parsedObject
import dev.marzahn.gloriana.logic.json.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val DONE = "[DONE]"
private const val STREAM_OPTIONS = "stream_options"

internal object ChatCompletionsAdapter : FormatAdapter {
  override val path = "chat/completions"
  override val modelListTarget = "models"
  override val fixedHeaders = emptyList<HostHeader>()

  override val keyHeaderName = AuthorizationHeader

  override fun keyValue(key: String) = bearer(key)

  override fun body(sections: List<PromptSection>, fields: ConnectionFields, samplers: SentSamplers): JsonObject {
    val streams = fields.customBody.streamsOr(fields.streaming)
    return buildJsonObject {
      put("model", fields.model)
      put("messages", roleMessages(sections))
      put("max_tokens", fields.maxReplyTokens)
      put(STREAM, fields.streaming)
      if (streams && fields.includeUsage) putJsonObject(STREAM_OPTIONS) { put("include_usage", true) }
      samplers.putInto(this)
    }.mergedWith(fields.customBody)
  }

  override fun streamReader(scrubber: Scrubber): StreamReader = ChatCompletionsStream(scrubber)

  override fun whole(reply: JsonObject): List<ModelEvent> {
    val choice = reply.objects("choices").firstOrNull()
    val message = choice?.objectAt("message")
    val state = ReplyState(
      promptTokens = reply.objectAt("usage")?.long("prompt_tokens"),
      replyTokens = reply.objectAt("usage")?.long("completion_tokens"),
      reason = choice?.string("finish_reason")?.let(::finishReason),
      refused = message?.let(::refusal) != null,
    )
    return message?.let(::messageEvents).orEmpty() + state.closing(FinishReason.Unfinished)
  }
}

private class ChatCompletionsStream(scrubber: Scrubber) : StreamReader(scrubber) {
  override fun readEvent(data: String): List<ModelEvent> {
    val chunk = parsedObject(data)
    return when {
      data.trim() == DONE -> finished()
      chunk == null || chunk.holds("error") -> hostFailed(data)
      else -> readChunk(chunk)
    }
  }

  private fun readChunk(chunk: JsonObject): List<ModelEvent> {
    val choice = chunk.objects("choices").firstOrNull()
    val message = choice?.objectAt("delta")
    update { state ->
      state.copy(
        promptTokens = chunk.objectAt("usage")?.long("prompt_tokens") ?: state.promptTokens,
        replyTokens = chunk.objectAt("usage")?.long("completion_tokens") ?: state.replyTokens,
        reason = choice?.string("finish_reason")?.let(::finishReason) ?: state.reason,
        refused = state.refused || message?.let(::refusal) != null,
      )
    }
    return message?.let(::messageEvents).orEmpty()
  }
}

private fun refusal(message: JsonObject): String? = message.string("refusal")?.takeIf(String::isNotEmpty)

private fun messageEvents(message: JsonObject): List<ModelEvent> = listOfNotNull(
  (message.string("reasoning_content") ?: message.string("reasoning"))
    ?.takeIf(String::isNotEmpty)?.let(ModelEvent::ReasoningDelta),
  message.string("content")?.takeIf(String::isNotEmpty)?.let(ModelEvent::TextDelta),
  refusal(message)?.let(ModelEvent::TextDelta),
)

private fun finishReason(finishReason: String): FinishReason = when (finishReason) {
  "length" -> FinishReason.MaxTokens
  "content_filter" -> FinishReason.Refused
  else -> FinishReason.Completed
}
