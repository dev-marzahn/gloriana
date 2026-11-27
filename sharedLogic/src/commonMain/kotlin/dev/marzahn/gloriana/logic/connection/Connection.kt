package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.keys.SealedSecret
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

enum class ApiFormat(val samplerRanges: Map<Sampler, SamplerRange>) {
  AnthropicMessages(anthropicSamplerRanges),
  OpenAiChatCompletions(chatCompletionsSamplerRanges),
  OpenAiResponses(responsesSamplerRanges),
}

data class ConnectionFields(
  val name: String,
  val format: ApiFormat,
  val baseUrl: String,
  val model: String,
  val contextSize: Int = DEFAULT_CONTEXT_SIZE,
  val maxReplyTokens: Int = DEFAULT_MAX_REPLY_TOKENS,
  val streaming: Boolean = true,
  val includeUsage: Boolean = true,
  val samplers: Samplers = Samplers(),
  val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
  val customBody: JsonObject = JsonObject(emptyMap()),
) {
  fun switchedTo(format: ApiFormat): ConnectionFields = copy(format = format)

  companion object {
    const val DEFAULT_CONTEXT_SIZE = 16_384
    const val DEFAULT_MAX_REPLY_TOKENS = 1_024
    const val DEFAULT_TIMEOUT_SECONDS = 60
  }
}

class CustomHeader(val name: String, val value: SealedSecret)

class Connection(
  val id: Uuid,
  val fields: ConnectionFields,
  val key: SealedSecret?,
  val headers: List<CustomHeader>,
) {
  val holdsSecrets: Boolean get() = key != null || headers.isNotEmpty()
}

enum class ConnectionTemplate(val templateName: String, val format: ApiFormat, val baseUrl: String) {
  Anthropic("Anthropic", ApiFormat.AnthropicMessages, "https://api.anthropic.com/v1"),
  OpenAi("OpenAI", ApiFormat.OpenAiChatCompletions, "https://api.openai.com/v1"),
  OpenRouter("OpenRouter", ApiFormat.OpenAiChatCompletions, "https://openrouter.ai/api/v1"),
  ;

  fun fields(): ConnectionFields = ConnectionFields(name = templateName, format = format, baseUrl = baseUrl, model = "")
}

internal fun nameKey(name: String): String = name.lowercase()
