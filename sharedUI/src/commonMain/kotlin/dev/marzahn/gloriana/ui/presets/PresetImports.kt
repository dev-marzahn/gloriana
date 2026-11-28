package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.app.failingAs
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
import dev.marzahn.gloriana.ui.navigation.PageOpener
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.open
import dev.marzahn.gloriana.ui.resources.preset_imported
import dev.marzahn.gloriana.ui.resources.preset_not_imported
import dev.marzahn.gloriana.ui.resources.samplers_copied
import dev.marzahn.gloriana.ui.state.updateAndClaim
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

data class QueuedImport(val id: Long, val file: PickedFile) {
  val fileName: String get() = file.name
}

data class ImportFailureNote(val importId: Long, val fileName: String, val reason: PresetImportFailure)

data class PresetImportState(
  val running: QueuedImport? = null,
  val failures: List<ImportFailureNote> = emptyList(),
  val offer: SamplerOfferState? = null,
  val waiting: List<QueuedImport> = emptyList(),
  val nextImportId: Long = 0,
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
) {
  private val mutableState = MutableStateFlow(PresetImportState())
  private val pages = PageOpener<ImportedPreset> { preset -> scope.launch { messages.showImported(preset) } }

  val state: StateFlow<PresetImportState> = mutableState.asStateFlow()

  suspend fun openPagesWhileShown(open: (Uuid) -> Unit): Nothing = pages.openWhileShown { open(it.id) }

  fun start(file: PickedFile) {
    mutableState.update { state ->
      state.copy(
        waiting = state.waiting + QueuedImport(state.nextImportId, file),
        failures = if (state.busy) state.failures else emptyList(),
        nextImportId = state.nextImportId + 1,
      )
    }
    next()
  }

  private fun next() {
    val queued = mutableState.claimNext() ?: return
    scope.launch {
      when (val result = import.import(queued.file)) {
        is PresetImportResult.Failed -> {
          val note = ImportFailureNote(queued.id, queued.fileName, result.reason)
          mutableState.update { it.copy(running = null, failures = it.failures + note) }
          next()
          if (!pages.shown) {
            val open = MessageAction(getString(Res.string.open)) { it.showPresets() }
            messages.show(AppMessage(getString(Res.string.preset_not_imported, note.fileName), open))
          }
        }
        is PresetImportResult.Imported -> imported(queued.fileName, result)
      }
    }
  }

  fun dismissFailure(note: ImportFailureNote) = mutableState.update { it.copy(failures = it.failures - note) }

  fun pick(choice: OfferChoice) {
    if (mutableState.value.offer?.takeUnless { it.copying } == null) return
    mutableState.updateOffer { it.copy(picked = choice, difference = null) }
    scope.launch { findDifference(choice) }
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
      val result = offers.copy(difference.connectionId, difference.changes)
      val failure = when (result) {
        SamplersUpdateResult.Updated, SamplersUpdateResult.Unchanged -> null
        SamplersUpdateResult.StorageFull -> CopyFailure.Storage
        SamplersUpdateResult.Gone -> CopyFailure.ConnectionDeleted
        SamplersUpdateResult.Unreadable, SamplersUpdateResult.Failed, is SamplersUpdateResult.Refused ->
          CopyFailure.Other
      }
      mutableState.update { it.copy(offer = null) }
      if (result == SamplersUpdateResult.Updated) {
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
        if (!pages.shown) {
          val open = MessageAction(getString(Res.string.open)) { it.showPresets() }
          messages.show(AppMessage(getString(Res.string.preset_imported, name), open))
        }
        findDifference(offer.picked)
      }
      read == null -> opened(result.id, name, notes)
      read == OfferChoices.Unreadable ->
        opened(result.id, name, notes.copy(copyFailed = CopyFailed.ConnectionsUnreadable))
      else -> opened(result.id, name, notes.copy(samplersDroppedNoConnection = true))
    }
  }

  private suspend fun findDifference(choice: OfferChoice) {
    val samplers = mutableState.value.offer?.samplers ?: return
    val found = failingAs(DifferenceResult.Unreadable) { offers.difference(samplers, choice.id) }
    when (found) {
      is DifferenceResult.Found -> mutableState.updateOffer { offer ->
        if (offer.picked == choice && !offer.copying) offer.copy(difference = found.difference) else offer
      }
      DifferenceResult.ConnectionGone -> withoutChoice(choice)
      DifferenceResult.Unreadable -> mutableState.closeOffer { it.picked == choice }?.let { offer ->
        val copyFailed = CopyFailed.OnConnection(CopyFailure.Other, offer.picked.name)
        opened(offer.presetId, offer.presetName, offer.notes(copyFailed))
      }
    }
  }

  private suspend fun withoutChoice(gone: OfferChoice) {
    val dropped = mutableState.updateAndClaim { state ->
      val offer = state.offer?.takeUnless { it.copying } ?: return@updateAndClaim state to ChoiceDropped.Kept
      val left = offer.choices - gone
      val next = if (offer.picked == gone) left.firstOrNull { it.isDefault } ?: left.firstOrNull() else offer.picked
      when (next) {
        null -> state.copy(offer = null) to ChoiceDropped.LastOne(offer)
        offer.picked -> state.copy(offer = offer.copy(choices = left)) to ChoiceDropped.Kept
        else -> state.copy(offer = offer.copy(choices = left, picked = next, difference = null)) to
          ChoiceDropped.Repicked(next)
      }
    }
    when (dropped) {
      is ChoiceDropped.LastOne -> with(dropped.offer) {
        opened(presetId, presetName, notes().copy(samplersDroppedNoConnection = true))
      }
      is ChoiceDropped.Repicked -> findDifference(dropped.choice)
      ChoiceDropped.Kept -> Unit
    }
  }

  private suspend fun opened(id: Uuid, name: String, notes: ImportNotes) {
    sessions.importNotes(id, notes)
    mutableState.update { it.copy(running = null) }
    next()
    val preset = ImportedPreset(id, name)
    if (!pages.offer(preset)) messages.showImported(preset)
  }
}

