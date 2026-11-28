package dev.marzahn.gloriana.logic.presets

import kotlin.uuid.Uuid

enum class EntryRole {
  System,
  User,
  Assistant,
}

sealed interface EntryPlacement {
  val hasNegativeDepth: Boolean

  data object Relative : EntryPlacement {
    override val hasNegativeDepth: Boolean get() = false
  }

  data class InChat(val depth: Int) : EntryPlacement {
    override val hasNegativeDepth: Boolean get() = depth < 0
  }
}

enum class FilledWith(internal val identifiers: Set<String>) {
  ChatMessages(setOf("chatHistory")),
  LorebookEntries(setOf("worldInfoBefore", "worldInfoAfter")),
  PersonaDescription(setOf("personaDescription")),
  CharacterDescription(setOf("charDescription")),
  CharacterPersonality(setOf("charPersonality")),
  Scenario(setOf("scenario")),
  ExampleMessages(setOf("dialogueExamples")),
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
) : PresetRow {
  val filledWith: FilledWith?
    get() = FilledWith.entries.firstOrNull { identifier in it.identifiers }?.takeIf { filledByApp }
}

data class PresetGroup(override val id: Uuid, val name: String) : PresetRow

data class PresetContent(val rows: List<PresetRow>, val wrappers: WrapperPrompts) {
  val entries: List<PresetEntry> get() = rows.filterIsInstance<PresetEntry>()
}

data class Preset(val id: Uuid, val name: String, val content: PresetContent)

sealed interface StoredPreset {
  data class Readable(val preset: Preset) : StoredPreset

  data object Unreadable : StoredPreset
}

data class PresetSummary(val id: Uuid, val name: String, val entries: Int, val entriesOn: Int)
