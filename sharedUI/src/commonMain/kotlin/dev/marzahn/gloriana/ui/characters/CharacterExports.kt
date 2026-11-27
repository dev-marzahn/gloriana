package dev.marzahn.gloriana.ui.characters

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.characters.CardFormat
import dev.marzahn.gloriana.logic.characters.CharacterRepository
import dev.marzahn.gloriana.logic.characters.ExportResult
import dev.marzahn.gloriana.logic.files.ExportDestination
import dev.marzahn.gloriana.logic.files.ExportProblem
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.messages.MessageAction
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.export_saved
import dev.marzahn.gloriana.ui.resources.open
import dev.marzahn.gloriana.ui.resources.snack_export_failed
import dev.marzahn.gloriana.ui.resources.snack_export_incomplete
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.uuid.Uuid

sealed interface ExportState {
  val fileName: String

  data class Running(override val fileName: String) : ExportState

  data class NotSaved(override val fileName: String, val problem: ExportProblem) : ExportState
}

class SaveTarget(val destination: ExportDestination, val fileName: String)

class CharacterExports(
  private val repository: CharacterRepository,
  private val scope: ApplicationScope,
  private val messages: AppMessages,
) {
  private val states = MutableStateFlow<Map<Uuid, ExportState>>(emptyMap())
  private val pagesShown = MutableStateFlow<Map<Uuid, Int>>(emptyMap())

  fun state(id: Uuid): Flow<ExportState?> = states.map { it[id] }.distinctUntilChanged()

  fun pageShown(id: Uuid): () -> Unit {
    pagesShown.update { it + (id to (it[id] ?: 0) + 1) }
    return { pagesShown.update { it + (id to (it[id] ?: 1) - 1) } }
  }

  fun dismiss(id: Uuid) {
    states.update { it - id }
  }

  fun start(id: Uuid, format: CardFormat, target: SaveTarget) {
    if (states.value[id] is ExportState.Running) return
    states.update { it + (id to ExportState.Running(target.fileName)) }
    scope.launch {
      when (val result = repository.export(id, format, target.destination)) {
        is ExportResult.Saved -> saved(id, target.fileName)
        is ExportResult.NotSaved -> notSaved(id, ExportState.NotSaved(target.fileName, result.problem))
        ExportResult.CharacterGone -> states.update { it - id }
      }
    }
  }

  private suspend fun saved(id: Uuid, fileName: String) {
    states.update { it - id }
    messages.show(AppMessage(getString(Res.string.export_saved, fileName)))
  }

  private suspend fun notSaved(id: Uuid, state: ExportState.NotSaved) {
    states.update { it + (id to state) }
    if ((pagesShown.value[id] ?: 0) > 0) return
    val text = if (state.problem.incompleteFileLeft) {
      Res.string.snack_export_incomplete
    } else {
      Res.string.snack_export_failed
    }
    val open = MessageAction(getString(Res.string.open)) { it.openCharacter(id) }
    messages.show(AppMessage(getString(text, state.fileName), open))
  }
}

private val UnsafeFileNameCharacters = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

fun proposedFileName(name: String, format: CardFormat): String {
  val base = UnsafeFileNameCharacters.replace(name, "_").trim().ifEmpty { "_" }
  return base + if (format == CardFormat.Png) ".png" else ".json"
}
