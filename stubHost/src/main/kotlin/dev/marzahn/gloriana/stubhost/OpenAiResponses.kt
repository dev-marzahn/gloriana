package dev.marzahn.gloriana.stubhost

import dev.marzahn.gloriana.stubhost.ResponsesEvent.Completed
import dev.marzahn.gloriana.stubhost.ResponsesEvent.ContentPartAdded
import dev.marzahn.gloriana.stubhost.ResponsesEvent.ContentPartDone
import dev.marzahn.gloriana.stubhost.ResponsesEvent.Created
import dev.marzahn.gloriana.stubhost.ResponsesEvent.Failed
import dev.marzahn.gloriana.stubhost.ResponsesEvent.InProgress
import dev.marzahn.gloriana.stubhost.ResponsesEvent.Incomplete
import dev.marzahn.gloriana.stubhost.ResponsesEvent.ItemAdded
import dev.marzahn.gloriana.stubhost.ResponsesEvent.ItemDone
import dev.marzahn.gloriana.stubhost.ResponsesEvent.OutputTextDelta
import dev.marzahn.gloriana.stubhost.ResponsesEvent.OutputTextDone
import dev.marzahn.gloriana.stubhost.ResponsesEvent.RefusalDelta
import dev.marzahn.gloriana.stubhost.ResponsesEvent.RefusalDone
import dev.marzahn.gloriana.stubhost.ResponsesEvent.SummaryPartAdded
import dev.marzahn.gloriana.stubhost.ResponsesEvent.SummaryPartDone
import dev.marzahn.gloriana.stubhost.ResponsesEvent.SummaryTextDelta
import dev.marzahn.gloriana.stubhost.ResponsesEvent.SummaryTextDone
import dev.marzahn.gloriana.stubhost.ResponsesStatus.COMPLETED
import dev.marzahn.gloriana.stubhost.ResponsesStatus.IN_PROGRESS
import io.ktor.http.Headers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private const val RESPONSE_ID = "resp_stub"
private const val SEQUENCE_NUMBER = "sequence_number"
private const val ONLY_PART = 0

internal data object OpenAiResponses : ApiFormat {
  override val path = "responses"

  override fun key(headers: Headers): String? = bearerToken(headers)

  override fun whole(reply: Reply, request: ModelRequest): String =
    wireJson.encodeToString(ResponsesResponse.serializer(), completedResponse(request, reply, shapes(reply)))

  override fun stream(reply: Reply, request: ModelRequest): List<SseEvent> {
    val shapes = shapes(reply)
    val events = listOf(Created(createdResponse(request)), InProgress(createdResponse(request))) +
      shapes.flatMapIndexed { outputIndex, shape ->
        listOf(ItemAdded(outputIndex, shape.opening)) + shape.partEvents + ItemDone(outputIndex, shape.done)
      } +
      closing(completedResponse(request, reply, shapes))
    return events.mapIndexed(::numbered)
  }

  override fun unfinishedStream(reply: Reply, request: ModelRequest): List<SseEvent> =
    stream(reply, request).dropLast(1)

  override fun streamError(failure: Failure, request: ModelRequest, sent: Int): SseEvent {
    val failed = createdResponse(request).copy(status = ResponsesStatus.FAILED, error = openAiErrorDetail(failure))
    return numbered(sent, Failed(failed))
  }

  private fun closing(response: ResponsesResponse): ResponsesEvent =
    if (response.status == ResponsesStatus.INCOMPLETE) Incomplete(response) else Completed(response)

  override fun error(failure: Failure): String = openAiError(failure)

  override fun modelList(models: List<StubModel>): String = openAiModelList(models)

  private fun numbered(sequenceNumber: Int, event: ResponsesEvent): SseEvent {
    val json = wireJson.encodeToJsonElement(ResponsesEvent.serializer(), event).jsonObject
    return SseEvent.named(JsonObject(json + (SEQUENCE_NUMBER to JsonPrimitive(sequenceNumber))))
  }

  private fun shapes(reply: Reply): List<ItemShape> = reply.segments.mapIndexed(::shape)
}

private class ItemShape(val opening: ResponsesItem, val partEvents: List<ResponsesEvent>, val done: ResponsesItem)

private fun shape(outputIndex: Int, segment: Segment): ItemShape {
  val messageId = "msg_stub_$outputIndex"
  return when (segment) {
    is Segment.Reasoning -> {
      val id = "rs_stub_$outputIndex"
      val full = ResponsesPart.SummaryText(segment.text)
      ItemShape(
        opening = ResponsesItem.Reasoning(id, IN_PROGRESS, summary = emptyList()),
        partEvents = listOf(SummaryPartAdded(id, outputIndex, ONLY_PART, ResponsesPart.SummaryText(""))) +
          segment.pieces.map { SummaryTextDelta(id, outputIndex, ONLY_PART, it) } +
          SummaryTextDone(id, outputIndex, ONLY_PART, segment.text) +
          SummaryPartDone(id, outputIndex, ONLY_PART, full),
        done = ResponsesItem.Reasoning(id, COMPLETED, summary = listOf(full)),
      )
    }
    is Segment.Text -> message(
      messageId,
      outputIndex,
      empty = ResponsesPart.OutputText(""),
      full = ResponsesPart.OutputText(segment.text),
      textEvents = segment.pieces.map { OutputTextDelta(messageId, outputIndex, ONLY_PART, it) } +
        OutputTextDone(messageId, outputIndex, ONLY_PART, segment.text),
    )
    is Segment.Refusal -> message(
      messageId,
      outputIndex,
      empty = ResponsesPart.Refusal(""),
      full = ResponsesPart.Refusal(segment.text),
      textEvents = segment.pieces.map { RefusalDelta(messageId, outputIndex, ONLY_PART, it) } +
        RefusalDone(messageId, outputIndex, ONLY_PART, segment.text),
    )
  }
}

private fun message(
  id: String,
  outputIndex: Int,
  empty: ResponsesPart,
  full: ResponsesPart,
  textEvents: List<ResponsesEvent>,
) = ItemShape(
  opening = ResponsesItem.Message(id, IN_PROGRESS, content = emptyList()),
  partEvents = listOf(ContentPartAdded(id, outputIndex, ONLY_PART, empty)) + textEvents +
    ContentPartDone(id, outputIndex, ONLY_PART, full),
  done = ResponsesItem.Message(id, COMPLETED, content = listOf(full)),
)

private fun createdResponse(request: ModelRequest) =
  ResponsesResponse(id = RESPONSE_ID, status = IN_PROGRESS, model = request.model, output = emptyList(), usage = null)

private fun completedResponse(request: ModelRequest, reply: Reply, shapes: List<ItemShape>) = ResponsesResponse(
  id = RESPONSE_ID,
  status = if (reply.hitLimit) ResponsesStatus.INCOMPLETE else COMPLETED,
  incompleteDetails = if (reply.hitLimit) IncompleteDetails("max_output_tokens") else null,
  model = request.model,
  output = shapes.map(ItemShape::done),
  usage = ResponsesUsage(
    inputTokens = reply.tokens.input,
    outputTokens = reply.tokens.output,
    outputTokensDetails = OutputTokensDetails(reasoningTokens = reply.tokens.reasoning),
    totalTokens = reply.tokens.total,
  ),
)
