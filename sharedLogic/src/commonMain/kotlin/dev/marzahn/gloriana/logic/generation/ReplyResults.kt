package dev.marzahn.gloriana.logic.generation

import dev.marzahn.gloriana.logic.chat.ChatDeleteResult
import dev.marzahn.gloriana.logic.chat.ReplyText
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.api.ModelApiError
import kotlin.uuid.Uuid

sealed interface ReplyStartResult {
  data class Started(val messageId: Uuid) : ReplyStartResult

  data object ChatGone : ReplyStartResult

  data object CharacterGone : ReplyStartResult

  data object PersonaGone : ReplyStartResult

  data object PresetGone : ReplyStartResult

  data object PresetUnreadable : ReplyStartResult

  data object NoConnection : ReplyStartResult

  data object ConnectionGone : ReplyStartResult

  data object ConnectionUnreadable : ReplyStartResult

  data class KeysNotReady(val connection: Connection, val error: ModelApiError) : ReplyStartResult

  data object ReplyRunning : ReplyStartResult

  data object NothingToRetry : ReplyStartResult

  data object StorageFull : ReplyStartResult

  data object Failed : ReplyStartResult
}

enum class UnfinishedWhy { Stopped, BackgroundTimeRanOut, StreamEnded }

sealed interface ReplyFailure {
  data class Host(val error: ModelApiError) : ReplyFailure

  data object Refused : ReplyFailure

  data object GlorianaProblem : ReplyFailure

  data object NotSaved : ReplyFailure

  data object NotSavedStorageFull : ReplyFailure
}

sealed interface LiveReply {
  val messageId: Uuid

  data class Writing(override val messageId: Uuid, val text: ReplyText) : LiveReply

  data class Failed(override val messageId: Uuid, val failure: ReplyFailure) : LiveReply

  data class Unfinished(override val messageId: Uuid, val why: UnfinishedWhy) : LiveReply
}

sealed interface ReplyDeleteResult {
  data object ReplyRunning : ReplyDeleteResult

  data class Done(val result: ChatDeleteResult) : ReplyDeleteResult
}
