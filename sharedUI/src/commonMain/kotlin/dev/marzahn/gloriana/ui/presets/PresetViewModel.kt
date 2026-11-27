package dev.marzahn.gloriana.ui.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.EntryRole
import dev.marzahn.gloriana.logic.presets.Preset
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.PresetRow
import dev.marzahn.gloriana.logic.presets.StoredPreset
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.ui.files.ExportState
import dev.marzahn.gloriana.ui.files.SaveTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

private const val STOP_TIMEOUT_MILLIS = 5_000L

sealed interface PresetIntent {
  sealed interface Edit : PresetIntent {
    fun applied(content: PresetContent): PresetContent
  }

  data class SetOn(val entryId: Uuid, val on: Boolean) : Edit {
    override fun applied(content: PresetContent) = content.rows { withEntry(entryId) { it.copy(enabled = on) } }
  }

  data class SetRole(val entryId: Uuid, val role: EntryRole) : Edit {
    override fun applied(content: PresetContent) = content.rows { withEntry(entryId) { it.copy(role = role) } }
  }

  data class SetPlacement(val entryId: Uuid, val placement: EntryPlacement) : Edit {
    override fun applied(content: PresetContent) =
      content.rows { withEntry(entryId) { it.copy(placement = placement) } }
  }

  data class Move(val rowId: Uuid, val direction: MoveDirection) : Edit {
    override fun applied(content: PresetContent) = content.rows { moved(rowId, direction) }
  }

  data class RenameGroup(val groupId: Uuid, val name: String) : Edit {
    override fun applied(content: PresetContent) = content.rows { withGroupRenamed(groupId, name) }
  }

  data class DeleteGroup(val groupId: Uuid) : Edit {
    override fun applied(content: PresetContent) = content.rows { withoutGroup(groupId) }
  }

  data class SetWrapper(val prompt: WrapperPrompt, val text: String) : Edit {
    override fun applied(content: PresetContent) = content.copy(wrappers = content.wrappers.with(prompt, text))
  }

  data class AddGroup(val name: String, val above: Uuid?) : PresetIntent

  data object Save : PresetIntent

  sealed interface DeleteIntent : PresetIntent

  data object AskToDelete : DeleteIntent

  data object CancelDelete : DeleteIntent

  data object ConfirmDelete : DeleteIntent

  sealed interface Dismissal : PresetIntent

  data object DismissNote : Dismissal

  data object DismissImportNotes : Dismissal

  data object DismissExportNote : Dismissal

  data class Export(val target: SaveTarget) : PresetIntent

  data object Retry : PresetIntent
}

private fun PresetContent.rows(change: List<PresetRow>.() -> List<PresetRow>) = copy(rows = rows.change())

sealed interface PresetPageState {
  data object Loading : PresetPageState

  data object CouldNotLoad : PresetPageState

  data object Gone : PresetPageState

  data class Shown(
    val stored: Preset,
    val session: PresetSession,
    val export: ExportState?,
  ) : PresetPageState {
    val content: PresetContent get() = session.draft ?: stored.content

    val hasChanges: Boolean get() = session.draft != null

    val name: String get() = stored.name

    val canSave: Boolean get() = hasChanges && session.activity == PresetActivity.Idle

    fun entry(id: Uuid): PresetEntry? = content.entries.firstOrNull { it.id == id }
  }
}

private data class PageInputs(val id: Uuid, val attempt: Int)

class PresetViewModel(
  private val repository: PresetRepository,
  private val sessions: PresetSessions,
  private val commands: PresetCommands,
  private val exports: PresetExports,
) : ViewModel() {
  private val inputs = MutableStateFlow<PageInputs?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  val state: StateFlow<PresetPageState> = inputs.filterNotNull()
    .flatMapLatest { (id) -> pageState(id) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PresetPageState.Loading)

  fun show(id: Uuid) {
    if (inputs.value?.id != id) inputs.value = PageInputs(id, attempt = 0)
  }

  fun on(intent: PresetIntent) {
    val inputs = inputs.value ?: return
    when (intent) {
      is PresetIntent.Dismissal -> dismiss(inputs.id, intent)
      PresetIntent.Retry -> this.inputs.value = inputs.copy(attempt = inputs.attempt + 1)
      else -> (state.value as? PresetPageState.Shown)?.let { onPage(it, intent) }
    }
  }

  private fun onPage(page: PresetPageState.Shown, intent: PresetIntent) {
    when (intent) {
      is PresetIntent.Edit -> sessions.edit(page.stored.id, page.stored.content, intent::applied)
      is PresetIntent.AddGroup -> addGroup(page, intent)
      PresetIntent.Save -> commands.save(page.stored)
      is PresetIntent.DeleteIntent -> deleting(page, intent)
      is PresetIntent.Export -> if (!page.hasChanges) exports.start(page.stored.id, intent.target)
      is PresetIntent.Dismissal, PresetIntent.Retry -> Unit
    }
  }

  private fun addGroup(page: PresetPageState.Shown, intent: PresetIntent.AddGroup) {
    val group = repository.newGroup(intent.name)
    sessions.edit(page.stored.id, page.stored.content) { it.rows { withGroupAdded(group, intent.above) } }
  }

  private fun deleting(page: PresetPageState.Shown, intent: PresetIntent.DeleteIntent) = when (intent) {
    PresetIntent.AskToDelete -> commands.askToDelete(page.stored)
    PresetIntent.CancelDelete -> commands.cancelDelete(page.stored)
    PresetIntent.ConfirmDelete -> commands.confirmDelete(page.stored)
  }

  private fun dismiss(id: Uuid, intent: PresetIntent.Dismissal) = when (intent) {
    PresetIntent.DismissNote -> sessions.update(id) { it.copy(note = null) }
    PresetIntent.DismissImportNotes -> sessions.importNotes(id, sessions.current(id).importNotes?.withoutInfo())
    PresetIntent.DismissExportNote -> exports.runs.dismiss(id)
  }

  private fun pageState(id: Uuid): Flow<PresetPageState> = combine(
    repository.preset(id).map { Result.success(it) }.catch { emit(Result.failure(it)) },
    sessions.session(id),
    exports.runs.state(id),
  ) { stored, session, export ->
    when (val read = stored.getOrElse { return@combine PresetPageState.CouldNotLoad }) {
      null -> PresetPageState.Gone
      StoredPreset.Unreadable -> PresetPageState.CouldNotLoad
      is StoredPreset.Readable -> PresetPageState.Shown(read.preset, session, export)
    }
  }
}
