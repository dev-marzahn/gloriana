package dev.marzahn.gloriana.stubhost

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChatCompletion(
  val id: String,
  val `object`: String = "chat.completion",
  val created: Long = 0,
  val model: String,
  val choices: List<ChatChoice>,
  val usage: ChatUsage,
)

@Serializable
internal data class ChatChoice(val index: Int, val message: ChatMessage, val finishReason: ChatFinishReason)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ChatMessage(
  val role: String = "assistant",
  val content: String?,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val reasoningContent: String? = null,
  val refusal: String?,
)

@Serializable
internal data class ChatChunk(
  val id: String,
  val `object`: String = "chat.completion.chunk",
  val created: Long = 0,
  val model: String,
  val choices: List<ChatChunkChoice>,
  val usage: ChatUsage? = null,
)

@Serializable
internal data class ChatChunkChoice(val index: Int, val delta: ChatDelta, val finishReason: ChatFinishReason?)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ChatDelta(
  @EncodeDefault(EncodeDefault.Mode.NEVER) val role: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val content: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val reasoningContent: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val refusal: String? = null,
)

@Serializable
internal enum class ChatFinishReason {
  @SerialName("stop")
  STOP,

  @SerialName("length")
  LENGTH,
}

@Serializable
internal data class ChatUsage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int)
