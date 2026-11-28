package dev.marzahn.gloriana.ui.presets

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.presets.Preset
import dev.marzahn.gloriana.logic.presets.PresetDeleteResult
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.PresetSaveResult
import dev.marzahn.gloriana.ui.components.NotDeleted
import dev.marzahn.gloriana.ui.lists.DefaultAndDelete
import dev.marzahn.gloriana.ui.lists.DefaultedLibrary
import dev.marzahn.gloriana.ui.lists.LastDeleted
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_made_default_preset
import dev.marzahn.gloriana.ui.resources.snack_saved
import dev.marzahn.gloriana.ui.state.SaveFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.uuid.Uuid

private class PresetLibrary(private val repository: PresetRepository) : DefaultedLibrary {
  override suspend fun idsInListOrder() = repository.presets().first().map { it.id }

  override suspend fun chatsUsing(id: Uuid) = repository.chatsUsing(id)

  override suspend fun defaultName() = repository.defaultName()

  override suspend fun makeDefault(id: Uuid) = repository.makeDefault(id)

  override suspend fun delete(id: Uuid): NotDeleted? = when (repository.delete(id)) {
    PresetDeleteResult.Deleted, PresetDeleteResult.Gone -> null
    PresetDeleteResult.IsDefault -> NotDeleted.IsDefault
    PresetDeleteResult.ChatsHaveNoDefault, PresetDeleteResult.Failed -> NotDeleted.Failed
  }
}

class PresetCommands(
  private val repository: PresetRepository,
  private val sessions: PresetSessions,
  private val scope: ApplicationScope,
  private val messages: AppMessages,
) {
  val deletions = LastDeleted()

  val onStored = DefaultAndDelete(
    PresetLibrary(repository),
    sessions,
    deletions,
    scope,
    messages,
    Res.string.snack_made_default_preset,
  )

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
}
