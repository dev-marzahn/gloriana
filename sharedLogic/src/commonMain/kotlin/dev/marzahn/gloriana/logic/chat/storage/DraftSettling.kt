package dev.marzahn.gloriana.logic.chat.storage

import dev.marzahn.gloriana.logic.chat.ChatWritePoint
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock

internal class DraftSettling(
  private val dao: SwipeDraftDao,
  private val ids: IdFactory,
  private val clock: UpdatedAtClock,
  private val tombstones: Tombstones,
  private val faults: Faults,
) {
  suspend fun settle(draft: SwipeDraftEntity, state: SwipeState): DraftSettled {
    val message = checkNotNull(dao.message(draft.messageId)) { "A draft outlived its message" }
    val now = clock.now()
    dao.delete(draft.id)
    tombstones.record(SwipeDraftsEntityType, RowKey.Row(draft.id))
    val last = dao.lastSwipeIndex(message.id)
    val keptAsSwipe = draft.text.isNotEmpty() || draft.reasoning.isNotEmpty() || state == SwipeState.Failed
    return when {
      keptAsSwipe -> {
        val added = (last ?: -1) + 1
        dao.insert(SwipeEntity(ids.newId(), message.id, added, draft.text, draft.reasoning, state, now))
        faults.reached(ChatWritePoint.DraftSwipeWritten)
        dao.showSwipe(message.id, added, clock.updatedAt(message.updatedAt, now))
        DraftSettled.SwipeAdded
      }
      last == null -> {
        dao.deleteMessage(message.id)
        tombstones.record(MessagesEntityType, RowKey.Row(message.id))
        DraftSettled.MessageRemoved
      }
      else -> {
        if (!dao.swipeExists(message.id, message.shownSwipe)) {
          dao.showSwipe(message.id, last, clock.updatedAt(message.updatedAt, now))
        }
        DraftSettled.PreviousSwipeKept
      }
    }
  }
}

internal enum class DraftSettled { SwipeAdded, MessageRemoved, PreviousSwipeKept }
