package dev.marzahn.gloriana.ui.personas

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.personas.PersonaDeleteResult
import dev.marzahn.gloriana.logic.personas.PersonaFields
import dev.marzahn.gloriana.logic.personas.PersonaRepository
import dev.marzahn.gloriana.logic.personas.PersonaSaveResult
import dev.marzahn.gloriana.ui.components.NotDeleted
import dev.marzahn.gloriana.ui.lists.DefaultAndDelete
import dev.marzahn.gloriana.ui.lists.DefaultedLibrary
import dev.marzahn.gloriana.ui.lists.LastDeleted
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_made_default_persona
import dev.marzahn.gloriana.ui.resources.snack_saved
import dev.marzahn.gloriana.ui.state.SaveFailure
import dev.marzahn.gloriana.ui.state.SaveStart
import dev.marzahn.gloriana.ui.state.UnsavedSaves
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.uuid.Uuid

enum class PersonaSaveRefusal { NoName }

private class PersonaLibrary(private val repository: PersonaRepository) : DefaultedLibrary {
  override suspend fun idsInListOrder() = repository.personas().first().map { it.id }

  override suspend fun chatsUsing(id: Uuid) = repository.chatsUsing(id)

  override suspend fun defaultName() = repository.defaultName()

  override suspend fun makeDefault(id: Uuid) = repository.makeDefault(id)

  override suspend fun delete(id: Uuid): NotDeleted? = when (repository.delete(id)) {
    PersonaDeleteResult.Deleted, PersonaDeleteResult.Gone -> null
    PersonaDeleteResult.IsDefault -> NotDeleted.IsDefault
    PersonaDeleteResult.ChatsHaveNoDefault, PersonaDeleteResult.Failed -> NotDeleted.Failed
  }
}

class PersonaCommands(
  private val repository: PersonaRepository,
  private val sessions: PersonaSessions,
  private val scope: ApplicationScope,
  private val messages: AppMessages,
) {
  val deletions = LastDeleted()

  val onStored = DefaultAndDelete(
    PersonaLibrary(repository),
    sessions,
    deletions,
    scope,
    messages,
    Res.string.snack_made_default_persona,
  )

  fun save(route: PersonaRoute): PersonaSaveRefusal? {
    val saves = sessions.saves(route)
    val start = saves.startUnless(
      ready = { session, _ -> session.canSave(route) },
      refusal = { fields -> PersonaSaveRefusal.NoName.takeIf { fields.name.isBlank() } },
      refused = { session, _ -> session.copy(nameMissing = true) },
      started = { it.copy(notSaved = null, nameMissing = false) },
    )
    return when (start) {
      is SaveStart.Started -> {
        startWriting(route, saves, start.draft)
        null
      }
      is SaveStart.Refused -> start.reason
      null -> null
    }
  }

  private fun startWriting(
    route: PersonaRoute,
    saves: UnsavedSaves<PersonaSession, PersonaFields>,
    fields: PersonaFields,
  ) {
    scope.launch {
      when (val result = write(route, fields)) {
        is PersonaSaveResult.Saved -> {
          saves.saved(fields) { it.copy(editing = null, savedAs = result.id.takeIf { route is PersonaRoute.New }) }
          messages.show(AppMessage(getString(Res.string.snack_saved, fields.name.trim())))
        }
        PersonaSaveResult.StorageFull -> saves.notSaved { it.copy(notSaved = SaveFailure.Storage) }
        PersonaSaveResult.EmptyName, PersonaSaveResult.Gone, PersonaSaveResult.Failed ->
          saves.notSaved { it.copy(notSaved = SaveFailure.Other) }
      }
    }
  }

  private suspend fun write(route: PersonaRoute, fields: PersonaFields): PersonaSaveResult = when (route) {
    is PersonaRoute.New -> repository.create(route.opening.id, fields)
    is PersonaRoute.Saved -> repository.update(route.id, fields)
  }
}
