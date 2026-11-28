package dev.marzahn.gloriana.ui.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.app.ShippedDefaultsAtStart
import dev.marzahn.gloriana.logic.personas.Persona
import dev.marzahn.gloriana.logic.personas.PersonaRepository
import dev.marzahn.gloriana.ui.lists.Deleted
import dev.marzahn.gloriana.ui.state.WhileCollected
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

sealed interface PersonasListing {
  data object Loading : PersonasListing

  data object CouldNotLoad : PersonasListing

  data class Loaded(val personas: List<Persona>, val defaultId: Uuid?) : PersonasListing
}

class PersonasViewModel(
  private val repository: PersonaRepository,
  private val commands: PersonaCommands,
  private val shippedDefaults: ShippedDefaultsAtStart,
) : ViewModel() {
  private val attempt = MutableStateFlow(0)

  val lastDeleted: StateFlow<Deleted?> = commands.deletions.latest

  fun focused(delete: Deleted) = commands.deletions.handled(delete)

  @OptIn(ExperimentalCoroutinesApi::class)
  val listing: StateFlow<PersonasListing> = attempt.flatMapLatest {
    flow {
      shippedDefaults.missing.await()
      emitAll(
        combine<List<Persona>, Uuid?, PersonasListing>(
          repository.personas(),
          repository.defaultPersona(),
        ) { personas, defaultId -> PersonasListing.Loaded(personas, defaultId) },
      )
    }
      .onStart { emit(PersonasListing.Loading) }
      .catch { emit(PersonasListing.CouldNotLoad) }
  }.stateIn(viewModelScope, WhileCollected, PersonasListing.Loading)

  fun retry() {
    attempt.value++
  }
}
