package dev.marzahn.gloriana.ui.files

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.files.ExportProblem
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.messages.MessageAction
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
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

sealed interface ExportOutcome {
  data object Saved : ExportOutcome

  data class NotSaved(val problem: ExportProblem) : ExportOutcome

  data object Gone : ExportOutcome
}

class ExportRuns(
  private val scope: ApplicationScope,
  private val messages: AppMessages,
  private val openPage: (ShellNavigator, Uuid) -> Unit,
) {
  private val states = MutableStateFlow<Map<Uuid, ExportState>>(emptyMap())
  private val pagesShown = MutableStateFlow<Map<Uuid, Int>>(emptyMap())

  fun state(id: Uuid): Flow<ExportState?> = states.map { it[id] }.distinctUntilChanged()

  fun pageShown(id: Uuid) = pagesShown.update { it + (id to (it[id] ?: 0) + 1) }

  fun pageHidden(id: Uuid) = pagesShown.update { shown ->
    val left = (shown[id] ?: 0) - 1
    if (left > 0) shown + (id to left) else shown - id
  }

  fun dismiss(id: Uuid) = states.update { it - id }

  fun start(id: Uuid, target: SaveTarget, write: suspend () -> ExportOutcome) {
    if (states.value[id] is ExportState.Running) return
    states.update { it + (id to ExportState.Running(target.fileName)) }
    scope.launch {
      when (val outcome = write()) {
        ExportOutcome.Saved -> {
          states.update { it - id }
          messages.show(AppMessage(getString(Res.string.export_saved, target.fileName)))
        }
        is ExportOutcome.NotSaved -> notSaved(id, ExportState.NotSaved(target.fileName, outcome.problem))
        ExportOutcome.Gone -> states.update { it - id }
      }
    }
  }

  private suspend fun notSaved(id: Uuid, state: ExportState.NotSaved) {
    states.update { it + (id to state) }
    if (id in pagesShown.value) return
    val text = if (state.problem.incompleteFileLeft) {
      Res.string.snack_export_incomplete
    } else {
      Res.string.snack_export_failed
    }
    val open = MessageAction(getString(Res.string.open)) { openPage(it, id) }
    messages.show(AppMessage(getString(text, state.fileName), open))
  }
}
