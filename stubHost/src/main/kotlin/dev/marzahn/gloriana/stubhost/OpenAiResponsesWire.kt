package dev.marzahn.gloriana.stubhost

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal enum class ResponsesStatus {
  @SerialName("in_progress")
  IN_PROGRESS,

  @SerialName("completed")
  COMPLETED,
}

@Serializable
internal data class ResponsesResponse(
  val id: String,
  val `object`: String = "response",
  val createdAt: Long = 0,
  val status: ResponsesStatus,
  val model: String,
  val output: List<ResponsesItem>,
  val usage: ResponsesUsage?,
)

@Serializable
internal data class ResponsesUsage(
  val inputTokens: Int,
  val inputTokensDetails: InputTokensDetails = InputTokensDetails(),
  val outputTokens: Int,
  val outputTokensDetails: OutputTokensDetails,
  val totalTokens: Int,
)

@Serializable
internal data class InputTokensDetails(val cachedTokens: Int = 0)

@Serializable
internal data class OutputTokensDetails(val reasoningTokens: Int)

@Serializable
internal sealed interface ResponsesItem {
  @Serializable
  @SerialName("reasoning")
  data class Reasoning(val id: String, val status: ResponsesStatus, val summary: List<ResponsesPart>) : ResponsesItem

  @Serializable
  @SerialName("message")
  data class Message(
    val id: String,
    val status: ResponsesStatus,
    val role: String = "assistant",
    val content: List<ResponsesPart>,
  ) : ResponsesItem
}

@Serializable
internal sealed interface ResponsesPart {
  @Serializable
  @SerialName("summary_text")
  data class SummaryText(val text: String) : ResponsesPart

  @Serializable
  @SerialName("output_text")
  data class OutputText(
    val text: String,
    val annotations: List<JsonElement> = emptyList(),
    val logprobs: List<JsonElement> = emptyList(),
  ) : ResponsesPart

  @Serializable
  @SerialName("refusal")
  data class Refusal(val refusal: String) : ResponsesPart
}

@Serializable
internal sealed interface ResponsesEvent {
  @Serializable
  @SerialName("response.created")
  data class Created(val response: ResponsesResponse) : ResponsesEvent

  @Serializable
  @SerialName("response.in_progress")
  data class InProgress(val response: ResponsesResponse) : ResponsesEvent

  @Serializable
  @SerialName("response.completed")
  data class Completed(val response: ResponsesResponse) : ResponsesEvent

  @Serializable
  @SerialName("response.output_item.added")
  data class ItemAdded(val outputIndex: Int, val item: ResponsesItem) : ResponsesEvent

  @Serializable
  @SerialName("response.output_item.done")
  data class ItemDone(val outputIndex: Int, val item: ResponsesItem) : ResponsesEvent

  @Serializable
  @SerialName("response.reasoning_summary_part.added")
  data class SummaryPartAdded(
    val itemId: String,
    val outputIndex: Int,
    val summaryIndex: Int,
    val part: ResponsesPart,
  ) : ResponsesEvent

  @Serializable
  @SerialName("response.reasoning_summary_text.delta")
  data class SummaryTextDelta(val itemId: String, val outputIndex: Int, val summaryIndex: Int, val delta: String) :
    ResponsesEvent

  @Serializable
  @SerialName("response.reasoning_summary_text.done")
  data class SummaryTextDone(val itemId: String, val outputIndex: Int, val summaryIndex: Int, val text: String) :
    ResponsesEvent

  @Serializable
  @SerialName("response.reasoning_summary_part.done")
  data class SummaryPartDone(val itemId: String, val outputIndex: Int, val summaryIndex: Int, val part: ResponsesPart) :
    ResponsesEvent

  @Serializable
  @SerialName("response.content_part.added")
  data class ContentPartAdded(
    val itemId: String,
    val outputIndex: Int,
    val contentIndex: Int,
    val part: ResponsesPart,
  ) : ResponsesEvent

  @Serializable
  @SerialName("response.content_part.done")
  data class ContentPartDone(val itemId: String, val outputIndex: Int, val contentIndex: Int, val part: ResponsesPart) :
    ResponsesEvent

  @Serializable
  @SerialName("response.output_text.delta")
  data class OutputTextDelta(
    val itemId: String,
    val outputIndex: Int,
    val contentIndex: Int,
    val delta: String,
    val logprobs: List<JsonElement> = emptyList(),
  ) : ResponsesEvent

  @Serializable
  @SerialName("response.output_text.done")
  data class OutputTextDone(
    val itemId: String,
    val outputIndex: Int,
    val contentIndex: Int,
    val text: String,
    val logprobs: List<JsonElement> = emptyList(),
  ) : ResponsesEvent

  @Serializable
  @SerialName("response.refusal.delta")
  data class RefusalDelta(val itemId: String, val outputIndex: Int, val contentIndex: Int, val delta: String) :
    ResponsesEvent

  @Serializable
  @SerialName("response.refusal.done")
  data class RefusalDone(val itemId: String, val outputIndex: Int, val contentIndex: Int, val refusal: String) :
    ResponsesEvent
}
