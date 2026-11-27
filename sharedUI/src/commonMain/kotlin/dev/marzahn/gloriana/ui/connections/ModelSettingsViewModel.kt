package dev.marzahn.gloriana.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.GlobalSamplersSave
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_model_settings_saved
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

enum class ModelSettingsNote { NotSavedStorage, NotSaved }

sealed interface ModelSettingsState {
  data object Loading : ModelSettingsState

  data object CouldNotLoad : ModelSettingsState

  data class Shown(
    val copy: EditedCopy<SamplerTexts>,
    val errors: Map<Sampler, SamplerError> = emptyMap(),
    val saving: Boolean = false,
    val note: ModelSettingsNote? = null,
  ) : ModelSettingsState
}

class ModelSettingsViewModel(
  private val settings: GlobalModelSettings,
  private val messages: AppMessages,
) : ViewModel() {
  private val mutableState = MutableStateFlow<ModelSettingsState>(ModelSettingsState.Loading)
  private val focusChannel = Channel<Sampler>(Channel.BUFFERED)

  val state: StateFlow<ModelSettingsState> = mutableState.asStateFlow()

  val focusRequests: Flow<Sampler> = focusChannel.receiveAsFlow()

  init {
    load()
  }

  fun retry() {
    mutableState.value = ModelSettingsState.Loading
    load()
  }

  fun edit(sampler: Sampler, text: String) {
    update {
      it.copy(copy = it.copy.edited { texts -> texts.with(sampler, text) }, errors = it.errors - sampler, note = null)
    }
  }

  fun discard() {
    update { it.copy(copy = it.copy.discarded(), errors = emptyMap(), note = null) }
  }

  fun save() {
    val shown = (mutableState.value as? ModelSettingsState.Shown)?.takeUnless { it.saving } ?: return
    val draft = shown.copy.draft
    val entryErrors = draft.entryErrors()
    if (entryErrors.isNotEmpty()) {
      refuse(entryErrors)
      return
    }
    update { it.copy(saving = true, note = null) }
    viewModelScope.launch {
      val samplers = draft.toSamplers()
      when (val result = settings.setSamplers(samplers)) {
        GlobalSamplersSave.Saved -> {
          update { it.copy(copy = EditedCopy.of(SamplerTexts.of(samplers)), saving = false) }
          messages.show(AppMessage(getString(Res.string.snack_model_settings_saved)))
        }
        is GlobalSamplersSave.Refused -> refuse(
          result.outsideEveryFormat.mapNotNull { sampler ->
            val value = sampler.valueIn(samplers) ?: return@mapNotNull null
            val widest = sampler.widestRange() ?: return@mapNotNull null
            sampler to SamplerError.NoFormatTakes(value, widest)
          }.toMap(),
        )
        GlobalSamplersSave.StorageFull -> update { it.copy(saving = false, note = ModelSettingsNote.NotSavedStorage) }
        GlobalSamplersSave.Failed -> update { it.copy(saving = false, note = ModelSettingsNote.NotSaved) }
      }
    }
  }

  private fun refuse(errors: Map<Sampler, SamplerError>) {
    update { it.copy(saving = false, errors = errors) }
    Sampler.entries.firstOrNull { it in errors }?.let { focusChannel.trySend(it) }
  }

  private fun load() {
    viewModelScope.launch {
      mutableState.value = settings.samplers()
        .map<_, ModelSettingsState> { ModelSettingsState.Shown(EditedCopy.of(SamplerTexts.of(it))) }
        .catch { emit(ModelSettingsState.CouldNotLoad) }
        .first()
    }
  }

  private fun update(change: (ModelSettingsState.Shown) -> ModelSettingsState.Shown) {
    mutableState.update { current -> (current as? ModelSettingsState.Shown)?.let(change) ?: current }
  }
}
