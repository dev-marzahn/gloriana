package dev.marzahn.gloriana.ui.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.files.PickedFile
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.PresetSummary
import dev.marzahn.gloriana.ui.lists.Deleted
import dev.marzahn.gloriana.ui.state.WhileCollected
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

sealed interface PresetsListing {
  data object Loading : PresetsListing

  data object CouldNotLoad : PresetsListing

  data class Loaded(val presets: List<PresetSummary>) : PresetsListing
}

data class PresetsState(val listing: PresetsListing, val imports: PresetImportState) {
  val isEmpty: Boolean
    get() = listing is PresetsListing.Loaded && listing.presets.isEmpty() && imports.running == null &&
      imports.failures.isEmpty() && imports.waiting.isEmpty()
}

class PresetsViewModel(
  private val repository: PresetRepository,
  private val imports: PresetImports,
  private val commands: PresetCommands,
) : ViewModel() {
  private val attempt = MutableStateFlow(0)

  val lastDeleted: StateFlow<Deleted?> = commands.deletions.latest

  fun focused(delete: Deleted) = commands.deletions.handled(delete)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val listing: Flow<PresetsListing> = attempt.flatMapLatest {
    repository.presets()
      .map<List<PresetSummary>, PresetsListing> { PresetsListing.Loaded(it) }
      .onStart { emit(PresetsListing.Loading) }
      .catch { emit(PresetsListing.CouldNotLoad) }
  }

  val state: StateFlow<PresetsState> = combine(listing, imports.state, ::PresetsState)
    .stateIn(viewModelScope, WhileCollected, PresetsState(PresetsListing.Loading, PresetImportState()))

  suspend fun openPagesWhileShown(open: (Uuid) -> Unit): Nothing = imports.openPagesWhileShown(open)

  fun retry() {
    attempt.value++
  }

  fun import(file: PickedFile) = imports.start(file)

  fun dismissFailure(note: ImportFailureNote) = imports.dismissFailure(note)

  fun pick(choice: OfferChoice) = imports.pick(choice)

  fun copy() = imports.copy()

  fun decline() = imports.decline()
}
