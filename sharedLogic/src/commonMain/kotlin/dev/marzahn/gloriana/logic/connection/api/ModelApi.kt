package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.Connection
import kotlinx.coroutines.flow.Flow

enum class SectionRole {
  System,
  User,
  Assistant,
}

class PromptSection(val role: SectionRole, val text: String)

enum class FinishReason {
  Completed,
  MaxTokens,
  Refused,
  Unfinished,
}

sealed interface ModelEvent {
  data class TextDelta(val text: String) : ModelEvent

  data class ReasoningDelta(val text: String) : ModelEvent

  data class Usage(val promptTokens: Long?, val replyTokens: Long?) : ModelEvent

  data class Finish(val reason: FinishReason) : ModelEvent

  data class Failed(val error: ModelApiError) : ModelEvent
}

fun interface ModelApi {
  fun reply(sections: List<PromptSection>, connection: Connection): Flow<ModelEvent>
}