private data class ImportedPreset(val id: Uuid, val name: String)

private suspend fun AppMessages.showImported(preset: ImportedPreset) {
  val open = MessageAction(getString(Res.string.open)) { it.openPreset(preset.id) }
  show(AppMessage(getString(Res.string.preset_imported, preset.name), open))
}

private fun MutableStateFlow<PresetImportState>.updateOffer(change: (SamplerOfferState) -> SamplerOfferState) =
  update { state -> state.copy(offer = state.offer?.let(change)) }

private sealed interface ChoiceDropped {
  data class LastOne(val offer: SamplerOfferState) : ChoiceDropped

  data class Repicked(val choice: OfferChoice) : ChoiceDropped

  data object Kept : ChoiceDropped
}

private fun MutableStateFlow<PresetImportState>.closeOffer(where: (SamplerOfferState) -> Boolean) =
  updateAndClaim { state ->
    val closed = state.offer?.takeIf { !it.copying && where(it) }
    (if (closed == null) state else state.copy(offer = null)) to closed
  }

private fun SamplerOfferState.notes(copyFailed: CopyFailed? = null) =
  ImportNotes(fileName, credentialsSkipped, samplersDroppedNoConnection = false, copyFailed = copyFailed)

private suspend fun ConnectionRepository.offerChoices(): OfferChoices {
  val default = defaultConnection().catch { emit(null) }.first()
  val read = connections().map<List<Connection>, OfferChoices> { stored ->
    OfferChoices.Read(stored.map { OfferChoice(it.id, it.fields.name, it.fields.format, it.id == default) })
  }
  return read.catch { emit(OfferChoices.Unreadable) }.first()
}

private fun MutableStateFlow<PresetImportState>.claimNext(): QueuedImport? = updateAndClaim { state ->
  val claimed = state.waiting.firstOrNull()?.takeUnless { state.busy }
  (claimed?.let { state.copy(running = it, waiting = state.waiting.drop(1)) } ?: state) to claimed
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
