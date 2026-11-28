package dev.marzahn.gloriana.logic.storage

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.debug.FaultPoint
import dev.marzahn.gloriana.logic.debug.Faults
import kotlin.uuid.Uuid

enum class MoveResult {
  Moved,
  Gone,
  Failed,
  StorageFull,
}

sealed interface Place {
  data class Before(val id: Uuid) : Place

  data object Last : Place
}

fun interface Reordering {
  suspend fun move(id: Uuid, to: Place): MoveResult
}

internal data class PlacedRow(val id: Uuid, val position: Long, val updatedAt: Long)

internal class OrderColumn(
  val placedRows: suspend () -> List<PlacedRow>,
  val place: suspend (id: Uuid, position: Long, updatedAt: Long) -> Unit,
  val rowsWritten: FaultPoint,
)

internal class UserOrder(
  private val database: GlorianaDatabase,
  private val clock: UpdatedAtClock,
  private val faults: Faults,
  private val column: OrderColumn,
) : Reordering {
  override suspend fun move(id: Uuid, to: Place): MoveResult =
    failingAs({ if (it.isStorageFull()) MoveResult.StorageFull else MoveResult.Failed }) {
      database.writeTransaction { moveInTransaction(id, to) }
    }

  private suspend fun moveInTransaction(id: Uuid, to: Place): MoveResult {
    val stored = column.placedRows()
    val order = stored.moved(id, to) ?: return MoveResult.Gone
    val now = clock.now()
    order.zip(stored.map { it.position })
      .filter { (row, slot) -> row.position != slot }
      .forEach { (row, slot) -> column.place(row.id, slot, clock.updatedAt(row.updatedAt, now)) }
    faults.reached(column.rowsWritten)
    return MoveResult.Moved
  }
}

private fun List<PlacedRow>.moved(id: Uuid, to: Place): List<PlacedRow>? {
  val moving = firstOrNull { it.id == id } ?: return null
  val others = filterNot { it.id == id }
  val index = when (to) {
    Place.Last -> others.size
    Place.Before(id) -> indexOf(moving)
    is Place.Before -> others.indexOfFirst { it.id == to.id }.takeIf { it >= 0 }
  }
  return index?.let { others.toMutableList().apply { add(it, moving) } }
}
