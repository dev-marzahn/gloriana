package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.files.ExportFailure
import dev.marzahn.gloriana.logic.files.ExportProblem
import dev.marzahn.gloriana.logic.presets.PresetExportResult
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.ui.files.ExportOutcome
import dev.marzahn.gloriana.ui.files.ExportRuns
import dev.marzahn.gloriana.ui.files.SaveTarget
import dev.marzahn.gloriana.ui.messages.AppMessages
import kotlin.uuid.Uuid

private val storedPresetUnreadable = ExportProblem(ExportFailure.StoredFileUnreadable, incompleteFileLeft = false)

class PresetExports(
  private val repository: PresetRepository,
  scope: ApplicationScope,
  messages: AppMessages,
) {
  val runs = ExportRuns(scope, messages) { navigator, id -> navigator.openPreset(id) }

  fun start(id: Uuid, target: SaveTarget) = runs.start(id, target) {
    when (val result = repository.export(id, target.destination)) {
      PresetExportResult.Exported -> ExportOutcome.Saved
      is PresetExportResult.Failed -> ExportOutcome.NotSaved(result.problem)
      PresetExportResult.Unreadable -> ExportOutcome.NotSaved(storedPresetUnreadable)
      PresetExportResult.Gone -> ExportOutcome.Gone
    }
  }
}
