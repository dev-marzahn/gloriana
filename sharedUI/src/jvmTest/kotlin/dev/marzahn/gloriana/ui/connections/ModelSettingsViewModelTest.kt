package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 10_000L

@OptIn(ExperimentalCoroutinesApi::class)
class ModelSettingsViewModelTest {
  @BeforeTest
  fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

  @AfterTest
  fun reset() = Dispatchers.resetMain()

  private suspend fun ModelSettingsViewModel.shownWhen(holds: (ModelSettingsState.Shown) -> Boolean) =
    withTimeout(WAIT_MILLIS) { state.first { it is ModelSettingsState.Shown && holds(it) } }
      .let { assertIs<ModelSettingsState.Shown>(it) }

  @Test
  fun anOpenPageFollowsAWriteFromElsewhereUnderItsEdits() = LiveLibrary.use { library ->
    runBlocking {
      val page = library.koin.get<ModelSettingsViewModel>()
      page.shownWhen { true }
      page.edit(Sampler.TopK, "40")

      library.koin.get<GlobalModelSettings>().setSamplers(Samplers(temperature = 0.3))

      val followed = page.shownWhen { it.unsaved.stored[Sampler.Temperature] == "0.3" }
      assertEquals("0.3", followed.unsaved.draft[Sampler.Temperature])
      assertEquals("40", followed.unsaved.draft[Sampler.TopK])
      assertTrue(followed.unsaved.hasChanges)
    }
  }

  @Test
  fun aSaveLeavesNothingToSave() = LiveLibrary.use { library ->
    runBlocking {
      val page = library.koin.get<ModelSettingsViewModel>()
      page.shownWhen { true }
      page.edit(Sampler.Temperature, "0.70")

      page.save()

      val saved = page.shownWhen { !it.unsaved.isSaving && it.unsaved.stored[Sampler.Temperature] == "0.7" }
      assertFalse(saved.unsaved.hasChanges)
      assertEquals(0.7, library.globalSamplers().temperature)
    }
  }
}
