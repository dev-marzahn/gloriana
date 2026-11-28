package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.STREAM
import dev.marzahn.gloriana.logic.connection.mergedWith
import dev.marzahn.gloriana.logic.json.long
import dev.marzahn.gloriana.logic.json.objectAt
import dev.marzahn.gloriana.logic.json.objects
import dev.marzahn.gloriana.logic.json.parsedObject
import dev.marzahn.gloriana.logic.json.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object ResponsesAdapter : FormatAdapter {
  override val path = "responses"
  override val modelListTarget = "models"
  override val fixedHeaders = emptyList<HostHeader>()

  override val keyHeaderName = AuthorizationHeader

  override fun keyValue(key: String) = bearer(key)

  override fun isErrorReply(reply: JsonObject) = super.isErrorReply(reply) || reply.string("status") == "failed"

  override fun body(sections: List<PromptSection>, fields: ConnectionFields, samplers: SentSamplers) =
    buildJsonObject {
      put("model", fields.model)
      leadingSystemText(sections)?.let { put("instructions", it) }
      put("input", roleMessages(afterLeadingSystem(sections)))
      put("max_output_tokens", fields.maxReplyTokens)
      put("store", false)
      put(STREAM, fields.streaming)
      samplers.putInto(this)
    }.mergedWith(fields.customBody)

  override fun streamReader(scrubber: Scrubber): StreamReader = ResponsesStream(scrubber)

  override fun whole(reply: JsonObject): List<ModelEvent> =
    reply.objects("output").flatMap(::outputEvents) +
      endState(reply).copy(refused = refusedIn(reply)).closing(FinishReason.Unfinished)
}

private class ResponsesStream(scrubber: Scrubber) : StreamReader(scrubber) {
  override fun readEvent(data: String): List<ModelEvent> {
    val event = parsedObject(data) ?: return hostFailed(data)
    return when (event.string("type")) {
      "response.output_text.delta" -> listOfNotNull(event.string("delta")?.let(ModelEvent::TextDelta))
      "response.reasoning_summary_text.delta", "response.reasoning_text.delta" ->
        listOfNotNull(event.string("delta")?.let(ModelEvent::ReasoningDelta))
      "response.refusal.delta" -> {
        update { it.copy(refused = true) }
        listOfNotNull(event.string("delta")?.let(ModelEvent::TextDelta))
      }
      "response.completed" -> ended(event, impliedStatus = "completed", data)
      "response.incomplete" -> ended(event, impliedStatus = "incomplete", data)
      "response.failed", "error" -> hostFailed(data)
      else -> emptyList()
    }
  }

  private fun ended(event: JsonObject, impliedStatus: String, data: String): List<ModelEvent> {
    val response = event.objectAt("response") ?: return hostFailed(data)
    val end = endState(response, impliedStatus)
    update { state -> end.copy(refused = state.refused) }
    return finished()
  }
}

private fun endState(response: JsonObject, impliedStatus: String? = null) = ReplyState(
  promptTokens = response.objectAt("usage")?.long("input_tokens"),
  replyTokens = response.objectAt("usage")?.long("output_tokens"),
  reason = when (response.string("status") ?: impliedStatus) {
    "completed" -> FinishReason.Completed
    "incomplete" -> incompleteReason(response)
    else -> FinishReason.Unfinished
  },
)

private fun incompleteReason(response: JsonObject): FinishReason =
  if (response.objectAt("incomplete_details")?.string("reason") == "max_output_tokens") {
    FinishReason.MaxTokens
  } else {
    FinishReason.Unfinished
  }

private fun outputEvents(item: JsonObject): List<ModelEvent> = when (item.string("type")) {
  "reasoning" -> item.objects("summary").mapNotNull { part -> part.string("text")?.let(ModelEvent::ReasoningDelta) }
  "message" -> item.objects("content").mapNotNull { part ->
    when (part.string("type")) {
      "output_text" -> part.string("text")?.let(ModelEvent::TextDelta)
      "refusal" -> part.string("refusal")?.let(ModelEvent::TextDelta)
      else -> null
    }
  }
  else -> emptyList()
}

private fun refusedIn(reply: JsonObject): Boolean = reply.objects("output")
  .flatMap { it.objects("content") }
  .any { it.string("type") == "refusal" }
