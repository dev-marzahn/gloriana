package dev.marzahn.gloriana.stubhost

import io.ktor.http.Headers

private const val SIGNATURE = "stub-signature"
private const val ERROR_EVENT = "error"
private val closingEvents = setOf("message_delta", "message_stop")

internal data object AnthropicMessages : ApiFormat {
  override val path = "messages"

  override fun key(headers: Headers): String? = headers["x-api-key"]?.takeIf(String::isNotEmpty)

  override fun whole(reply: Reply, request: ModelRequest): String = wireJson.encodeToString(
    AnthropicMessage.serializer(),
    message(request, reply.segments.map { shape(it).full }, stopReason(reply), reply.tokens),
  )

  override fun stream(reply: Reply, request: ModelRequest): List<SseEvent> {
    val opening = message(request, emptyList(), stopReason = null, reply.tokens.copy(output = 0))
    val events = listOf(AnthropicEvent.MessageStart(opening), AnthropicEvent.Ping) +
      reply.segments.flatMapIndexed { index, segment -> shape(segment).events(index) } +
      AnthropicEvent.MessageDelta(AnthropicStop(stopReason(reply)), AnthropicOutputUsage(reply.tokens.output)) +
      AnthropicEvent.MessageStop
    return events.map { SseEvent.namedByType(AnthropicEvent.serializer(), it) }
  }

  override fun unfinishedStream(reply: Reply, request: ModelRequest): List<SseEvent> =
    stream(reply, request).filterNot { it.name in closingEvents }

  override fun streamError(failure: Failure, request: ModelRequest, sent: Int): SseEvent =
    SseEvent(ERROR_EVENT, error(failure))

  override fun error(failure: Failure): String = wireJson.encodeToString(
    AnthropicError.serializer(),
    AnthropicError(error = AnthropicErrorDetail(errorType(failure.kind), failure.message)),
  )

  private fun message(
    request: ModelRequest,
    content: List<AnthropicBlock>,
    stopReason: AnthropicStopReason?,
    tokens: Tokens,
  ) = AnthropicMessage(
    id = "msg_stub",
    model = request.model,
    content = content,
    stopReason = stopReason,
    usage = AnthropicUsage(inputTokens = tokens.input, outputTokens = tokens.output),
  )

  private fun stopReason(reply: Reply): AnthropicStopReason = when {
    reply.refused -> AnthropicStopReason.REFUSAL
    reply.hitLimit -> AnthropicStopReason.MAX_TOKENS
    else -> AnthropicStopReason.END_TURN
  }
}

private class BlockShape(val start: AnthropicBlock, val deltas: List<AnthropicDelta>, val full: AnthropicBlock) {
  fun events(index: Int): List<AnthropicEvent> =
    listOf(AnthropicEvent.BlockStart(index, start)) +
      deltas.map { AnthropicEvent.BlockDelta(index, it) } +
      AnthropicEvent.BlockStop(index)
}

private fun shape(segment: Segment): BlockShape = when (segment) {
  is Segment.Reasoning -> BlockShape(
    start = AnthropicBlock.Thinking(thinking = "", signature = ""),
    deltas = segment.pieces.map(AnthropicDelta::Thinking) + AnthropicDelta.Signature(SIGNATURE),
    full = AnthropicBlock.Thinking(segment.text, SIGNATURE),
  )
  is Segment.Text, is Segment.Refusal -> BlockShape(
    start = AnthropicBlock.Text(text = ""),
    deltas = segment.pieces.map(AnthropicDelta::Text),
    full = AnthropicBlock.Text(segment.text),
  )
}

private fun errorType(kind: FailureKind): String = when (kind) {
  FailureKind.BAD_REQUEST -> "invalid_request_error"
  FailureKind.KEY_REJECTED -> "authentication_error"
  FailureKind.FORBIDDEN -> "permission_error"
  FailureKind.NOT_FOUND -> "not_found_error"
  FailureKind.RATE_LIMITED -> "rate_limit_error"
  FailureKind.HOST_ERROR -> "api_error"
}
