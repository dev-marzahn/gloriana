package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetRow
import kotlin.uuid.Uuid

enum class MoveDirection(val step: Int) {
  Up(-1),
  Down(1),
}

internal fun List<PresetRow>.indexOfRow(id: Uuid): Int = indexOfFirst { it.id == id }

internal fun List<PresetRow>.blockEnd(headerIndex: Int): Int =
  (headerIndex + 1 until size).firstOrNull { this[it] is PresetGroup } ?: size

private fun List<PresetRow>.carriesEntries(index: Int): Boolean =
  this[index] is PresetGroup && blockEnd(index) > index + 1

private sealed interface Move {
  class Swap(val index: Int, val other: Int) : Move

  class Blocks(val first: IntRange, val second: IntRange) : Move
}

private fun List<PresetRow>.moveOf(id: Uuid, direction: MoveDirection): Move? {
  val index = indexOfRow(id)
  return when {
    index < 0 -> null
    carriesEntries(index) -> neighbourBlock(index, direction)?.let { neighbour ->
      val own = index until blockEnd(index)
      if (direction == MoveDirection.Up) Move.Blocks(neighbour, own) else Move.Blocks(own, neighbour)
    }
    (index + direction.step) in indices -> Move.Swap(index, index + direction.step)
    else -> null
  }
}

fun List<PresetRow>.canMove(id: Uuid, direction: MoveDirection): Boolean = moveOf(id, direction) != null

fun List<PresetRow>.moved(id: Uuid, direction: MoveDirection): List<PresetRow> = when (
  val move = moveOf(id, direction)
) {
  null -> this
  is Move.Swap -> toMutableList().apply {
    this[move.index] = this@moved[move.other]
    this[move.other] = this@moved[move.index]
  }
  is Move.Blocks -> take(move.first.first) + slice(move.second) + slice(move.first) + drop(move.second.last + 1)
}

private fun List<PresetRow>.neighbourBlock(headerIndex: Int, direction: MoveDirection): IntRange? = when (direction) {
  MoveDirection.Up -> (headerIndex - 1 downTo 0).firstOrNull { this[it] is PresetGroup }?.let { it until headerIndex }
  MoveDirection.Down -> blockEnd(headerIndex).takeIf { it < size }?.let { it until blockEnd(it) }
}
