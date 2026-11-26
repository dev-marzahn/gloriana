package dev.marzahn.gloriana.stubhost

import io.ktor.http.Headers

private const val ID = "chatcmpl-stub"

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
        choices = listOf(ChatChoice(index = 0, message = message, finishReason = ChatFinishReason.STOP)),
        usage = usage(reply.tokens),
      ),
    )
  }

  override fun stream(reply: Reply, request: ModelRequest): List<SseEvent> {
    fun chunk(delta: ChatDelta, finishReason: ChatFinishReason? = null) =
      ChatChunk(id = ID, model = request.model, choices = listOf(ChatChunkChoice(0, delta, finishReason)))

    val chunks = listOf(chunk(ChatDelta(role = "assistant", content = ""))) +
      reply.segments.flatMap { segment -> segment.pieces.map { chunk(delta(segment, it)) } } +
      chunk(ChatDelta(), finishReason = ChatFinishReason.STOP)
    val usageChunk = ChatChunk(id = ID, model = request.model, choices = emptyList(), usage = usage(reply.tokens))
    val sent = if (request.includesUsage) chunks + usageChunk else chunks
    return sent.map { SseEvent.unnamed(ChatChunk.serializer(), it) } + SseEvent(name = null, data = "[DONE]")
  }

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
