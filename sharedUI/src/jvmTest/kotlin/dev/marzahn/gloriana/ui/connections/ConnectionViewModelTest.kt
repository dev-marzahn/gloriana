package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.ui.messages.AppMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

private const val WAIT_MILLIS = 10_000L
private const val QUIET_MILLIS = 500L
private const val POLL_MILLIS = 10L

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
  @BeforeTest
  fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

  @AfterTest
  fun reset() = Dispatchers.resetMain()

  private suspend fun CoroutineScope.shownPage(library: LiveLibrary, name: String): ConnectionViewModel {
    val saved = library.created(name)
    val page = library.koin.get<ConnectionViewModel>()
    launch(Dispatchers.Main) { page.state.collect {} }
    page.show(ConnectionRoute.Saved(saved.id))
    withTimeout(WAIT_MILLIS) { page.state.first { it is ConnectionPageState.Shown } }
    return page
  }

  private suspend fun LiveLibrary.writeHeld() =
    withTimeout(WAIT_MILLIS) { while (writes.writesCaught == 0) delay(POLL_MILLIS) }

  @Test
  fun aDoubleTapOnDeleteDeletesOnce() = LiveLibrary.use { library ->
    runBlocking {
      val page = shownPage(library, "Work")

      library.writes.hold()
      page.on(PageIntent.AskToDelete)
      page.on(PageIntent.ConfirmDelete)
      page.on(PageIntent.ConfirmDelete)
      library.writeHeld()
      library.writes.release()

      val messages = library.koin.get<AppMessages>().messages
      withTimeout(WAIT_MILLIS) { messages.first() }
      assertNull(withTimeoutOrNull(QUIET_MILLIS) { messages.first() })
      assertEquals(emptyList(), library.connections())
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun anEditSentWhileTheSaveIsHeldIsRefusedAndTheSavedFormIsShown() = LiveLibrary.use { library ->
    runBlocking {
      val page = shownPage(library, "Work")
      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Home") })

      library.writes.hold()
      page.on(PageIntent.Save)
      library.writeHeld()
      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Late") })
      page.on(PageIntent.Discard)
      val whileHeld = assertIs<ConnectionPageState.Shown>(page.state.value)
      assertEquals(PageActivity.Saving, whileHeld.activity)
      assertEquals("Home", whileHeld.form.name)
      library.writes.release()

      val settled = withTimeout(WAIT_MILLIS) {
        page.state.first { state ->
          state is ConnectionPageState.Shown &&
            state.activity == PageActivity.Idle &&
            state.saved?.fields?.name == "Home"
        }
      }
      assertIs<ConnectionPageState.Shown>(settled)
      assertEquals("Home", settled.form.name)
      assertFalse(settled.copy.hasChanges)
      coroutineContext.cancelChildren()
    }
  }
}
