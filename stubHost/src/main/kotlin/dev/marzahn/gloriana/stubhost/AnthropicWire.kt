package dev.marzahn.gloriana.stubhost

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AnthropicMessage(
  val id: String,
  val type: String = "message",
  val role: String = "assistant",
  val model: String,
  val content: List<AnthropicBlock>,
  val stopReason: AnthropicStopReason?,
  val stopSequence: String? = null,
  val usage: AnthropicUsage,
)

@Serializable
internal data class AnthropicUsage(val inputTokens: Int, val outputTokens: Int)

@Serializable
internal sealed interface AnthropicBlock {
  @Serializable
  @SerialName("thinking")
  data class Thinking(val thinking: String, val signature: String) : AnthropicBlock

  @Serializable
  @SerialName("text")
  data class Text(val text: String) : AnthropicBlock
}

@Serializable
internal sealed interface AnthropicDelta {
  @Serializable
  @SerialName("thinking_delta")
  data class Thinking(val thinking: String) : AnthropicDelta

  @Serializable
  @SerialName("signature_delta")
  data class Signature(val signature: String) : AnthropicDelta

  @Serializable
  @SerialName("text_delta")
  data class Text(val text: String) : AnthropicDelta
}

@Serializable
internal data class AnthropicStop(val stopReason: AnthropicStopReason, val stopSequence: String? = null)

@Serializable
internal enum class AnthropicStopReason {
  @SerialName("end_turn")
  END_TURN,

  @SerialName("refusal")
  REFUSAL,

  @SerialName("max_tokens")
  MAX_TOKENS,
}

@Serializable
internal data class AnthropicOutputUsage(val outputTokens: Int)

@Serializable
internal sealed interface AnthropicEvent {
  @Serializable
  @SerialName("message_start")
  data class MessageStart(val message: AnthropicMessage) : AnthropicEvent

  @Serializable
  @SerialName("ping")
  data object Ping : AnthropicEvent

  @Serializable
  @SerialName("content_block_start")
  data class BlockStart(val index: Int, val contentBlock: AnthropicBlock) : AnthropicEvent

  @Serializable
  @SerialName("content_block_delta")
  data class BlockDelta(val index: Int, val delta: AnthropicDelta) : AnthropicEvent

  @Serializable
  @SerialName("content_block_stop")
  data class BlockStop(val index: Int) : AnthropicEvent

  @Serializable
  @SerialName("message_delta")
  data class MessageDelta(val delta: AnthropicStop, val usage: AnthropicOutputUsage) : AnthropicEvent

  @Serializable
  @SerialName("message_stop")
  data object MessageStop : AnthropicEvent
}

@Serializable
internal data class AnthropicError(val type: String = "error", val error: AnthropicErrorDetail)

@Serializable
internal data class AnthropicErrorDetail(val type: String, val message: String)
