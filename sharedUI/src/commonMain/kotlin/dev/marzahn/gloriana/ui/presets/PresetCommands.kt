package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.presets.Preset
import dev.marzahn.gloriana.logic.presets.PresetDeleteResult
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.PresetSaveResult
import dev.marzahn.gloriana.ui.lists.Deleted
import dev.marzahn.gloriana.ui.lists.LastDeleted
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.preset_deleted
import dev.marzahn.gloriana.ui.resources.preset_saved
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class PresetCommands(
  private val repository: PresetRepository,
  private val sessions: PresetSessions,
  private val scope: ApplicationScope,
  private val messages: AppMessages,
) {
  val deletions = LastDeleted()

  fun save(stored: Preset) {
    val draft = sessions.claim(stored.id) { session ->
      session.draft?.takeIf { session.activity == PresetActivity.Idle }?.let { draft ->
        session.copy(activity = PresetActivity.Saving, note = null) to draft
      }
    } ?: return
    scope.launch {
      val note = when (repository.save(stored.copy(content = draft))) {
        PresetSaveResult.Saved -> null
        PresetSaveResult.StorageFull -> PresetNote.NotSavedStorage
        PresetSaveResult.Gone, PresetSaveResult.Failed, is PresetSaveResult.Refused -> PresetNote.NotSaved
      }
      sessions.update(stored.id) { session ->
        if (note != null) {
          session.copy(activity = PresetActivity.Idle, note = note)
        } else {
          session.copy(
            activity = PresetActivity.Idle,
            draft = session.draft.takeUnless { it == draft },
            savedAhead = draft,
          )
        }
      }
      if (note == null) messages.show(AppMessage(getString(Res.string.preset_saved, stored.name)))
    }
  }

  fun askToDelete(stored: Preset) {
    if (sessions.current(stored.id).activity != PresetActivity.Idle) return
    scope.launch {
      val chats = repository.chatsUsing(stored.id)
      sessions.update(stored.id) { session ->
        if (session.activity != PresetActivity.Idle) {
          session
        } else {
          session.copy(activity = PresetActivity.ConfirmingDelete(chats), note = null)
        }
      }
    }
  }

  fun cancelDelete(stored: Preset) = sessions.update(stored.id) { session ->
    if (session.activity is PresetActivity.ConfirmingDelete) session.copy(activity = PresetActivity.Idle) else session
  }

  fun confirmDelete(stored: Preset) {
    sessions.claim(stored.id) { session ->
      (session.activity as? PresetActivity.ConfirmingDelete)?.let { confirming ->
        session.copy(activity = PresetActivity.Deleting(confirming.chatsUsing)) to confirming
      }
    } ?: return
    scope.launch {
      val index = repository.presets().catch { emit(emptyList()) }.first().indexOfFirst { it.id == stored.id }
      when (repository.delete(stored.id)) {
        PresetDeleteResult.Deleted, PresetDeleteResult.Gone -> {
          sessions.forget(stored.id)
          deletions.record(Deleted(stored.id, index.coerceAtLeast(0)))
          messages.show(AppMessage(getString(Res.string.preset_deleted, stored.name)))
        }
        PresetDeleteResult.Failed -> sessions.update(stored.id) {
          it.copy(activity = PresetActivity.Idle, note = PresetNote.NotDeleted)
        }
      }
    }
  }
}
