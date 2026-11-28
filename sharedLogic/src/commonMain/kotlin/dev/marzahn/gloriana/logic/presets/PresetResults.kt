package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.files.ExportProblem
import kotlin.uuid.Uuid

sealed interface PresetProblem {
  data object EmptyName : PresetProblem

  data class NegativeDepth(val entryId: Uuid) : PresetProblem

  data class RepeatedRow(val id: Uuid) : PresetProblem

  data class ForeignRow(val id: Uuid) : PresetProblem
}

sealed interface PresetSaveResult {
  data object Saved : PresetSaveResult

  data class Refused(val problems: Set<PresetProblem>) : PresetSaveResult

  data object Gone : PresetSaveResult

  data object StorageFull : PresetSaveResult

  data object Failed : PresetSaveResult
}

enum class PresetDeleteResult {
  Deleted,
  IsDefault,
  Gone,
  Failed,
}

sealed interface PresetExportResult {
  data object Exported : PresetExportResult

  data object Gone : PresetExportResult

  data object Unreadable : PresetExportResult

  data class Failed(val problem: ExportProblem) : PresetExportResult
}

sealed interface PresetImportResult {
  data class Imported(val id: Uuid, val samplers: PresetSamplers, val credentialsSkipped: Boolean) : PresetImportResult

  data class Failed(val reason: PresetImportFailure) : PresetImportResult
}

enum class PresetImportFailure {
  CantBeOpened,
  Damaged,
  NotAPreset,
  TooLarge,
  StorageFull,
  CouldNotSave,
}
