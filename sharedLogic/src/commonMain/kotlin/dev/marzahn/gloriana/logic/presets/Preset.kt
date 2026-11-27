package dev.marzahn.gloriana.logic.presets

import kotlin.uuid.Uuid

enum class EntryRole {
  System,
  User,
  Assistant,
}

sealed interface EntryPlacement {
  data object Relative : EntryPlacement

  data class InChat(val depth: Int) : EntryPlacement
}

sealed interface PresetRow {
  val id: Uuid
}

data class PresetEntry(
  override val id: Uuid,
  val identifier: String,
  val name: String,
  val text: String,
  val filledByApp: Boolean,
  val enabled: Boolean,
  val role: EntryRole,
  val placement: EntryPlacement,
) : PresetRow

data class PresetGroup(override val id: Uuid, val name: String) : PresetRow

data class PresetContent(val rows: List<PresetRow>, val wrappers: WrapperPrompts) {
  val entries: List<PresetEntry> get() = rows.filterIsInstance<PresetEntry>()
}

data class Preset(val id: Uuid, val name: String, val content: PresetContent)

sealed interface StoredPreset {
  data class Readable(val preset: Preset) : StoredPreset

  data object Unreadable : StoredPreset
}

data class PresetSummary(val id: Uuid, val name: String)
