package dev.marzahn.gloriana.stubhost

import io.ktor.http.Headers

internal sealed interface ApiFormat {
  val path: String

  fun key(headers: Headers): String?

  fun whole(reply: Reply, request: ModelRequest): String

  fun stream(reply: Reply, request: ModelRequest): List<SseEvent>

  fun error(failure: Failure): String

  companion object {
    val all: List<ApiFormat> = listOf(AnthropicMessages, ChatCompletions, OpenAiResponses)
    val byPath: Map<String, ApiFormat> = all.associateBy(ApiFormat::path)
  }
}
