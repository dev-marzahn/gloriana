package dev.marzahn.gloriana.stubhost

import io.ktor.http.Headers

internal sealed interface ApiFormat {
  val path: String

  fun key(headers: Headers): String?

  fun whole(reply: Reply, request: ModelRequest): String

  fun stream(reply: Reply, request: ModelRequest): List<SseEvent>

  fun unfinishedStream(reply: Reply, request: ModelRequest): List<SseEvent>

  fun streamError(failure: Failure, request: ModelRequest, sent: Int): SseEvent

  fun error(failure: Failure): String

  fun modelList(models: List<StubModel>): String

  companion object {
    val all: List<ApiFormat> = listOf(AnthropicMessages, ChatCompletions, OpenAiResponses)
    val byPath: Map<String, ApiFormat> = all.associateBy(ApiFormat::path)
  }
}
