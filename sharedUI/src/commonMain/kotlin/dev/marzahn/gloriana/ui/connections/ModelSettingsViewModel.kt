package dev.marzahn.gloriana.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.GlobalSamplersSave
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_model_settings_saved
import dev.marzahn.gloriana.ui.state.DraftEdit
import dev.marzahn.gloriana.ui.state.SaveFailure
import dev.marzahn.gloriana.ui.state.StateCell
import dev.marzahn.gloriana.ui.state.StoredReads
import dev.marzahn.gloriana.ui.state.StoredValue
import dev.marzahn.gloriana.ui.state.UnsavedCopy
import dev.marzahn.gloriana.ui.state.UnsavedSaves
import dev.marzahn.gloriana.ui.state.WhileCollected
import dev.marzahn.gloriana.ui.state.alongside
import dev.marzahn.gloriana.ui.state.asCell
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class SetSampler(val sampler: Sampler, val text: String) : DraftEdit<SamplerTexts> {
  override fun applied(to: SamplerTexts) = to.with(sampler, text)

  override fun replaces(earlier: DraftEdit<SamplerTexts>) = earlier is SetSampler && earlier.sampler == sampler
}

sealed interface ModelSettingsState {
  data object Loading : ModelSettingsState

  data object CouldNotLoad : ModelSettingsState

  data class Shown(
    val unsaved: UnsavedCopy<SamplerTexts>,
    val errors: Map<Sampler, SamplerError> = emptyMap(),
    val note: SaveFailure? = null,
  ) : ModelSettingsState {
    val canSave: Boolean get() = unsaved.canSave
  }
}

internal enum class SettingsRead { Loading, Failed, Read }

internal data class ModelSettingsSession(
  val read: SettingsRead = SettingsRead.Loading,
  val page: ModelSettingsState.Shown? = null,
) {
  val state: ModelSettingsState
    get() = when (read) {
      SettingsRead.Loading -> ModelSettingsState.Loading
      SettingsRead.Failed -> ModelSettingsState.CouldNotLoad
      SettingsRead.Read -> page ?: ModelSettingsState.Loading
    }

  fun withStored(stored: StoredValue<Samplers>): ModelSettingsSession = when (stored) {
    StoredValue.Loading -> copy(read = SettingsRead.Loading)
    StoredValue.CouldNotLoad -> copy(read = SettingsRead.Failed)
    is StoredValue.Found -> {
      val texts = SamplerTexts.of(stored.value)
      val shown = page ?: ModelSettingsState.Shown(UnsavedCopy.of(texts, SamplerTexts::sameAs))
      ModelSettingsSession(SettingsRead.Read, shown.copy(unsaved = shown.unsaved.storedNow(texts)))
    }
  }

  fun withPage(change: (ModelSettingsState.Shown) -> ModelSettingsState.Shown) = copy(page = page?.let(change))

  fun withNote(note: SaveFailure?) = withPage { it.copy(note = note) }
}

internal fun modelSettingsSaves(cell: StateCell<ModelSettingsSession>) =
  UnsavedSaves(cell, { it.page?.unsaved }) { current, unsaved -> current.withPage { it.copy(unsaved = unsaved) } }

class ModelSettingsViewModel(
  private val settings: GlobalModelSettings,
  private val messages: AppMessages,
) : ViewModel() {
  private val session = MutableStateFlow(ModelSettingsSession())
  private val reads = StoredReads.values<Samplers>()
  private val focusChannel = Channel<Sampler>(Channel.BUFFERED)
  private val saves = modelSettingsSaves(session.asCell())

  private val following = reads.follow(key = Unit) { settings.samplers() }
    .onEach { read -> session.update { it.withStored(read) } }

  val state: StateFlow<ModelSettingsState> = session.map { it.state }
    .alongside(following)
    .stateIn(viewModelScope, WhileCollected, ModelSettingsState.Loading)

  val focusRequests: Flow<Sampler> = focusChannel.receiveAsFlow()

  fun retry() = reads.retry()

  fun edit(sampler: Sampler, text: String) = update { shown ->
    shown.copy(unsaved = shown.unsaved.edited(SetSampler(sampler, text)), errors = shown.errors - sampler, note = null)
  }

  fun discard() = update { it.copy(unsaved = it.unsaved.discarded(), errors = emptyMap(), note = null) }

  fun save() {
    val texts = (session.value.state as? ModelSettingsState.Shown)?.takeIf { it.canSave }?.unsaved?.draft ?: return
    val errors = texts.entryErrors()
    when {
      errors.isNotEmpty() -> refuse(errors)
      saves.start(ready = { _, unsaved -> unsaved.draft === texts }, started = { it.withNote(null) }) != null ->
        viewModelScope.launch { write(texts) }
    }
  }

  private fun update(change: (ModelSettingsState.Shown) -> ModelSettingsState.Shown) =
    session.update { it.withPage(change) }

  private suspend fun write(texts: SamplerTexts) {
    val samplers = texts.toSamplers()
    when (val result = settings.setSamplers(samplers)) {
      GlobalSamplersSave.Saved -> {
        saves.saved(SamplerTexts.of(samplers))
        messages.show(AppMessage(getString(Res.string.snack_model_settings_saved)))
      }
      is GlobalSamplersSave.Refused -> {
        saves.notSaved()
        refuse(outsideEveryFormat(result, samplers))
      }
      GlobalSamplersSave.StorageFull -> notSaved(SaveFailure.Storage)
      GlobalSamplersSave.Failed -> notSaved(SaveFailure.Other)
    }
  }

  private fun notSaved(failure: SaveFailure) = saves.notSaved { it.withNote(failure) }

  private fun refuse(errors: Map<Sampler, SamplerError>) {
    update { it.copy(errors = errors) }
    Sampler.entries.firstOrNull { it in errors }?.let { focusChannel.trySend(it) }
  }
}

private fun outsideEveryFormat(result: GlobalSamplersSave.Refused, samplers: Samplers): Map<Sampler, SamplerError> =
  result.outsideEveryFormat.mapNotNull { sampler ->
    val value = sampler.valueIn(samplers) ?: return@mapNotNull null
    val widest = sampler.widestRange() ?: return@mapNotNull null
    sampler to SamplerError.NoFormatTakes(value, widest)
  }.toMap()
