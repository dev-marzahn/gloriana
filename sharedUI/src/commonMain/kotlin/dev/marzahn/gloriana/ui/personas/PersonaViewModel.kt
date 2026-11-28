package dev.marzahn.gloriana.ui.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.personas.Persona
import dev.marzahn.gloriana.logic.personas.PersonaFields
import dev.marzahn.gloriana.logic.personas.PersonaRepository
import dev.marzahn.gloriana.ui.state.DefaultMarked
import dev.marzahn.gloriana.ui.state.DraftEdit
import dev.marzahn.gloriana.ui.state.StoredReads
import dev.marzahn.gloriana.ui.state.StoredRow
import dev.marzahn.gloriana.ui.state.StoredValue
import dev.marzahn.gloriana.ui.state.WhileCollected
import dev.marzahn.gloriana.ui.state.markedDefaultWhen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

sealed interface PersonaIntent {
  sealed interface OnPage : PersonaIntent

  sealed interface OnStored : OnPage

  sealed interface Edit : OnPage, DraftEdit<PersonaFields> {
    override fun replaces(earlier: DraftEdit<PersonaFields>) = earlier::class == this::class
  }

  data class SetName(val text: String) : Edit {
    override fun applied(to: PersonaFields) = to.copy(name = text)
  }

  data class SetDescription(val text: String) : Edit {
    override fun applied(to: PersonaFields) = to.copy(description = text)
  }

  data class SetLabel(val text: String) : Edit {
    override fun applied(to: PersonaFields) = to.copy(personaLabel = text)
  }

  data class SetNote(val text: String) : Edit {
    override fun applied(to: PersonaFields) = to.copy(personaNote = text)
  }

  data object StartEditing : OnPage

  data object Save : OnPage

  data object Discard : OnPage

  data object MakeDefault : OnStored

  data object AskToDelete : OnStored

  data object CancelDelete : OnStored

  data object ConfirmDelete : OnStored

  data object Retry : PersonaIntent
}

sealed interface PersonaPageState {
  data object Loading : PersonaPageState

  data object CouldNotLoad : PersonaPageState

  data object Gone : PersonaPageState

  data class Shown(
    val route: PersonaRoute,
    val stored: Persona?,
    val session: PersonaSession,
    val isDefault: Boolean,
  ) : PersonaPageState {
    val fields: PersonaFields = session.editing?.draft ?: stored?.fields ?: PersonaSession.BlankFields

    val isEditing: Boolean get() = session.editing != null

    val savedName: String get() = stored?.fields?.name.orEmpty()

    val hasChanges: Boolean get() = session.hasChanges

    val canSave: Boolean get() = session.canSave(route)
  }
}

class PersonaViewModel(
  private val repository: PersonaRepository,
  private val sessions: PersonaSessions,
  private val commands: PersonaCommands,
) : ViewModel() {
  private val route = MutableStateFlow<PersonaRoute?>(null)
  private val reads = StoredReads.rows<DefaultMarked<Persona>>()
  private val nameFocus = Channel<Unit>(Channel.CONFLATED)

  val focusName: Flow<Unit> = nameFocus.receiveAsFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  val state: StateFlow<PersonaPageState> = route.filterNotNull()
    .flatMapLatest(::pageState)
    .stateIn(viewModelScope, WhileCollected, PersonaPageState.Loading)

  fun show(route: PersonaRoute) {
    this.route.value = route
  }

  fun on(intent: PersonaIntent) {
    when (intent) {
      PersonaIntent.Retry -> reads.retry()
      is PersonaIntent.OnPage -> (state.value as? PersonaPageState.Shown)?.let { onPage(it, intent) }
    }
  }

  private fun onPage(page: PersonaPageState.Shown, intent: PersonaIntent.OnPage) {
    val route = page.route
    when (intent) {
      is PersonaIntent.Edit -> sessions.edit(route, intent)
      PersonaIntent.StartEditing -> page.stored?.let { sessions.startEditing(route, it.fields) }
      PersonaIntent.Save -> commands.save(route)?.let { nameFocus.trySend(Unit) }
      PersonaIntent.Discard -> sessions.discard(route)
      is PersonaIntent.OnStored -> page.stored?.let { onStored(page, it, intent) }
    }
  }

  private fun onStored(page: PersonaPageState.Shown, stored: Persona, intent: PersonaIntent.OnStored) {
    when (intent) {
      PersonaIntent.MakeDefault -> if (!page.isDefault) commands.onStored.makeDefault(stored.id, stored.fields.name)
      PersonaIntent.AskToDelete -> if (!page.isDefault) commands.onStored.askToDelete(stored.id)
      PersonaIntent.CancelDelete -> commands.onStored.cancelDelete(stored.id)
      PersonaIntent.ConfirmDelete -> commands.onStored.confirmDelete(stored.id, stored.fields.name)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun pageState(route: PersonaRoute): Flow<PersonaPageState> = when (route) {
    is PersonaRoute.New -> sessions.session(route).flatMapLatest { session ->
      session.savedAs?.let { pageState(PersonaRoute.Saved(it)) }
        ?: flowOf(PersonaPageState.Shown(route, null, session, isDefault = false))
    }
    is PersonaRoute.Saved -> combine(follow(route), sessions.session(route)) { row, session ->
      when (row) {
        StoredValue.Loading -> PersonaPageState.Loading
        StoredValue.CouldNotLoad -> PersonaPageState.CouldNotLoad
        StoredRow.Gone -> PersonaPageState.Gone
        is StoredValue.Found -> PersonaPageState.Shown(route, row.value.row, session, row.value.isDefault)
      }
    }
  }

  private fun follow(route: PersonaRoute.Saved): Flow<StoredRow<DefaultMarked<Persona>>> =
    reads.follow(route) { repository.persona(route.id).markedDefaultWhen(route.id, repository.defaultPersona()) }
      .onEach { row -> if (row is StoredValue.Found) sessions.storedNow(route, row.value.row.fields) }
}
