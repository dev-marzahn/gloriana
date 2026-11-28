package dev.marzahn.gloriana.ui.characters

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.characters.CardFormat
import dev.marzahn.gloriana.logic.characters.CharacterRepository
import dev.marzahn.gloriana.logic.characters.ExportResult
import dev.marzahn.gloriana.ui.files.ExportOutcome
import dev.marzahn.gloriana.ui.files.ExportRuns
import dev.marzahn.gloriana.ui.files.SaveTarget
import dev.marzahn.gloriana.ui.files.SavedFileType
import dev.marzahn.gloriana.ui.messages.AppMessages
import kotlin.uuid.Uuid

val CardFormat.savedFileType: SavedFileType
  get() = when (this) {
    CardFormat.Png -> SavedFileType.Png
    CardFormat.Json -> SavedFileType.Json
  }

class CharacterExports(private val repository: CharacterRepository, scope: ApplicationScope, messages: AppMessages) {
  val runs = ExportRuns(scope, messages) { navigator, id -> navigator.openCharacter(id) }

  fun start(id: Uuid, format: CardFormat, target: SaveTarget) = runs.start(id, target) {
    when (val result = repository.export(id, format, target.destination)) {
      is ExportResult.Saved -> ExportOutcome.Saved
      is ExportResult.NotSaved -> ExportOutcome.NotSaved(result.problem)
      ExportResult.CharacterGone -> ExportOutcome.Gone
    }
  }
}
