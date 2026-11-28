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
import dev.marzahn.gloriana.ui.resources.snack_deleted
import dev.marzahn.gloriana.ui.resources.snack_saved
import dev.marzahn.gloriana.ui.state.SaveFailure
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
    val saves = sessions.saves(stored.id)
    val draft = saves.start(
      ready = { session, _ -> session.canSave },
      started = { it.copy(notSaved = null) },
    ) ?: return
    scope.launch {
      when (repository.save(stored.copy(content = draft))) {
        PresetSaveResult.Saved -> {
          saves.saved(draft)
          messages.show(AppMessage(getString(Res.string.snack_saved, stored.name)))
        }
        PresetSaveResult.StorageFull -> saves.notSaved { it.copy(notSaved = SaveFailure.Storage) }
        PresetSaveResult.Gone, PresetSaveResult.Failed, is PresetSaveResult.Refused ->
          saves.notSaved { it.copy(notSaved = SaveFailure.Other) }
      }
    }
  }

  fun askToDelete(stored: Preset) {
    if (!sessions.current(stored.id).isIdle) return
    scope.launch {
      val chats = repository.chatsUsing(stored.id)
      sessions.update(stored.id) { session ->
        if (!session.isIdle) return@update session
        session.copy(activity = PresetActivity.ConfirmingDelete(chats), notDeleted = false)
      }
    }
  }

  fun cancelDelete(stored: Preset) = sessions.update(stored.id) { session ->
    if (session.activity is PresetActivity.ConfirmingDelete) session.copy(activity = PresetActivity.Idle) else session
  }

  fun confirmDelete(stored: Preset) {
    sessions.cell(stored.id).claim { session ->
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
          messages.show(AppMessage(getString(Res.string.snack_deleted, stored.name)))
        }
        PresetDeleteResult.IsDefault, PresetDeleteResult.ChatsHaveNoDefault, PresetDeleteResult.Failed ->
          sessions.update(stored.id) { it.copy(activity = PresetActivity.Idle, notDeleted = true) }
      }
    }
  }
}
