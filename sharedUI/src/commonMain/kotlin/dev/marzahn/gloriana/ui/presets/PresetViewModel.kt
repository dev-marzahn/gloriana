package dev.marzahn.gloriana.ui.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.EntryRole
import dev.marzahn.gloriana.logic.presets.MoveDirection
import dev.marzahn.gloriana.logic.presets.Preset
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.PresetRow
import dev.marzahn.gloriana.logic.presets.StoredPreset
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.logic.presets.moved
import dev.marzahn.gloriana.logic.presets.withEntry
import dev.marzahn.gloriana.logic.presets.withGroupAdded
import dev.marzahn.gloriana.logic.presets.withGroupRenamed
import dev.marzahn.gloriana.logic.presets.withoutGroup
import dev.marzahn.gloriana.ui.files.ExportState
import dev.marzahn.gloriana.ui.files.SaveTarget
import dev.marzahn.gloriana.ui.state.DraftEdit
import dev.marzahn.gloriana.ui.state.UnsavedCopy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

private const val STOP_TIMEOUT_MILLIS = 5_000L

sealed interface PresetIntent {
  sealed interface OnPage : PresetIntent

  sealed interface Edit : OnPage, DraftEdit<PresetContent>

  data class SetOn(val entryId: Uuid, val on: Boolean) : Edit {
    override fun applied(to: PresetContent) = to.rows { withEntry(entryId) { it.copy(enabled = on) } }
  }

  data class SetRole(val entryId: Uuid, val role: EntryRole) : Edit {
    override fun applied(to: PresetContent) = to.rows { withEntry(entryId) { it.copy(role = role) } }
  }

  data class SetPlacement(val entryId: Uuid, val placement: EntryPlacement) : Edit {
    override fun applied(to: PresetContent) = to.rows { withEntry(entryId) { it.copy(placement = placement) } }

    override fun replaces(earlier: DraftEdit<PresetContent>) = earlier is SetPlacement && earlier.entryId == entryId
  }

  data class Move(val rowId: Uuid, val direction: MoveDirection) : Edit {
    override fun applied(to: PresetContent) = to.rows { moved(rowId, direction) }
  }

  data class RenameGroup(val groupId: Uuid, val name: String) : Edit {
    override fun applied(to: PresetContent) = to.rows { withGroupRenamed(groupId, name) }
  }

  data class DeleteGroup(val groupId: Uuid) : Edit {
    override fun applied(to: PresetContent) = to.rows { withoutGroup(groupId) }
  }

  data class SetWrapper(val prompt: WrapperPrompt, val text: String) : Edit {
    override fun applied(to: PresetContent) = to.copy(wrappers = to.wrappers.with(prompt, text))

    override fun replaces(earlier: DraftEdit<PresetContent>) = earlier is SetWrapper && earlier.prompt == prompt
  }

  data class AddGroup(val name: String, val above: Uuid?) : OnPage

  data object Save : OnPage

  sealed interface DeleteIntent : OnPage

  data object AskToDelete : DeleteIntent

  data object CancelDelete : DeleteIntent

  data object ConfirmDelete : DeleteIntent

  sealed interface Dismissal : PresetIntent

  data object DismissImportNotes : Dismissal

  data object DismissExportNote : Dismissal

  data class Export(val target: SaveTarget) : OnPage

  data object Retry : PresetIntent
}

private class GroupAdded(private val group: PresetGroup, private val above: Uuid?) : DraftEdit<PresetContent> {
  override fun applied(to: PresetContent) = to.rows { withGroupAdded(group, above) }
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
    val unsaved: UnsavedCopy<PresetContent> = session.unsavedOver(stored.content)

    val content: PresetContent get() = unsaved.draft

    val hasChanges: Boolean get() = unsaved.hasChanges

    val name: String get() = stored.name

    val canSave: Boolean get() = hasChanges && session.isIdle

    fun entry(id: Uuid): PresetEntry? = content.entries.firstOrNull { it.id == id }
  }
}

private sealed interface PresetRead {
  data object Failed : PresetRead

  data class Read(val stored: StoredPreset?) : PresetRead
}

class PresetViewModel(
  private val repository: PresetRepository,
  private val sessions: PresetSessions,
  private val commands: PresetCommands,
  private val exports: PresetExports,
) : ViewModel() {
  private val id = MutableStateFlow<Uuid?>(null)
  private val read = MutableStateFlow<PresetRead?>(null)
  private var following: Job? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  val state: StateFlow<PresetPageState> = id.filterNotNull()
    .flatMapLatest(::pageState)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PresetPageState.Loading)

  fun show(id: Uuid) {
    if (this.id.value == id) return
    this.id.value = id
    follow(id)
  }

  fun contentNow(): PresetContent? {
    val page = state.value as? PresetPageState.Shown ?: return null
    return sessions.current(page.stored.id).unsavedOver(page.stored.content).draft
  }

  fun on(intent: PresetIntent) {
    val id = id.value ?: return
    when (intent) {
      is PresetIntent.Dismissal -> dismiss(id, intent)
      PresetIntent.Retry -> follow(id)
      is PresetIntent.OnPage -> (state.value as? PresetPageState.Shown)?.let { onPage(it, intent) }
    }
  }

  private fun follow(id: Uuid) {
    following?.cancel()
    read.value = null
    following = viewModelScope.launch {
      repository.preset(id)
        .map<StoredPreset?, PresetRead> { PresetRead.Read(it) }
        .catch { emit(PresetRead.Failed) }
        .collect { result ->
          val readable = (result as? PresetRead.Read)?.stored as? StoredPreset.Readable
          readable?.let { sessions.storedNow(id, it.preset.content) }
          read.value = result
        }
    }
  }

  private fun latestStored(): PresetContent? =
    ((read.value as? PresetRead.Read)?.stored as? StoredPreset.Readable)?.preset?.content

  private fun onPage(page: PresetPageState.Shown, intent: PresetIntent.OnPage) {
    val id = page.stored.id
    val latest = latestStored() ?: page.stored.content
    when (intent) {
      is PresetIntent.Edit -> sessions.edit(id, latest, intent)
      is PresetIntent.AddGroup -> sessions.edit(id, latest, GroupAdded(repository.newGroup(intent.name), intent.above))
      PresetIntent.Save -> commands.save(page.stored)
      is PresetIntent.DeleteIntent -> deleting(page, intent)
      is PresetIntent.Export -> if (!page.hasChanges) exports.start(id, intent.target)
    }
  }

  private fun deleting(page: PresetPageState.Shown, intent: PresetIntent.DeleteIntent) = when (intent) {
    PresetIntent.AskToDelete -> commands.askToDelete(page.stored)
    PresetIntent.CancelDelete -> commands.cancelDelete(page.stored)
    PresetIntent.ConfirmDelete -> commands.confirmDelete(page.stored)
  }

  private fun dismiss(id: Uuid, intent: PresetIntent.Dismissal) = when (intent) {
    PresetIntent.DismissImportNotes -> sessions.importNotes(id, sessions.current(id).importNotes?.withoutInfo())
    PresetIntent.DismissExportNote -> exports.runs.dismiss(id)
  }

  private fun pageState(id: Uuid): Flow<PresetPageState> =
    combine(read.filterNotNull(), sessions.session(id), exports.runs.state(id)) { read, session, export ->
      when (val stored = (read as? PresetRead.Read ?: return@combine PresetPageState.CouldNotLoad).stored) {
        null -> PresetPageState.Gone
        StoredPreset.Unreadable -> PresetPageState.CouldNotLoad
        is StoredPreset.Readable -> PresetPageState.Shown(stored.preset, session, export)
      }
    }
}
