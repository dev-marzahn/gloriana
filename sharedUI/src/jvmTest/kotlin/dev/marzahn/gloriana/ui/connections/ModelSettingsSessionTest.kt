package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.ui.state.SaveFailure
import dev.marzahn.gloriana.ui.state.StoredValue
import dev.marzahn.gloriana.ui.state.asCell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ModelSettingsSessionTest {
  private val stored = Samplers(temperature = 0.5)
  private val session = MutableStateFlow(ModelSettingsSession().withStored(StoredValue.Found(stored)))
  private val saves = modelSettingsSaves(session.asCell())

  private fun savingTemperature(text: String): SamplerTexts {
    session.update { current ->
      current.withPage { it.copy(unsaved = it.unsaved.edited(SetSampler(Sampler.Temperature, text))) }
    }
    return checkNotNull(saves.start(ready = { _, unsaved -> unsaved.canSave }))
  }

  private fun read(value: StoredValue<Samplers>) = session.update { it.withStored(value) }

  private fun retrySucceeds(stored: Samplers) {
    read(StoredValue.Loading)
    read(StoredValue.Found(stored))
  }

  @Test
  fun aSaveThatFinishesWhileTheReadFailedEndsOnThePageShownAfterRetry() {
    val written = savingTemperature("0.7")
    read(StoredValue.CouldNotLoad)

    saves.saved(written)
    retrySucceeds(Samplers(temperature = 0.7))

    val shown = assertIs<ModelSettingsState.Shown>(session.value.state)
    assertFalse(shown.unsaved.isSaving)
    assertFalse(shown.unsaved.hasChanges)
    assertEquals("0.7", shown.unsaved.draft[Sampler.Temperature])
  }

  @Test
  fun aSaveThatFailsWhileTheReadFailedKeepsItsNoteAfterRetry() {
    savingTemperature("0.7")
    read(StoredValue.CouldNotLoad)

    saves.notSaved { it.withNote(SaveFailure.Storage) }
    retrySucceeds(stored)

    val shown = assertIs<ModelSettingsState.Shown>(session.value.state)
    assertFalse(shown.unsaved.isSaving)
    assertEquals(SaveFailure.Storage, shown.note)
    assertEquals("0.7", shown.unsaved.draft[Sampler.Temperature])
  }
}
