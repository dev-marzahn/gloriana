package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.presets.PresetExportResult
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.ui.files.ExportOutcome
import dev.marzahn.gloriana.ui.files.ExportRuns
import dev.marzahn.gloriana.ui.files.SaveTarget
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.navigation.LibraryKinds
import kotlin.uuid.Uuid

class PresetExports(
  private val repository: PresetRepository,
  scope: ApplicationScope,
  messages: AppMessages,
  kinds: LibraryKinds,
) {
  val runs = ExportRuns(scope, messages) { navigator, id -> navigator.openPreset(id, kinds) }

  fun start(id: Uuid, target: SaveTarget) = runs.start(id, target) {
    when (val result = repository.export(id, target.destination)) {
      PresetExportResult.Exported -> ExportOutcome.Saved
      is PresetExportResult.Failed -> ExportOutcome.NotSaved(result.problem)
      PresetExportResult.Gone, PresetExportResult.Unreadable -> ExportOutcome.Gone
    }
  }
}
