package dev.marzahn.gloriana.logic.generation

import dev.marzahn.gloriana.logic.app.ProblemLog
import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.chat.ReplyDraft
import dev.marzahn.gloriana.logic.chat.ReplyText
import dev.marzahn.gloriana.logic.chat.storage.ReplyRows
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.api.FinishReason
import dev.marzahn.gloriana.logic.connection.api.ModelApi
import dev.marzahn.gloriana.logic.connection.api.ModelApiError
import dev.marzahn.gloriana.logic.connection.api.ModelEvent
import dev.marzahn.gloriana.logic.connection.api.PromptSection
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val DRAFT_SAVE_INTERVAL: Duration = 500.milliseconds

internal sealed interface StreamEnd {
  data class Finished(val reason: FinishReason) : StreamEnd

  data class Failed(val error: ModelApiError) : StreamEnd
}

internal class ReplyStream(
  private val api: ModelApi,
  private val rows: ReplyRows,
  private val log: ProblemLog,
  private val saveInterval: Duration = DRAFT_SAVE_INTERVAL,
) {
  suspend fun stream(
    sections: List<PromptSection>,
    connection: Connection,
    draft: ReplyDraft,
    arrived: MutableStateFlow<ReplyText>,
  ): StreamEnd = coroutineScope {
    val saving = launch { saveWhileStreaming(draft, arrived) }
    try {
      read(sections, connection, arrived)
    } finally {
      saving.cancel()
    }
  }

  private suspend fun read(
    sections: List<PromptSection>,
    connection: Connection,
    arrived: MutableStateFlow<ReplyText>,
  ): StreamEnd {
    var end: StreamEnd = StreamEnd.Finished(FinishReason.Unfinished)
    api.reply(sections, connection).collect { event ->
      when (event) {
        is ModelEvent.TextDelta -> arrived.update { it + ReplyText(event.text, "") }
        is ModelEvent.ReasoningDelta -> arrived.update { it + ReplyText("", event.text) }
        is ModelEvent.Usage -> Unit
        is ModelEvent.Finish -> end = StreamEnd.Finished(event.reason)
        is ModelEvent.Failed -> end = StreamEnd.Failed(event.error)
      }
    }
    return end
  }

  private suspend fun saveWhileStreaming(draft: ReplyDraft, arrived: MutableStateFlow<ReplyText>): Nothing {
    var saved = ReplyText.Empty
    while (true) {
      delay(saveInterval)
      val latest = arrived.value
      if (latest != saved) {
        failingAs({ log.record("A reply draft could not be saved", it) }) { rows.save(draft, latest) }
        saved = latest
      }
    }
  }
}
