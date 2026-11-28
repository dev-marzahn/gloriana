package dev.marzahn.gloriana.logic.generation

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.chat.ChatDeleteResult
import dev.marzahn.gloriana.logic.chat.ChatRepository
import dev.marzahn.gloriana.logic.chat.LeftoverDraftsAtStart
import dev.marzahn.gloriana.logic.chat.ReplyDraft
import dev.marzahn.gloriana.logic.chat.storage.ReplyRows
import dev.marzahn.gloriana.logic.prompt.withNamesFilled
import dev.marzahn.gloriana.logic.tasks.TaskNotice
import dev.marzahn.gloriana.logic.tasks.TaskRunner
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

private sealed interface RowsWritten {
  class Ready(val draft: ReplyDraft) : RowsWritten

  class Refused(val result: ReplyStartResult) : RowsWritten
}

class ReplyWriter internal constructor(
  private val chats: ChatRepository,
  private val preparation: ReplyPreparation,
  private val rows: ReplyRows,
  private val leftoverDrafts: LeftoverDraftsAtStart,
  private val runner: TaskRunner,
  private val run: ReplyRun,
  private val live: LiveReplies,
) {
  val replies: Flow<Map<Uuid, LiveReply>> = live.replies

  suspend fun send(chatId: Uuid, text: String, notice: Flow<TaskNotice>): ReplyStartResult =
    start(chatId, notice, refusedWhenNoRow = ReplyStartResult.ChatGone) { material ->
      rows.startReply(chatId, text.withNamesFilled(material.prompt.names), material.characterId)
    }

  suspend fun retry(chatId: Uuid, notice: Flow<TaskNotice>): ReplyStartResult =
    start(chatId, notice, refusedWhenNoRow = ReplyStartResult.NothingToRetry) { retryRows(chatId) }

  fun stop(chatId: Uuid) = live.stop(chatId)

  suspend fun delete(chatId: Uuid): ReplyDeleteResult {
    if (!live.claim(chatId)) return ReplyDeleteResult.ReplyRunning
    return try {
      val deleted = chats.delete(chatId)
      if (deleted != ChatDeleteResult.Failed) live.changeSettled { it - chatId }
      ReplyDeleteResult.Done(deleted)
    } finally {
      live.release(chatId)
    }
  }

  private suspend fun start(
    chatId: Uuid,
    notice: Flow<TaskNotice>,
    refusedWhenNoRow: ReplyStartResult,
    writeRows: suspend (ReplyMaterial) -> ReplyDraft?,
  ): ReplyStartResult {
    if (!live.claim(chatId)) return ReplyStartResult.ReplyRunning
    val started = runCatching {
      when (val prepared = preparation.prepare(chatId)) {
        is Preparation.Refused -> prepared.result
        is Preparation.Ready -> startWith(chatId, prepared.material, notice, refusedWhenNoRow, writeRows)
      }
    }
    if (started.getOrNull() !is ReplyStartResult.Started) live.release(chatId)
    return started.getOrThrow()
  }

  private suspend fun startWith(
    chatId: Uuid,
    material: ReplyMaterial,
    notice: Flow<TaskNotice>,
    refusedWhenNoRow: ReplyStartResult,
    writeRows: suspend (ReplyMaterial) -> ReplyDraft?,
  ): ReplyStartResult {
    leftoverDrafts.kept.await()
    val written = failingAs({ RowsWritten.Refused(rowsNotWritten(it)) }) {
      writeRows(material)?.let(RowsWritten::Ready) ?: RowsWritten.Refused(refusedWhenNoRow)
    }
    return when (written) {
      is RowsWritten.Refused -> written.result
      is RowsWritten.Ready -> launched(RunningReply(chatId, written.draft, material), notice)
    }
  }

  private suspend fun retryRows(chatId: Uuid): ReplyDraft? {
    val draft = rows.startRetry(chatId)
    if (draft == null) live.changeSettled { it - chatId }
    return draft
  }

  private fun rowsNotWritten(cause: Exception): ReplyStartResult =
    if (cause.isStorageFull()) ReplyStartResult.StorageFull else ReplyStartResult.Failed

  private fun launched(reply: RunningReply, notice: Flow<TaskNotice>): ReplyStartResult {
    live.track(reply)
    runner.launch(notice, onBackgroundTimeOut = { run.settleCutOffByBackgroundTime(reply) }) {
      run.writeAndSettle(reply)
    }
    return ReplyStartResult.Started(reply.draft.messageId)
  }
}
