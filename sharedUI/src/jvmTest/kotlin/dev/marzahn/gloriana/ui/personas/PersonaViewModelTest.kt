package dev.marzahn.gloriana.ui.personas

import dev.marzahn.gloriana.logic.storage.UuidV7IdFactory
import dev.marzahn.gloriana.ui.navigation.Opening
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 10_000L

@OptIn(ExperimentalCoroutinesApi::class)
class PersonaViewModelTest {
  @BeforeTest
  fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

  @AfterTest
  fun reset() = Dispatchers.resetMain()

  @Test
  fun aNewPersonaOnceSavedShowsItsStoredPageAndNeverAnEmptyUnsavedOne() = LiveLibrary.use { library ->
    runBlocking {
      val route = PersonaRoute.New(Opening.fresh(UuidV7IdFactory))
      val page = library.koin.get<PersonaViewModel>()
      val seen = CopyOnWriteArrayList<PersonaPageState>()
      launch(Dispatchers.Main) { page.state.collect { seen += it } }
      page.show(route)
      page.on(PersonaIntent.SetName("Wren"))
      page.on(PersonaIntent.Save)

      val saved = withTimeout(WAIT_MILLIS) {
        page.state.first { it is PersonaPageState.Shown && it.stored != null }
      } as PersonaPageState.Shown

      assertEquals(PersonaRoute.Saved(route.opening.id), saved.route)
      assertEquals("Wren", saved.fields.name)
      assertTrue(
        seen.filterIsInstance<PersonaPageState.Shown>().none { it.route is PersonaRoute.New && !it.isEditing },
      )
      coroutineContext.cancelChildren()
    }
  }
}
