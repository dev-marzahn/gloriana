package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.ui.lists.ItemWork
import dev.marzahn.gloriana.ui.lists.ItemWorkSessions
import dev.marzahn.gloriana.ui.state.DraftEdit
import dev.marzahn.gloriana.ui.state.KeyedSessions
import dev.marzahn.gloriana.ui.state.SaveFailure
import dev.marzahn.gloriana.ui.state.UnsavedCopy
import dev.marzahn.gloriana.ui.state.UnsavedSaves
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

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
  val work: ItemWork = ItemWork(),
  val notSaved: SaveFailure? = null,
  val importNotes: ImportNotes? = null,
) {
  val isIdle: Boolean get() = work.isIdle && unsaved?.isSaving != true

  val canSave: Boolean get() = isIdle && unsaved?.hasChanges == true

  val holdsNothing: Boolean
    get() = unsaved?.holdsNothingBeyondTheStoredValue != false && work.holdsNothing &&
      notSaved == null && importNotes == null
}

class PresetSessions : ItemWorkSessions {
  private val sessions = KeyedSessions<Uuid, PresetSession>({ PresetSession() }) { _, session ->
    session.holdsNothing
  }

  fun session(id: Uuid): Flow<PresetSession> = sessions.session(id)

  fun current(id: Uuid): PresetSession = sessions.current(id)

  override fun <R : Any> claimWork(
    id: Uuid,
    change: (work: ItemWork, canAct: Boolean) -> Pair<ItemWork, R>?,
  ): R? = sessions.cell(id).claim { session ->
    change(session.work, session.isIdle)?.let { (work, result) -> session.copy(work = work) to result }
  }

  override fun deleted(id: Uuid) = sessions.forget(id)

  fun saves(id: Uuid): UnsavedSaves<PresetSession, PresetContent> =
    UnsavedSaves(sessions.cell(id), PresetSession::unsaved) { session, unsaved -> session.copy(unsaved = unsaved) }

  fun storedNow(id: Uuid, stored: PresetContent) = sessions.update(id) { session ->
    session.copy(unsaved = session.unsaved?.storedNow(stored))
  }

  fun edit(id: Uuid, latestStored: PresetContent, edit: DraftEdit<PresetContent>) = sessions.update(id) { session ->
    session.copy(unsaved = (session.unsaved ?: UnsavedCopy.of(latestStored)).edited(edit), notSaved = null)
  }

  fun discard(id: Uuid) = sessions.update(id) { it.copy(unsaved = it.unsaved?.discarded(), notSaved = null) }

  fun importNotes(id: Uuid, notes: ImportNotes?) = sessions.update(id) {
    it.copy(importNotes = notes?.takeUnless(ImportNotes::isEmpty))
  }
}
