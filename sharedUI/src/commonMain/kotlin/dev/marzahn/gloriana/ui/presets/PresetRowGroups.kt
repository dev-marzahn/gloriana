package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetRow
import kotlin.uuid.Uuid

fun List<PresetRow>.groupOf(entryId: Uuid): PresetGroup? =
  take(indexOfRow(entryId).coerceAtLeast(0)).lastOrNull { it is PresetGroup } as? PresetGroup

fun List<PresetRow>.entriesUnder(groupId: Uuid): List<PresetEntry> {
  val start = indexOfRow(groupId)
  return if (start < 0) emptyList() else subList(start + 1, blockEnd(start)).filterIsInstance<PresetEntry>()
}

fun List<PresetRow>.withGroupAdded(group: PresetGroup, above: Uuid?): List<PresetRow> {
  val index = above?.let(::indexOfRow)?.takeIf { it >= 0 } ?: size
  return take(index) + group + drop(index)
}

fun List<PresetRow>.withGroupRenamed(id: Uuid, name: String): List<PresetRow> =
  map { if (it is PresetGroup && it.id == id) it.copy(name = name) else it }

fun List<PresetRow>.withoutGroup(id: Uuid): List<PresetRow> = filterNot { it is PresetGroup && it.id == id }

fun List<PresetRow>.withEntry(id: Uuid, change: (PresetEntry) -> PresetEntry): List<PresetRow> =
  map { if (it is PresetEntry && it.id == id) change(it) else it }

class RowPlace(val position: Int, val of: Int, val group: PresetGroup?)

fun List<PresetRow>.placeOf(id: Uuid): RowPlace? = when (val row = firstOrNull { it.id == id }) {
  null -> null
  is PresetEntry -> {
    val entries = filterIsInstance<PresetEntry>()
    RowPlace(entries.indexOf(row) + 1, entries.size, groupOf(id))
  }
  is PresetGroup -> {
    val groups = filterIsInstance<PresetGroup>()
    RowPlace(groups.indexOf(row) + 1, groups.size, null)
  }
}
