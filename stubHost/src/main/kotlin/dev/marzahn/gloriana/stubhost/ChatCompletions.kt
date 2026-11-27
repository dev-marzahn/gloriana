package dev.marzahn.gloriana.stubhost

import io.ktor.http.Headers

private const val ID = "chatcmpl-stub"

private fun finishReason(reply: Reply) = if (reply.hitLimit) ChatFinishReason.LENGTH else ChatFinishReason.STOP

internal data object ChatCompletions : ApiFormat {
  override val path = "chat/completions"

  override fun key(headers: Headers): String? = bearerToken(headers)

  override fun whole(reply: Reply, request: ModelRequest): String {
    val parts = reply.segments.map { delta(it, it.text) }
    val message = ChatMessage(
      content = parts.mapNotNull(ChatDelta::content).joinedOrNull(),
      reasoningContent = parts.mapNotNull(ChatDelta::reasoningContent).joinedOrNull(),
      refusal = parts.mapNotNull(ChatDelta::refusal).joinedOrNull(),
    )
    return wireJson.encodeToString(
      ChatCompletion.serializer(),
      ChatCompletion(
        id = ID,
        model = request.model,
        choices = listOf(ChatChoice(index = 0, message = message, finishReason = finishReason(reply))),
        usage = usage(reply.tokens),
      ),
    )
  }

  override fun stream(reply: Reply, request: ModelRequest): List<SseEvent> {
    val chunks = contentChunks(reply, request) + chunk(request, ChatDelta(), finishReason = finishReason(reply))
    val usageChunk = ChatChunk(id = ID, model = request.model, choices = emptyList(), usage = usage(reply.tokens))
    val sent = if (request.includesUsage) chunks + usageChunk else chunks
    return sent.map { SseEvent.unnamed(ChatChunk.serializer(), it) } + SseEvent(name = null, data = "[DONE]")
  }

  override fun unfinishedStream(reply: Reply, request: ModelRequest): List<SseEvent> =
    contentChunks(reply, request).map { SseEvent.unnamed(ChatChunk.serializer(), it) }

  override fun streamError(failure: Failure, request: ModelRequest, sent: Int): SseEvent =
    SseEvent(name = null, data = error(failure))

  private fun contentChunks(reply: Reply, request: ModelRequest): List<ChatChunk> =
    listOf(chunk(request, ChatDelta(role = "assistant", content = ""))) +
      reply.segments.flatMap { segment -> segment.pieces.map { chunk(request, delta(segment, it)) } }

  private fun chunk(request: ModelRequest, delta: ChatDelta, finishReason: ChatFinishReason? = null) =
    ChatChunk(id = ID, model = request.model, choices = listOf(ChatChunkChoice(0, delta, finishReason)))

  override fun error(failure: Failure): String = openAiError(failure)

  private fun delta(segment: Segment, text: String): ChatDelta = when (segment) {
    is Segment.Reasoning -> ChatDelta(reasoningContent = text)
    is Segment.Text -> ChatDelta(content = text)
    is Segment.Refusal -> ChatDelta(refusal = text)
  }

  private fun List<String>.joinedOrNull(): String? = if (isEmpty()) null else joinToString("")

  private fun usage(tokens: Tokens) =
    ChatUsage(promptTokens = tokens.input, completionTokens = tokens.output, totalTokens = tokens.total)
}
