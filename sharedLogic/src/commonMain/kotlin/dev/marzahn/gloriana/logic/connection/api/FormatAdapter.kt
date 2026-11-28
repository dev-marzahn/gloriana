package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.json.holds
import dev.marzahn.gloriana.logic.json.string
import dev.marzahn.gloriana.logic.keys.HeaderName
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonObject

internal interface FormatAdapter {
  val path: String
  val modelListTarget: String
  val fixedHeaders: List<HostHeader>
  val keyHeaderName: HeaderName

  fun keyValue(key: String): String

  fun body(sections: List<PromptSection>, fields: ConnectionFields, samplers: SentSamplers): JsonObject

  fun streamReader(scrubber: Scrubber): StreamReader

  fun isErrorReply(reply: JsonObject): Boolean = reply.holds("error") || reply.string("type") == "error"

  fun whole(reply: JsonObject): List<ModelEvent>
}

internal fun FormatAdapter.keyHeader(key: String) = HostHeader(keyHeaderName, keyValue(key))

internal val ApiFormat.adapter: FormatAdapter
  get() = when (this) {
    ApiFormat.AnthropicMessages -> AnthropicMessagesAdapter
    ApiFormat.OpenAiChatCompletions -> ChatCompletionsAdapter
    ApiFormat.OpenAiResponses -> ResponsesAdapter
  }

internal val AuthorizationHeader: HeaderName = HeaderName.of(HttpHeaders.Authorization)

internal fun bearer(key: String) = "Bearer $key"
