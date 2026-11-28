package dev.marzahn.gloriana.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.assets.AssetFiles
import dev.marzahn.gloriana.logic.characters.CardFormat
import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.CharacterRepository
import dev.marzahn.gloriana.logic.characters.ChatsOfDeletedCharacter
import dev.marzahn.gloriana.logic.characters.DeleteResult
import dev.marzahn.gloriana.ui.files.ExportState
import dev.marzahn.gloriana.ui.files.SaveTarget
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_deleted
import dev.marzahn.gloriana.ui.state.WhileCollected
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.uuid.Uuid

enum class DeleteProgress { Idle, Confirming, Deleting, Failed }

sealed interface CharacterPageState {
  data object Loading : CharacterPageState

  data object CouldNotLoad : CharacterPageState

  data object Gone : CharacterPageState

  data class Shown(
    val character: Character,
    val avatarPath: String?,
    val export: ExportState?,
    val delete: DeleteProgress,
  ) : CharacterPageState
}

private data class PageInputs(val id: Uuid, val attempt: Int)

class CharacterViewModel(
  private val repository: CharacterRepository,
  private val exports: CharacterExports,
  private val assets: AssetFiles,
  private val messages: AppMessages,
) : ViewModel() {
  private val inputs = MutableStateFlow<PageInputs?>(null)
  private val delete = MutableStateFlow(DeleteProgress.Idle)
  private val closes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  @OptIn(ExperimentalCoroutinesApi::class)
  val state: StateFlow<CharacterPageState> = inputs.filterNotNull()
    .flatMapLatest { (id) -> combine(characterOf(id), exports.runs.state(id), delete, ::pageState) }
    .stateIn(viewModelScope, WhileCollected, CharacterPageState.Loading)

  val closeRequests: SharedFlow<Unit> = closes.asSharedFlow()

  fun show(id: Uuid) {
    if (inputs.value?.id != id) inputs.value = PageInputs(id, attempt = 0)
  }

  fun retry() {
    inputs.update { it?.copy(attempt = it.attempt + 1) }
  }

  fun export(format: CardFormat, target: SaveTarget) {
    inputs.value?.let { exports.start(it.id, format, target) }
  }

  fun dismissExportNote() {
    inputs.value?.let { exports.runs.dismiss(it.id) }
  }

  fun pageShown(id: Uuid) = exports.runs.pageShown(id)

  fun pageHidden(id: Uuid) = exports.runs.pageHidden(id)

  fun askToDelete() {
    delete.value = DeleteProgress.Confirming
  }

  fun cancelDelete() {
    delete.value = DeleteProgress.Idle
  }

  fun confirmDelete() {
    val shown = state.value as? CharacterPageState.Shown ?: return
    delete.value = DeleteProgress.Deleting
    viewModelScope.launch {
      when (repository.delete(shown.character.id, ChatsOfDeletedCharacter.Keep)) {
        DeleteResult.Deleted, DeleteResult.Gone -> {
          messages.show(AppMessage(getString(Res.string.snack_deleted, shown.character.fields.name)))
          delete.value = DeleteProgress.Idle
          closes.emit(Unit)
        }
        DeleteResult.Failed -> delete.value = DeleteProgress.Failed
      }
    }
  }

  private fun characterOf(id: Uuid): Flow<Result<Character?>> =
    repository.character(id).map { Result.success(it) }.catch { emit(Result.failure(it)) }

  private fun pageState(
    character: Result<Character?>,
    export: ExportState?,
    delete: DeleteProgress,
  ): CharacterPageState = character.fold(
    onSuccess = { loaded ->
      loaded?.let { CharacterPageState.Shown(it, it.avatar?.let(assets::pathOf), export, delete) }
        ?: CharacterPageState.Gone
    },
    onFailure = { CharacterPageState.CouldNotLoad },
  )
}
