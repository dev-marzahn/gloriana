package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.presets.MoveDirection
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetRow
import dev.marzahn.gloriana.logic.presets.canMove
import dev.marzahn.gloriana.logic.presets.carriesEntries
import kotlin.uuid.Uuid

class PanelLayout(val rows: List<PresetRow>, val hidden: Set<Uuid>, val heights: Map<Uuid, Int>) {
  private val visible = rows.filterNot { it.id in hidden }

  val tops: Map<Uuid, Float> = visible.runningFold(0f) { top, row -> top + height(row.id) }
    .zip(visible) { top, row -> row.id to top }
    .toMap()

  fun height(id: Uuid): Int = if (id in hidden) 0 else heights[id] ?: 0

  private fun center(id: Uuid): Float? = tops[id]?.let { it + height(id) / 2f }

  private fun neighbour(id: Uuid, direction: MoveDirection): Uuid? {
    if (rows.none { it.id == id }) return null
    val candidates = if (rows.carriesEntries(id)) {
      visible.filterIsInstance<PresetGroup>()
    } else {
      visible
    }
    return candidates.getOrNull(candidates.indexOfFirst { it.id == id } + direction.step)?.id
  }

  fun step(id: Uuid, draggedTop: Float): MoveDirection? {
    val slotTop = tops[id] ?: return null
    val direction = if (draggedTop > slotTop) MoveDirection.Down else MoveDirection.Up
    val passed = neighbour(id, direction)?.let { other ->
      val margin = height(other) * SWAP_MARGIN_SHARE
      val middle = center(other) ?: return@let false
      when (direction) {
        MoveDirection.Down -> draggedTop + height(id) > middle + margin
        MoveDirection.Up -> draggedTop < middle - margin
      }
    } ?: false
    return direction.takeIf { passed && rows.canMove(id, direction) }
  }
}

private const val SWAP_MARGIN_SHARE = 0.1f
