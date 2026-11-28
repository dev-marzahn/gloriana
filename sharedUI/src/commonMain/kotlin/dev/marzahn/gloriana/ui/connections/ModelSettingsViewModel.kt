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
import dev.marzahn.gloriana.ui.state.UnsavedCopy
import dev.marzahn.gloriana.ui.state.updateAndClaim
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    val canSave: Boolean get() = !unsaved.isSaving && unsaved.hasChanges
  }
}

class ModelSettingsViewModel(
  private val settings: GlobalModelSettings,
  private val messages: AppMessages,
) : ViewModel() {
  private val mutableState = MutableStateFlow<ModelSettingsState>(ModelSettingsState.Loading)
  private val focusChannel = Channel<Sampler>(Channel.BUFFERED)
  private var following: Job? = null

  val state: StateFlow<ModelSettingsState> = mutableState.asStateFlow()

  val focusRequests: Flow<Sampler> = focusChannel.receiveAsFlow()

  init {
    follow()
  }

  fun retry() {
    mutableState.value = ModelSettingsState.Loading
    follow()
  }

  fun edit(sampler: Sampler, text: String) {
    update {
      it.copy(unsaved = it.unsaved.edited(SetSampler(sampler, text)), errors = it.errors - sampler, note = null)
    }
  }

  fun discard() {
    update { it.copy(unsaved = it.unsaved.discarded(), errors = emptyMap(), note = null) }
  }

  fun save() {
    val claim = mutableState.updateAndClaim { current ->
      val shown = current as? ModelSettingsState.Shown
      val unsaved = shown?.unsaved?.takeIf { shown.canSave } ?: return@updateAndClaim current to null
      val errors = unsaved.draft.entryErrors()
      if (errors.isNotEmpty()) {
        current to SaveClaim.Refused(errors)
      } else {
        shown.copy(unsaved = unsaved.savingStarted(), note = null) to SaveClaim.Writing(unsaved.draft)
      }
    } ?: return
    when (claim) {
      is SaveClaim.Refused -> refuse(claim.errors)
      is SaveClaim.Writing -> viewModelScope.launch { write(claim.texts) }
    }
  }

  private suspend fun write(texts: SamplerTexts) {
    val samplers = texts.toSamplers()
    when (val result = settings.setSamplers(samplers)) {
      GlobalSamplersSave.Saved -> {
        update { it.copy(unsaved = it.unsaved.saved(SamplerTexts.of(samplers))) }
        messages.show(AppMessage(getString(Res.string.snack_model_settings_saved)))
      }
      is GlobalSamplersSave.Refused -> {
        update { it.copy(unsaved = it.unsaved.notSaved()) }
        refuse(outsideEveryFormat(result, samplers))
      }
      GlobalSamplersSave.StorageFull -> notSaved(SaveFailure.Storage)
      GlobalSamplersSave.Failed -> notSaved(SaveFailure.Other)
    }
  }

  private fun notSaved(failure: SaveFailure) = update { it.copy(unsaved = it.unsaved.notSaved(), note = failure) }

  private fun refuse(errors: Map<Sampler, SamplerError>) {
    update { it.copy(errors = errors) }
    Sampler.entries.firstOrNull { it in errors }?.let { focusChannel.trySend(it) }
  }

  private fun follow() {
    following?.cancel()
    following = viewModelScope.launch {
      settings.samplers()
        .retryWhen { _, attempt ->
          val shown = mutableState.value is ModelSettingsState.Shown
          if (shown) delay(followRetryDelay(attempt))
          shown
        }
        .catch { mutableState.value = ModelSettingsState.CouldNotLoad }
        .collect { samplers ->
          val stored = SamplerTexts.of(samplers)
          mutableState.update { current ->
            when (current) {
              is ModelSettingsState.Shown -> current.copy(unsaved = current.unsaved.storedNow(stored))
              ModelSettingsState.Loading, ModelSettingsState.CouldNotLoad ->
                ModelSettingsState.Shown(UnsavedCopy.of(stored, SamplerTexts::sameAs))
            }
          }
        }
    }
  }

  private fun update(change: (ModelSettingsState.Shown) -> ModelSettingsState.Shown) {
    mutableState.update { current -> (current as? ModelSettingsState.Shown)?.let(change) ?: current }
  }
}

private sealed interface SaveClaim {
  class Refused(val errors: Map<Sampler, SamplerError>) : SaveClaim

  class Writing(val texts: SamplerTexts) : SaveClaim
}

private fun outsideEveryFormat(result: GlobalSamplersSave.Refused, samplers: Samplers): Map<Sampler, SamplerError> =
  result.outsideEveryFormat.mapNotNull { sampler ->
    val value = sampler.valueIn(samplers) ?: return@mapNotNull null
    val widest = sampler.widestRange() ?: return@mapNotNull null
    sampler to SamplerError.NoFormatTakes(value, widest)
  }.toMap()

private val FirstRetry = 1.seconds
private val LongestRetry = 30.seconds
private const val DOUBLINGS_TO_THE_LONGEST = 5L

private fun followRetryDelay(attempt: Long): Duration =
  minOf(FirstRetry * (1 shl attempt.coerceAtMost(DOUBLINGS_TO_THE_LONGEST).toInt()), LongestRetry)
