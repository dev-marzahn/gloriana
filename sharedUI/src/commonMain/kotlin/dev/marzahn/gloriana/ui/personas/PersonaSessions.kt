package dev.marzahn.gloriana.ui.personas

import dev.marzahn.gloriana.logic.personas.PersonaFields
import dev.marzahn.gloriana.ui.lists.ItemWork
import dev.marzahn.gloriana.ui.lists.ItemWorkSessions
import dev.marzahn.gloriana.ui.state.DraftEdit
import dev.marzahn.gloriana.ui.state.KeyedSessions
import dev.marzahn.gloriana.ui.state.SaveFailure
import dev.marzahn.gloriana.ui.state.UnsavedCopy
import dev.marzahn.gloriana.ui.state.UnsavedSaves
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

data class PersonaSession(
  val editing: UnsavedCopy<PersonaFields>? = null,
  val work: ItemWork = ItemWork(),
  val nameMissing: Boolean = false,
  val notSaved: SaveFailure? = null,
  val savedAs: Uuid? = null,
) {
  val isSaving: Boolean get() = editing?.isSaving == true

  val isIdle: Boolean get() = work.isIdle && !isSaving

  val canActOnStored: Boolean get() = isIdle && editing == null

  val hasChanges: Boolean get() = !isSaving && editing?.hasChanges == true

  fun canSave(route: PersonaRoute): Boolean =
    isIdle && editing != null && (route is PersonaRoute.New || editing.hasChanges)

  fun holdsNothing(route: PersonaRoute): Boolean =
    holdsNoEdits(route) && work.holdsNothing && !nameMissing && notSaved == null && savedAs == null

  private fun holdsNoEdits(route: PersonaRoute): Boolean = when (route) {
    is PersonaRoute.New -> editing?.holdsNothingBeyondTheStoredValue != false
    is PersonaRoute.Saved -> editing == null
  }

  companion object {
    val BlankFields = PersonaFields(name = "")

    fun initial(route: PersonaRoute): PersonaSession = when (route) {
      is PersonaRoute.Saved -> PersonaSession()
      is PersonaRoute.New -> PersonaSession(editing = UnsavedCopy.of(BlankFields))
    }
  }
}

class PersonaSessions : ItemWorkSessions {
  private val sessions = KeyedSessions<PersonaRoute, PersonaSession>(PersonaSession::initial) { route, session ->
    session.holdsNothing(route)
  }

  fun session(route: PersonaRoute): Flow<PersonaSession> = sessions.session(route)

  fun current(route: PersonaRoute): PersonaSession = sessions.current(route)

  override fun <R : Any> claimWork(
    id: Uuid,
    change: (work: ItemWork, canAct: Boolean) -> Pair<ItemWork, R>?,
  ): R? = sessions.cell(PersonaRoute.Saved(id)).claim { session ->
    change(session.work, session.canActOnStored)?.let { (work, result) -> session.copy(work = work) to result }
  }

  override fun deleted(id: Uuid) = sessions.forget(PersonaRoute.Saved(id))

  fun saves(route: PersonaRoute): UnsavedSaves<PersonaSession, PersonaFields> =
    UnsavedSaves(sessions.cell(route), PersonaSession::editing) { session, editing -> session.copy(editing = editing) }

  fun storedNow(route: PersonaRoute, stored: PersonaFields) = sessions.whileHeld(route) { session ->
    session.copy(editing = session.editing?.storedNow(stored))
  }

  fun startEditing(route: PersonaRoute, stored: PersonaFields) = sessions.update(route) { session ->
    if (session.editing != null || !session.isIdle) {
      session
    } else {
      session.copy(editing = UnsavedCopy.of(stored), work = ItemWork())
    }
  }

  fun edit(route: PersonaRoute, edit: DraftEdit<PersonaFields>) = sessions.update(route) { session ->
    val editing = session.editing?.takeUnless { it.isSaving } ?: return@update session
    session.copy(
      editing = editing.edited(edit),
      notSaved = null,
      nameMissing = session.nameMissing && edit !is PersonaIntent.SetName,
    )
  }

  fun discard(route: PersonaRoute) = when (route) {
    is PersonaRoute.New -> sessions.forget(route)
    is PersonaRoute.Saved -> sessions.update(route) { session ->
      if (session.isSaving) session else session.copy(editing = null, notSaved = null, nameMissing = false)
    }
  }

  fun closed(route: PersonaRoute) = when (route) {
    is PersonaRoute.New -> sessions.forget(route)
    is PersonaRoute.Saved -> sessions.update(route) { session ->
      session.copy(
        editing = session.editing?.takeIf { it.isSaving },
        work = session.work.closed(),
        nameMissing = false,
        notSaved = null,
      )
    }
  }
}
