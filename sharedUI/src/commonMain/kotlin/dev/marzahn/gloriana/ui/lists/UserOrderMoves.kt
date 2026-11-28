package dev.marzahn.gloriana.ui.lists

import dev.marzahn.gloriana.logic.storage.MoveResult
import dev.marzahn.gloriana.logic.storage.Place
import dev.marzahn.gloriana.logic.storage.Reordering
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

data class NotMoved(val name: String, val storageFull: Boolean)

data class SavedMove(val id: Uuid, val name: String, val serial: Int)

private class WaitingMove(val id: Uuid, val name: String, val to: Place)

private class UnsavedOrder(
  val order: List<Uuid>,
  val unsavedMoves: Int,
  val storedWhenAllSaved: List<Uuid>? = null,
) {
  fun withOneMore(order: List<Uuid>) = UnsavedOrder(order, unsavedMoves + 1)

  fun withOneSaved(stored: List<Uuid>?) =
    UnsavedOrder(order, unsavedMoves - 1, stored.takeIf { unsavedMoves == 1 })

  fun endsAt(stored: List<Uuid>): Boolean = unsavedMoves == 0 && stored != storedWhenAllSaved
}

class UserOrderMoves<T>(
  scope: CoroutineScope,
  private val reordering: Reordering,
  private val stored: Flow<List<T>>,
  private val id: (T) -> Uuid,
) {
  private val unsaved = MutableStateFlow<UnsavedOrder?>(null)
  private val waiting = Channel<WaitingMove>(Channel.UNLIMITED)
  private val storedIds: StateFlow<List<Uuid>?> =
    stored.map { rows -> rows.map(id) }.catch { }.stateIn(scope, SharingStarted.Eagerly, null)
  private val notMovedState = MutableStateFlow<NotMoved?>(null)
  private val savedState = MutableStateFlow<SavedMove?>(null)

  val notMoved: StateFlow<NotMoved?> = notMovedState.asStateFlow()

  val saved: StateFlow<SavedMove?> = savedState.asStateFlow()

  val arranged: Flow<List<T>> = combine(stored, unsaved) { rows, wanted ->
    wanted?.takeUnless { it.endsAt(rows.map(id)) }?.let { rows.inOrder(it.order, id) } ?: rows
  }

  init {
    scope.launch { for (move in waiting) save(move) }
    scope.launch {
      storedIds.filterNotNull().collect { ids -> unsaved.update { it?.takeUnless { wanted -> wanted.endsAt(ids) } } }
    }
  }

  fun move(order: List<Uuid>, id: Uuid, name: String) {
    notMovedState.value = null
    unsaved.update { it?.withOneMore(order) ?: UnsavedOrder(order, unsavedMoves = 1) }
    val next = order.getOrNull(order.indexOf(id) + 1)
    waiting.trySend(WaitingMove(id, name, next?.let(Place::Before) ?: Place.Last))
  }

  fun dismiss() {
    notMovedState.value = null
  }

  private suspend fun save(move: WaitingMove) {
    when (val result = reordering.move(move.id, move.to)) {
      MoveResult.Moved -> saved(move)
      MoveResult.Gone, MoveResult.Failed, MoveResult.StorageFull -> notSaved(move, result)
    }
  }

  private fun saved(move: WaitingMove) {
    savedState.value = SavedMove(move.id, move.name, (savedState.value?.serial ?: 0) + 1)
    unsaved.update { it?.withOneSaved(storedIds.value) }
  }

  private fun notSaved(move: WaitingMove, result: MoveResult) {
    do {
      val dropped = waiting.tryReceive().isSuccess
    } while (dropped)
    unsaved.value = null
    notMovedState.value = NotMoved(move.name, storageFull = result == MoveResult.StorageFull)
  }
}

private fun <T> List<T>.inOrder(order: List<Uuid>, id: (T) -> Uuid): List<T> {
  val byId = associateBy(id)
  val placed = order.mapNotNull(byId::get)
  val placedIds = placed.map(id).toSet()
  return placed + filterNot { id(it) in placedIds }
}
