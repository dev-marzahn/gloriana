package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.SamplersUpdateResult
import dev.marzahn.gloriana.logic.files.PickedFile
import dev.marzahn.gloriana.logic.presets.DifferenceResult
import dev.marzahn.gloriana.logic.presets.PresetImport
import dev.marzahn.gloriana.logic.presets.PresetImportFailure
import dev.marzahn.gloriana.logic.presets.PresetImportResult
import dev.marzahn.gloriana.logic.presets.PresetSamplers
import dev.marzahn.gloriana.logic.presets.SamplerDifference
import dev.marzahn.gloriana.logic.presets.SamplerOffer
import dev.marzahn.gloriana.logic.presets.presetName
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.messages.MessageAction
import dev.marzahn.gloriana.ui.navigation.LibraryKind
import dev.marzahn.gloriana.ui.navigation.LibraryKinds
import dev.marzahn.gloriana.ui.navigation.showLibraryKind
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.open
import dev.marzahn.gloriana.ui.resources.preset_imported
import dev.marzahn.gloriana.ui.resources.preset_not_imported
import dev.marzahn.gloriana.ui.resources.samplers_copied
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.uuid.Uuid

data class OfferChoice(val id: Uuid, val name: String, val format: ApiFormat, val isDefault: Boolean)

data class SamplerOfferState(
  val presetId: Uuid,
  val presetName: String,
  val fileName: String,
  val samplers: PresetSamplers,
  val credentialsSkipped: Boolean,
  val choices: List<OfferChoice>,
  val picked: OfferChoice,
  val difference: SamplerDifference?,
  val copying: Boolean = false,
)

data class ImportFailureNote(val fileName: String, val reason: PresetImportFailure)

data class PresetImportState(
  val running: String? = null,
  val failures: List<ImportFailureNote> = emptyList(),
  val offer: SamplerOfferState? = null,
  val waiting: List<PickedFile> = emptyList(),
) {
  val busy: Boolean get() = running != null || offer != null
}

private sealed interface OfferChoices {
  data class Read(val choices: List<OfferChoice>) : OfferChoices

  data object Unreadable : OfferChoices
}

