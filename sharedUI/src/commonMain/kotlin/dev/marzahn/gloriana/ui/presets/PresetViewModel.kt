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
import dev.marzahn.gloriana.ui.state.DefaultMarked
import dev.marzahn.gloriana.ui.state.DraftEdit
import dev.marzahn.gloriana.ui.state.StoredReads
import dev.marzahn.gloriana.ui.state.StoredRow
import dev.marzahn.gloriana.ui.state.StoredValue
import dev.marzahn.gloriana.ui.state.UnsavedCopy
import dev.marzahn.gloriana.ui.state.WhileCollected
import dev.marzahn.gloriana.ui.state.markedDefaultWhen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

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

  data object MakeDefault : OnPage

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
    val isDefault: Boolean,
  ) : PresetPageState {
    val unsaved: UnsavedCopy<PresetContent> = session.unsaved ?: UnsavedCopy.of(stored.content)

    val content: PresetContent get() = unsaved.draft

    val hasChanges: Boolean get() = unsaved.hasChanges

    val name: String get() = stored.name

    val canSave: Boolean get() = session.canSave

    fun entry(id: Uuid): PresetEntry? = content.entries.firstOrNull { it.id == id }
  }
}

class PresetViewModel(
  private val repository: PresetRepository,
  private val sessions: PresetSessions,
  private val commands: PresetCommands,
  private val exports: PresetExports,
) : ViewModel() {
  private val id = MutableStateFlow<Uuid?>(null)
  private val reads = StoredReads.rows<DefaultMarked<StoredPreset>>()

  @OptIn(ExperimentalCoroutinesApi::class)
  val state: StateFlow<PresetPageState> = id.filterNotNull()
    .flatMapLatest(::pageState)
    .stateIn(viewModelScope, WhileCollected, PresetPageState.Loading)

  fun show(id: Uuid) {
    this.id.value = id
  }

  fun contentNow(): PresetContent? {
    val page = state.value as? PresetPageState.Shown ?: return null
    return sessions.current(page.stored.id).unsaved?.draft ?: page.stored.content
  }

  fun on(intent: PresetIntent) {
    val id = id.value ?: return
    when (intent) {
      is PresetIntent.Dismissal -> dismiss(id, intent)
      PresetIntent.Retry -> reads.retry()
      is PresetIntent.OnPage -> (state.value as? PresetPageState.Shown)?.let { onPage(it, intent) }
    }
  }

  private fun follow(id: Uuid): Flow<StoredRow<DefaultMarked<StoredPreset>>> =
    reads.follow(id) { repository.preset(id).markedDefaultWhen(id, repository.defaultPreset()) }
      .onEach { row -> row.readableContent()?.let { sessions.storedNow(id, it) } }

  private fun onPage(page: PresetPageState.Shown, intent: PresetIntent.OnPage) {
    val id = page.stored.id
    val latest = reads.latest(id)?.readableContent() ?: page.stored.content
    when (intent) {
      is PresetIntent.Edit -> sessions.edit(id, latest, intent)
      is PresetIntent.AddGroup -> sessions.edit(id, latest, GroupAdded(repository.newGroup(intent.name), intent.above))
      PresetIntent.Save -> commands.save(page.stored)
      PresetIntent.MakeDefault -> if (!page.isDefault) commands.onStored.makeDefault(id, page.stored.name)
      is PresetIntent.DeleteIntent -> deleting(page, intent)
      is PresetIntent.Export -> if (!page.hasChanges) exports.start(id, intent.target)
    }
  }

  private fun deleting(page: PresetPageState.Shown, intent: PresetIntent.DeleteIntent) {
    when (intent) {
      PresetIntent.AskToDelete -> if (!page.isDefault) commands.onStored.askToDelete(page.stored.id)
      PresetIntent.CancelDelete -> commands.onStored.cancelDelete(page.stored.id)
      PresetIntent.ConfirmDelete -> commands.onStored.confirmDelete(page.stored.id, page.stored.name)
    }
  }

  private fun dismiss(id: Uuid, intent: PresetIntent.Dismissal) = when (intent) {
    PresetIntent.DismissImportNotes -> sessions.importNotes(id, sessions.current(id).importNotes?.withoutInfo())
    PresetIntent.DismissExportNote -> exports.runs.dismiss(id)
  }

  private fun pageState(id: Uuid): Flow<PresetPageState> =
    combine(
      follow(id),
      sessions.session(id),
      exports.runs.state(id),
    ) { row, session, export ->
      when (row) {
        StoredValue.Loading -> PresetPageState.Loading
        StoredValue.CouldNotLoad -> PresetPageState.CouldNotLoad
        StoredRow.Gone -> PresetPageState.Gone
        is StoredValue.Found -> when (val stored = row.value.row) {
          StoredPreset.Unreadable -> PresetPageState.CouldNotLoad
          is StoredPreset.Readable -> PresetPageState.Shown(stored.preset, session, export, row.value.isDefault)
        }
      }
    }
}

private fun StoredRow<DefaultMarked<StoredPreset>>.readableContent(): PresetContent? =
  ((this as? StoredValue.Found)?.value?.row as? StoredPreset.Readable)?.preset?.content
