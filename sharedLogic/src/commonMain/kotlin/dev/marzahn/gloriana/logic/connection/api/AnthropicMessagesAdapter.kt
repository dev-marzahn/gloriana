package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.STREAM
import dev.marzahn.gloriana.logic.connection.mergedWith
import dev.marzahn.gloriana.logic.json.long
import dev.marzahn.gloriana.logic.json.objectAt
import dev.marzahn.gloriana.logic.json.objects
import dev.marzahn.gloriana.logic.json.parsedObject
import dev.marzahn.gloriana.logic.json.string
import dev.marzahn.gloriana.logic.keys.HeaderName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val VERSION = "2023-06-01"
private const val MODEL_PAGE_LIMIT = 1_000
internal const val OPENING_USER_TURN = "Let's get started."

internal object AnthropicMessagesAdapter : FormatAdapter {
  override val path = "messages"
  override val modelListTarget = "models?limit=$MODEL_PAGE_LIMIT"
  override val fixedHeaders = listOf(HostHeader(HeaderName.of("anthropic-version"), VERSION))

  override val keyHeaderName = HeaderName.of("x-api-key")

  override fun keyValue(key: String) = key

  override fun body(sections: List<PromptSection>, fields: ConnectionFields, samplers: SentSamplers) =
    buildJsonObject {
      put("model", fields.model)
      put("max_tokens", fields.maxReplyTokens)
      leadingSystemText(sections)?.let { put("system", it) }
      put("messages", roleMessages(turns(sections)))
      put(STREAM, fields.streaming)
      samplers.putInto(this)
    }.mergedWith(fields.customBody)

  override fun streamReader(scrubber: Scrubber): StreamReader = AnthropicStream(scrubber)

  override fun whole(reply: JsonObject): List<ModelEvent> {
    val state = ReplyState(
      promptTokens = reply.objectAt("usage")?.let(::promptTokens),
      replyTokens = reply.objectAt("usage")?.long("output_tokens"),
      reason = reply.string("stop_reason")?.let(::finishReason),
    )
    return reply.objects("content").mapNotNull(::block) + state.closing(FinishReason.Unfinished)
  }

  private fun block(block: JsonObject): ModelEvent? = when (block.string("type")) {
    "text" -> block.string("text")?.let(ModelEvent::TextDelta)
    "thinking" -> block.string("thinking")?.let(ModelEvent::ReasoningDelta)
    else -> null
  }

  private fun turns(sections: List<PromptSection>): List<PromptSection> = buildList {
    afterLeadingSystem(sections).forEach { section ->
      val role = if (section.role == SectionRole.Assistant) SectionRole.Assistant else SectionRole.User
      val last = lastOrNull()
      if (last?.role == role) {
        set(lastIndex, PromptSection(role, last.text + SECTION_JOIN + section.text))
      } else {
        add(PromptSection(role, section.text))
      }
    }
    if (firstOrNull()?.role != SectionRole.User) add(0, PromptSection(SectionRole.User, OPENING_USER_TURN))
  }
}

private class AnthropicStream(scrubber: Scrubber) : StreamReader(scrubber) {
  override fun readEvent(data: String): List<ModelEvent> {
    val event = parsedObject(data) ?: return hostFailed(data)
    return when (event.string("type")) {
      "message_start" -> {
        update { it.copy(promptTokens = event.objectAt("message")?.objectAt("usage")?.let(::promptTokens)) }
        emptyList()
      }
      "content_block_delta" -> listOfNotNull(event.objectAt("delta")?.let(::delta))
      "message_delta" -> {
        update { state ->
          state.copy(
            reason = event.objectAt("delta")?.string("stop_reason")?.let(::finishReason) ?: state.reason,
            replyTokens = event.objectAt("usage")?.long("output_tokens") ?: state.replyTokens,
          )
        }
        emptyList()
      }
      "message_stop" -> finished()
      "error" -> hostFailed(data)
      else -> emptyList()
    }
  }

  private fun delta(delta: JsonObject): ModelEvent? = when (delta.string("type")) {
    "text_delta" -> delta.string("text")?.let(ModelEvent::TextDelta)
    "thinking_delta" -> delta.string("thinking")?.let(ModelEvent::ReasoningDelta)
    else -> null
  }
}

private val promptTokenCounts = listOf("input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens")

private fun promptTokens(usage: JsonObject): Long? =
  promptTokenCounts.mapNotNull(usage::long).takeIf { it.isNotEmpty() }?.sum()

private fun finishReason(stopReason: String): FinishReason = when (stopReason) {
  "max_tokens" -> FinishReason.MaxTokens
  "refusal" -> FinishReason.Refused
  else -> FinishReason.Completed
}
