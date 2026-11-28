package dev.marzahn.gloriana.logic.generation

import dev.marzahn.gloriana.logic.app.ProblemLog
import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.chat.ChatRepository
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.chat.storage.DraftSettled
import dev.marzahn.gloriana.logic.chat.storage.ReplyRows
import dev.marzahn.gloriana.logic.connection.api.FinishReason
import dev.marzahn.gloriana.logic.prompt.assemblePrompt
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

private sealed interface ReplyEnd {
  data object ChatRemoved : ReplyEnd

  sealed interface Saved : ReplyEnd {
    val state: SwipeState
  }

  data object Completed : Saved {
    override val state = SwipeState.Complete
  }

  sealed interface Kept : Saved {
    fun shown(messageId: Uuid): LiveReply
  }

  class Failed(private val failure: ReplyFailure) : Kept {
    override val state = SwipeState.Failed

    override fun shown(messageId: Uuid) = LiveReply.Failed(messageId, failure)
  }

  class Unfinished(private val why: UnfinishedWhy) : Kept {
    override val state = SwipeState.Unfinished

    override fun shown(messageId: Uuid) = LiveReply.Unfinished(messageId, why)
  }
}

private sealed interface Saving {
  class Done(val settled: DraftSettled) : Saving

  class NotSaved(val failure: ReplyFailure) : Saving

  data object MessageGone : Saving
}

internal class ReplyRun(
  private val chats: ChatRepository,
  private val rows: ReplyRows,
  private val stream: ReplyStream,
  private val live: LiveReplies,
  private val log: ProblemLog,
) {
  suspend fun writeAndSettle(reply: RunningReply) {
    val end = firstEnd(reply)
    withContext(NonCancellable) { settle(reply, end) }
  }

  suspend fun settleCutOffByBackgroundTime(reply: RunningReply) =
    settle(reply, ReplyEnd.Unfinished(UnfinishedWhy.BackgroundTimeRanOut))

  private suspend fun firstEnd(reply: RunningReply): ReplyEnd = coroutineScope {
    val ends: List<Deferred<ReplyEnd>> = listOf(
      async { writtenOrFailed(reply) },
      async {
        reply.stopped.await()
        ReplyEnd.Unfinished(UnfinishedWhy.Stopped)
      },
      async {
        chats.chat(reply.chatId).first { it == null }
        ReplyEnd.ChatRemoved
      },
    )
    val end = select { ends.forEach { candidate -> candidate.onAwait { it } } }
    coroutineContext.cancelChildren()
    end
  }

  private suspend fun writtenOrFailed(reply: RunningReply): ReplyEnd =
    failingAs({ Result.failure(it) }) { Result.success(written(reply)) }
      .getOrElse { cause -> failedUnlessChatRemoved(reply, cause) }

  private suspend fun failedUnlessChatRemoved(reply: RunningReply, cause: Throwable): ReplyEnd {
    if (chats.chat(reply.chatId).first() == null) return ReplyEnd.ChatRemoved
    log.record("A reply could not be written", cause)
    return ReplyEnd.Failed(ReplyFailure.GlorianaProblem)
  }

  private suspend fun written(reply: RunningReply): ReplyEnd {
    val history = chats.messages(reply.chatId).first().filterNot { it.id == reply.draft.messageId }
    val sections = assemblePrompt(reply.material.prompt, history).promptSections
    return when (val end = stream.stream(sections, reply.material.connection, reply.draft, reply.arrived)) {
      is StreamEnd.Failed -> ReplyEnd.Failed(ReplyFailure.Host(end.error))
      is StreamEnd.Finished -> finishedBy(end.reason)
    }
  }

  private fun finishedBy(reason: FinishReason): ReplyEnd = when (reason) {
    FinishReason.Completed, FinishReason.MaxTokens -> ReplyEnd.Completed
    FinishReason.Refused -> ReplyEnd.Failed(ReplyFailure.Refused)
    FinishReason.Unfinished -> ReplyEnd.Unfinished(UnfinishedWhy.StreamEnded)
  }

  private suspend fun settle(reply: RunningReply, end: ReplyEnd) {
    if (!reply.endsNow()) return
    when (end) {
      ReplyEnd.ChatRemoved -> live.changeSettled { it - reply.chatId }
      is ReplyEnd.Saved -> {
        val saving = saved(reply, end)
        live.changeSettled { shown -> shownAfter(shown, reply, end, saving) }
      }
    }
    live.forget(reply)
  }

  private suspend fun saved(reply: RunningReply, end: ReplyEnd.Saved): Saving = failingAs({ cause ->
    log.record("A reply could not be saved", cause)
    Saving.NotSaved(if (cause.isStorageFull()) ReplyFailure.NotSavedStorageFull else ReplyFailure.NotSaved)
  }) { rows.settle(reply.draft, reply.arrived.value, end.state)?.let(Saving::Done) ?: Saving.MessageGone }

  private fun shownAfter(
    shown: Map<Uuid, LiveReply>,
    reply: RunningReply,
    end: ReplyEnd.Saved,
    saving: Saving,
  ): Map<Uuid, LiveReply> {
    val messageId = reply.draft.messageId
    return when (saving) {
      is Saving.NotSaved -> shown + (reply.chatId to LiveReply.Failed(messageId, saving.failure))
      Saving.MessageGone -> shown - reply.chatId
      is Saving.Done -> when (saving.settled) {
        DraftSettled.MessageRemoved, DraftSettled.PreviousSwipeKept -> shown
        DraftSettled.SwipeAdded -> when (end) {
          ReplyEnd.Completed -> shown - reply.chatId
          is ReplyEnd.Kept -> shown + (reply.chatId to end.shown(messageId))
        }
      }
    }
  }
}
