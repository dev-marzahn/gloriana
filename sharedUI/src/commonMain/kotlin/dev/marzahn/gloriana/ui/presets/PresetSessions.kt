package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.ui.state.DraftEdit
import dev.marzahn.gloriana.ui.state.SaveFailure
import dev.marzahn.gloriana.ui.state.UnsavedCopy
import dev.marzahn.gloriana.ui.state.updateAndClaim
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

sealed interface PresetActivity {
  data object Idle : PresetActivity

  data class ConfirmingDelete(val chatsUsing: Int) : PresetActivity

  data class Deleting(val chatsUsing: Int) : PresetActivity
}

enum class CopyFailure { Storage, ConnectionDeleted, Other }

sealed interface CopyFailed {
  data class OnConnection(val failure: CopyFailure, val connectionName: String) : CopyFailed

  data object ConnectionsUnreadable : CopyFailed
}

data class ImportNotes(
  val fileName: String,
  val credentialsSkipped: Boolean,
  val samplersDroppedNoConnection: Boolean,
  val copyFailed: CopyFailed? = null,
) {
  val hasInfo: Boolean get() = credentialsSkipped || samplersDroppedNoConnection

  val isEmpty: Boolean get() = !hasInfo && copyFailed == null

  fun withoutInfo() = copy(credentialsSkipped = false, samplersDroppedNoConnection = false)
}

data class PresetSession(
  val unsaved: UnsavedCopy<PresetContent>? = null,
  val activity: PresetActivity = PresetActivity.Idle,
  val notSaved: SaveFailure? = null,
  val notDeleted: Boolean = false,
  val importNotes: ImportNotes? = null,
) {
  val isIdle: Boolean get() = activity == PresetActivity.Idle && unsaved?.isSaving != true

  val holdsNothing: Boolean
    get() = unsaved?.holdsNothingBeyondTheStoredValue != false && activity == PresetActivity.Idle &&
      notSaved == null && !notDeleted && importNotes == null

  fun unsavedOver(stored: PresetContent): UnsavedCopy<PresetContent> =
    (unsaved ?: UnsavedCopy.of(stored)).storedNow(stored)
}

class PresetSessions {
  private val sessions = MutableStateFlow<Map<Uuid, PresetSession>>(emptyMap())

  fun session(id: Uuid): Flow<PresetSession> =
    sessions.map { it[id] ?: PresetSession() }.distinctUntilChanged()

  fun current(id: Uuid): PresetSession = sessions.value[id] ?: PresetSession()

  fun update(id: Uuid, change: (PresetSession) -> PresetSession) {
    sessions.update { all -> all.with(id, change(all[id] ?: PresetSession())) }
  }

  fun <T : Any> claim(id: Uuid, change: (PresetSession) -> Pair<PresetSession, T>?): T? =
    sessions.updateAndClaim { all ->
      val result = change(all[id] ?: PresetSession())
      if (result == null) all to null else all.with(id, result.first) to result.second
    }

  fun forget(id: Uuid) = sessions.update { it - id }

  fun storedNow(id: Uuid, stored: PresetContent) = update(id) { session ->
    session.copy(unsaved = session.unsaved?.storedNow(stored))
  }

  fun edit(id: Uuid, latestStored: PresetContent, edit: DraftEdit<PresetContent>) = update(id) { session ->
    session.copy(unsaved = (session.unsaved ?: UnsavedCopy.of(latestStored)).edited(edit), notSaved = null)
  }

  fun discard(id: Uuid) = update(id) { it.copy(unsaved = it.unsaved?.discarded(), notSaved = null) }

  fun importNotes(id: Uuid, notes: ImportNotes?) = update(id) {
    it.copy(importNotes = notes?.takeUnless(ImportNotes::isEmpty))
  }
}

private fun Map<Uuid, PresetSession>.with(id: Uuid, session: PresetSession) =
  if (session.holdsNothing) this - id else this + (id to session)