class PresetImports(
  private val import: PresetImport,
  private val connections: ConnectionRepository,
  private val offers: SamplerOffer,
  private val sessions: PresetSessions,
  private val scope: ApplicationScope,
  private val messages: AppMessages,
  private val kinds: LibraryKinds,
) {
  private val mutableState = MutableStateFlow(PresetImportState())
  private val pages = Channel<Uuid>(Channel.BUFFERED)
  private val listsShown = MutableStateFlow(0)

  val state: StateFlow<PresetImportState> = mutableState.asStateFlow()

  suspend fun openPagesWhileShown(open: (Uuid) -> Unit): Nothing {
    listsShown.update { it + 1 }
    try {
      pages.receiveAsFlow().collect(open)
      error("The pages channel never closes")
    } finally {
      listsShown.update { it - 1 }
    }
  }

  fun start(file: PickedFile) {
    mutableState.update { state ->
      state.copy(waiting = state.waiting + file, failures = if (state.busy) state.failures else emptyList())
    }
    next()
  }

  private fun next() {
    val file = mutableState.claimNext() ?: return
    scope.launch {
      when (val result = import.import(file)) {
        is PresetImportResult.Failed -> {
          val note = ImportFailureNote(file.name, result.reason)
          mutableState.update { it.copy(running = null, failures = it.failures + note) }
          next()
          if (listsShown.value == 0) {
            val open = MessageAction(getString(Res.string.open)) { it.showLibraryKind(LibraryKind.Presets, kinds) }
            messages.show(AppMessage(getString(Res.string.preset_not_imported, note.fileName), open))
          }
        }
        is PresetImportResult.Imported -> imported(file.name, result)
      }
    }
  }

  fun dismissFailure(note: ImportFailureNote) = mutableState.update { it.copy(failures = it.failures - note) }

  fun pick(choice: OfferChoice) {
    val offer = mutableState.value.offer?.takeUnless { it.copying } ?: return
    mutableState.updateOffer { it.copy(picked = choice, difference = null) }
    scope.launch { findDifference(offer.copy(picked = choice)) }
  }

  fun decline() {
    val offer = mutableState.value.offer?.takeUnless { it.copying } ?: return
    mutableState.update { it.copy(offer = null) }
    scope.launch { opened(offer.presetId, offer.presetName, offer.notes()) }
  }

  fun copy() {
    val offer = mutableState.value.offer?.takeUnless { it.copying } ?: return
    val difference = offer.difference ?: return
    mutableState.updateOffer { it.copy(copying = true) }
    scope.launch {
      val failure = when (offers.copy(offer.picked.id, difference.changes)) {
        SamplersUpdateResult.Updated, SamplersUpdateResult.Unchanged -> null
        SamplersUpdateResult.StorageFull -> CopyFailure.Storage
        SamplersUpdateResult.Gone -> CopyFailure.ConnectionDeleted
        SamplersUpdateResult.Unreadable, SamplersUpdateResult.Failed, is SamplersUpdateResult.Refused ->
          CopyFailure.Other
      }
      mutableState.update { it.copy(offer = null) }
      if (failure == null) {
        val count = difference.changes.size
        messages.show(AppMessage(getPluralString(Res.plurals.samplers_copied, count, count, offer.picked.name)))
      }
      val copyFailed = failure?.let { CopyFailed.OnConnection(it, offer.picked.name) }
      opened(offer.presetId, offer.presetName, offer.notes(copyFailed))
    }
  }

  private suspend fun imported(fileName: String, result: PresetImportResult.Imported) {
    val name = presetName(fileName)
    val notes = ImportNotes(fileName, result.credentialsSkipped, samplersDroppedNoConnection = false)
    val read = if (result.samplers.isEmpty) null else connections.offerChoices()
    val offer = (read as? OfferChoices.Read)?.let { offerOf(result, name, fileName, it.choices) }
    when {
      offer != null -> {
        mutableState.update { it.copy(running = null, offer = offer) }
        if (listsShown.value == 0) {
          val open = MessageAction(getString(Res.string.open)) { it.showLibraryKind(LibraryKind.Presets, kinds) }
          messages.show(AppMessage(getString(Res.string.preset_imported, name), open))
        }
        findDifference(offer)
      }
      read == null -> opened(result.id, name, notes)
      read == OfferChoices.Unreadable ->
        opened(result.id, name, notes.copy(copyFailed = CopyFailed.ConnectionsUnreadable))
      else -> opened(result.id, name, notes.copy(samplersDroppedNoConnection = true))
    }
  }

  private suspend fun findDifference(offer: SamplerOfferState) {
    when (val found = offers.difference(offer.samplers, offer.picked.id)) {
      is DifferenceResult.Found -> mutableState.updateOffer { current ->
        current.takeIf { it.picked == offer.picked }?.copy(difference = found.difference) ?: current
      }
      DifferenceResult.ConnectionGone, DifferenceResult.Unreadable -> withoutChoice(offer.picked)
    }
  }

  private suspend fun withoutChoice(gone: OfferChoice) {
    val offer = mutableState.value.offer ?: return
    val left = offer.choices - gone
    val next = left.firstOrNull { it.isDefault } ?: left.firstOrNull()
    if (next == null) {
      mutableState.update { it.copy(offer = null) }
      opened(offer.presetId, offer.presetName, offer.notes().copy(samplersDroppedNoConnection = true))
      return
    }
    val changed = offer.copy(choices = left, picked = next, difference = null)
    mutableState.updateOffer { changed }
    findDifference(changed)
  }

  private suspend fun opened(id: Uuid, name: String, notes: ImportNotes) {
    sessions.importNotes(id, notes)
    mutableState.update { it.copy(running = null) }
    next()
    if (listsShown.value > 0) {
      pages.send(id)
    } else {
      val open = MessageAction(getString(Res.string.open)) { it.openPreset(id, kinds) }
      messages.show(AppMessage(getString(Res.string.preset_imported, name), open))
    }
  }
}

private fun MutableStateFlow<PresetImportState>.updateOffer(change: (SamplerOfferState) -> SamplerOfferState) =
  update { state -> state.copy(offer = state.offer?.let(change)) }

private fun SamplerOfferState.notes(copyFailed: CopyFailed? = null) =
  ImportNotes(fileName, credentialsSkipped, samplersDroppedNoConnection = false, copyFailed = copyFailed)

private suspend fun ConnectionRepository.offerChoices(): OfferChoices {
  val default = defaultConnection().catch { emit(null) }.first()
  val read = connections().map<List<Connection>, OfferChoices> { stored ->
    OfferChoices.Read(
      stored.sortedBy { it.fields.name.lowercase() }
        .map { OfferChoice(it.id, it.fields.name, it.fields.format, it.id == default) },
    )
  }
  return read.catch { emit(OfferChoices.Unreadable) }.first()
}

private fun MutableStateFlow<PresetImportState>.claimNext(): PickedFile? {
  var claimed: PickedFile? = null
  update { state ->
    claimed = state.waiting.firstOrNull()?.takeUnless { state.busy }
    claimed?.let { state.copy(running = it.name, waiting = state.waiting.drop(1)) } ?: state
  }
  return claimed
}

private fun offerOf(
  result: PresetImportResult.Imported,
  name: String,
  fileName: String,
  choices: List<OfferChoice>,
): SamplerOfferState? {
  val first = choices.firstOrNull { it.isDefault } ?: choices.firstOrNull() ?: return null
  return SamplerOfferState(
    result.id, name, fileName, result.samplers, result.credentialsSkipped, choices, first, difference = null,
  )
}
