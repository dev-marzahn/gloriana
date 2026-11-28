package dev.marzahn.gloriana.logic.chat.storage

import dev.marzahn.gloriana.logic.app.ProblemLog
import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.chat.ChatWritePoint
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.writeTransaction

internal class LeftoverDrafts(
  private val database: GlorianaDatabase,
  private val dao: SwipeDraftDao,
  private val ids: IdFactory,
  private val clock: UpdatedAtClock,
  private val tombstones: Tombstones,
  private val faults: Faults,
  private val log: ProblemLog,
) {
  suspend fun keepAsUnfinished(): Boolean = dao.all().map { keptOrLogged(it) }.all { it }

  private suspend fun keptOrLogged(draft: SwipeDraftEntity): Boolean = failingAs({ cause ->
    log.record("A leftover draft could not be kept as unfinished", cause)
    false
  }) {
    database.writeTransaction { keep(draft) }
    true
  }

  private suspend fun keep(draft: SwipeDraftEntity) {
    val message = checkNotNull(dao.message(draft.messageId)) { "A draft outlived its message" }
    val now = clock.now()
    dao.delete(draft.id)
    tombstones.record(SwipeDraftsEntityType, RowKey.Row(draft.id))
    val last = dao.lastSwipeIndex(message.id)
    when {
      draft.text.isNotEmpty() || draft.reasoning.isNotEmpty() -> {
        val added = (last ?: -1) + 1
        dao.insert(SwipeEntity(ids.newId(), message.id, added, draft.text, draft.reasoning, SwipeState.Unfinished, now))
        faults.reached(ChatWritePoint.DraftSwipeWritten)
        dao.showSwipe(message.id, added, clock.updatedAt(message.updatedAt, now))
      }
      last == null -> {
        dao.deleteMessage(message.id)
        tombstones.record(MessagesEntityType, RowKey.Row(message.id))
      }
      !dao.swipeExists(message.id, message.shownSwipe) ->
        dao.showSwipe(message.id, last, clock.updatedAt(message.updatedAt, now))
    }
  }
}
