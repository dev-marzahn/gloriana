package dev.marzahn.gloriana.logic.generation

import dev.marzahn.gloriana.logic.chat.ReplyDraft
import dev.marzahn.gloriana.logic.chat.ReplyText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

internal class RunningReply(val chatId: Uuid, val draft: ReplyDraft, val material: ReplyMaterial) {
  val arrived = MutableStateFlow(ReplyText.Empty)
  val stopped = CompletableDeferred<Unit>()
  private val ended = CompletableDeferred<Unit>()

  val writing: Flow<Pair<Uuid, LiveReply>> = arrived.map { chatId to LiveReply.Writing(draft.messageId, it) }

  fun endsNow(): Boolean = ended.complete(Unit)
}

internal class LiveReplies {
  private val busy = MutableStateFlow<Set<Uuid>>(emptySet())
  private val running = MutableStateFlow<Map<Uuid, RunningReply>>(emptyMap())
  private val settled = MutableStateFlow<Map<Uuid, LiveReply>>(emptyMap())

  @OptIn(ExperimentalCoroutinesApi::class)
  val replies: Flow<Map<Uuid, LiveReply>> =
    combine(running.flatMapLatest(::writing), settled) { writing, settled -> settled + writing }

  fun claim(chatId: Uuid): Boolean = chatId !in busy.getAndUpdate { it + chatId }

  fun release(chatId: Uuid) = busy.update { it - chatId }

  fun track(reply: RunningReply) = running.update { it + (reply.chatId to reply) }

  fun stop(chatId: Uuid) {
    running.value[chatId]?.stopped?.complete(Unit)
  }

  fun changeSettled(change: (Map<Uuid, LiveReply>) -> Map<Uuid, LiveReply>) = settled.update(change)

  fun forget(reply: RunningReply) {
    running.update { it - reply.chatId }
    release(reply.chatId)
  }

  private fun writing(running: Map<Uuid, RunningReply>): Flow<Map<Uuid, LiveReply>> =
    if (running.isEmpty()) {
      flowOf(emptyMap())
    } else {
      combine(running.values.map { it.writing }) { pairs -> pairs.toMap() }
    }
}
