package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.presets.PresetContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

sealed interface PresetActivity {
  data object Idle : PresetActivity

  data object Saving : PresetActivity

  data class ConfirmingDelete(val chatsUsing: Int) : PresetActivity

  data class Deleting(val chatsUsing: Int) : PresetActivity
}

enum class PresetNote { NotSavedStorage, NotSaved, NotDeleted }

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
  val draft: PresetContent? = null,
  val activity: PresetActivity = PresetActivity.Idle,
  val note: PresetNote? = null,
  val importNotes: ImportNotes? = null,
)

class PresetSessions {
  private val sessions = MutableStateFlow<Map<Uuid, PresetSession>>(emptyMap())

  fun session(id: Uuid): Flow<PresetSession> =
    sessions.map { it[id] ?: PresetSession() }.distinctUntilChanged()

  fun current(id: Uuid): PresetSession = sessions.value[id] ?: PresetSession()

  fun hasChanges(id: Uuid): Boolean = current(id).draft != null

  fun update(id: Uuid, change: (PresetSession) -> PresetSession) {
    sessions.update { all ->
      val changed = change(all[id] ?: PresetSession())
      if (changed == PresetSession()) all - id else all + (id to changed)
    }
  }

  fun <T : Any> claim(id: Uuid, change: (PresetSession) -> Pair<PresetSession, T>?): T? {
    var taken: T? = null
    update(id) { session ->
      val result = change(session)
      taken = result?.second
      result?.first ?: session
    }
    return taken
  }

  fun forget(id: Uuid) = sessions.update { it - id }

  fun edit(id: Uuid, stored: PresetContent, change: (PresetContent) -> PresetContent) = update(id) { session ->
    val edited = change(session.draft ?: stored)
    session.copy(draft = edited.takeUnless { it == stored }, note = null)
  }

  fun discard(id: Uuid) = update(id) { it.copy(draft = null, note = null) }

  fun importNotes(id: Uuid, notes: ImportNotes?) = update(id) {
    it.copy(importNotes = notes?.takeUnless(ImportNotes::isEmpty))
  }
}
