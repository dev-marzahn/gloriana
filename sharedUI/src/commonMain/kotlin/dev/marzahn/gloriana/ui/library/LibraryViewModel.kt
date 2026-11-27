package dev.marzahn.gloriana.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.assets.AssetFiles
import dev.marzahn.gloriana.logic.characters.AlreadyInLibraryAnswer
import dev.marzahn.gloriana.logic.characters.CharacterRepository
import dev.marzahn.gloriana.logic.characters.CharacterSummary
import dev.marzahn.gloriana.logic.characters.PickedFile
import dev.marzahn.gloriana.ui.characters.CharacterImports
import dev.marzahn.gloriana.ui.characters.ImportState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

private const val STOP_TIMEOUT_MILLIS = 5_000L

data class CharacterTile(val id: Uuid, val name: String, val avatarPath: String?)

sealed interface LibraryState {
  data object Loading : LibraryState

  data object CouldNotLoad : LibraryState

  data object Empty : LibraryState

  data class Shown(val tiles: List<CharacterTile>, val imports: ImportState) : LibraryState
}

private sealed interface Listing {
  data object Loading : Listing

  data object Failed : Listing

  data class Loaded(val characters: List<CharacterSummary>) : Listing
}

class LibraryViewModel(
  private val repository: CharacterRepository,
  private val imports: CharacterImports,
  private val assets: AssetFiles,
) : ViewModel() {
  private val attempt = MutableStateFlow(0)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val listing: Flow<Listing> = attempt.flatMapLatest {
    repository.characters()
      .map<List<CharacterSummary>, Listing> { Listing.Loaded(it) }
      .onStart { emit(Listing.Loading) }
      .catch { emit(Listing.Failed) }
  }

  val state: StateFlow<LibraryState> = combine(listing, imports.state, ::libraryState)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LibraryState.Loading)

  suspend fun openPagesWhileShown(open: (Uuid) -> Unit): Nothing = imports.openPagesWhileShown(open)

  fun preview(id: Uuid): Flow<CharacterPreview?> = repository.character(id).map { character ->
    character?.let {
      CharacterPreview(
        id = it.id,
        name = it.fields.name,
        creator = it.fields.creator,
        version = it.fields.characterVersion,
        description = it.fields.description,
        avatarPath = it.avatar?.let(assets::pathOf),
      )
    }
  }

  fun retry() {
    attempt.value++
  }

  fun import(files: List<PickedFile>) = imports.start(files)

  fun answer(answer: AlreadyInLibraryAnswer) = imports.answer(answer)

  fun showQuestion() = imports.showQuestion()

  fun dismissNote() = imports.dismissNote()

  private fun libraryState(listing: Listing, imports: ImportState): LibraryState = when (listing) {
    Listing.Loading -> LibraryState.Loading
    Listing.Failed -> LibraryState.CouldNotLoad
    is Listing.Loaded -> {
      val tiles = listing.characters.map { CharacterTile(it.id, it.name, it.avatar?.let(assets::pathOf)) }
      if (tiles.isEmpty() && imports == ImportState()) LibraryState.Empty else LibraryState.Shown(tiles, imports)
    }
  }
}
