package dev.marzahn.gloriana.ui.connections

import androidx.room.execSQL
import androidx.room.useWriterConnection
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.HeaderValueInput
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.SealedSecret
import dev.marzahn.gloriana.logic.keys.SecretSlot
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_saved
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import dev.marzahn.gloriana.ui.testing.NoKeychain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
import org.jetbrains.compose.resources.getString
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 10_000L
private const val QUIET_MILLIS = 500L
private const val POLL_MILLIS = 10L

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
  @BeforeTest
  fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

  @AfterTest
  fun reset() = Dispatchers.resetMain()

  private suspend fun CoroutineScope.shownPage(
    library: LiveLibrary,
    name: String,
    headers: List<HeaderDraft> = emptyList(),
  ): ConnectionViewModel {
    val saved = library.created(name, headers = headers)
    val page = library.koin.get<ConnectionViewModel>()
    launch(Dispatchers.Main) { page.state.collect {} }
    page.show(ConnectionRoute.Saved(saved.id))
    withTimeout(WAIT_MILLIS) { page.state.first { it is ConnectionPageState.Shown } }
    return page
  }

  private suspend fun LiveLibrary.writeHeld() =
    withTimeout(WAIT_MILLIS) { while (writes.writesCaught == 0) delay(POLL_MILLIS) }

  @Test
  fun aSamplerTheNewFormatDoesNotSendIsKeptInStorageForASwitchBack() = LiveLibrary.use { library ->
    runBlocking {
      val page = shownPage(library, "Work")
      val seed = FormField.SamplerField(Sampler.Seed)
      page.on(PageIntent.Edit(seed) { it.copy(samplers = it.samplers.with(Sampler.Seed, "7")) })
      page.on(PageIntent.Save)
      page.settled { it.saved?.fields?.samplers?.seed == 7L }

      page.on(PageIntent.Edit(FormField.Format) { it.copy(format = ApiFormat.OpenAiResponses) })
      page.on(PageIntent.Save)

      val switched = page.settled { it.saved?.fields?.format == ApiFormat.OpenAiResponses }
      assertEquals(7L, switched.saved?.fields?.samplers?.seed)
      assertEquals(7L, library.connections().single().fields.samplers.seed)
      coroutineContext.cancelChildren()
    }
  }

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

  private suspend fun ConnectionViewModel.settled(saved: (ConnectionPageState.Shown) -> Boolean) =
    withTimeout(WAIT_MILLIS) {
      state.first { it is ConnectionPageState.Shown && it.isIdle && saved(it) }
    }.let { assertIs<ConnectionPageState.Shown>(it) }

  @Test
  fun anEditMadeWhileTheSaveIsHeldStaysAsADraftOverTheSavedConnection() = LiveLibrary.use { library ->
    runBlocking {
      val page = shownPage(library, "Work")
      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Home") })

      library.writes.hold()
      page.on(PageIntent.Save)
      library.writeHeld()
      page.on(PageIntent.Edit(FormField.Model) { it.copy(model = "model-late") })
      val whileHeld = assertIs<ConnectionPageState.Shown>(page.state.value)
      assertTrue(whileHeld.unsaved.isSaving)
      assertTrue(whileHeld.canEdit)
      assertFalse(whileHeld.canSave)
      assertEquals("model-late", whileHeld.form.model)
      library.writes.release()

      val settled = page.settled { it.saved?.fields?.name == "Home" }
      assertEquals("model-1", settled.saved?.fields?.model)
      assertEquals("Home", settled.form.name)
      assertEquals("model-late", settled.form.model)
      assertTrue(settled.unsaved.hasChanges)
      assertTrue(settled.canSave)
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun anEqualRouteShownAgainWithoutTheEditsKeepsTheReplay() = LiveLibrary.use { library ->
    runBlocking {
      val saved = library.created("Work")
      val page = library.koin.get<ConnectionViewModel>()
      launch(Dispatchers.Main) { page.state.collect {} }
      val late = PageIntent.Edit(FormField.Model) { it.copy(model = "model-late") }
      page.show(ConnectionRoute.Saved(saved.id, editsDuringItsFirstSave = listOf(late)))
      page.settled { it.form.model == "model-late" }

      page.show(ConnectionRoute.Saved(saved.id))

      val shown = page.settled { it.saved != null }
      assertEquals("model-late", shown.form.model)
      assertTrue(shown.unsaved.hasChanges)
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun aDiscardWhileTheSaveIsHeldDropsOnlyTheEditsMadeBeforeIt() = LiveLibrary.use { library ->
    runBlocking {
      val page = shownPage(library, "Work")
      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Home") })

      library.writes.hold()
      page.on(PageIntent.Save)
      library.writeHeld()
      page.on(PageIntent.Edit(FormField.Model) { it.copy(model = "model-late") })
      page.on(PageIntent.Discard)
      page.on(PageIntent.Edit(FormField.Model) { it.copy(model = "model-later") })
      library.writes.release()

      val settled = page.settled { it.saved?.fields?.name == "Home" }
      assertEquals("Home", settled.form.name)
      assertEquals("model-later", settled.form.model)
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun aPageSavedWithNoEditDuringTheSaveFollowsLaterChangesToTheConnection() = LiveLibrary.use { library ->
    runBlocking {
      val page = shownPage(library, "Work")
      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Home") })
      page.on(PageIntent.Save)
      val saved = page.settled { it.saved?.fields?.name == "Home" }

      val id = checkNotNull(saved.saved).id
      library.koin.get<ConnectionRepository>().updateSamplers(id) { Samplers(temperature = 0.3) }

      val followed = page.settled { it.saved?.fields?.samplers?.temperature == 0.3 }
      assertTrue(followed.form.sameAs(ConnectionForm.of(checkNotNull(followed.saved))))
      assertFalse(followed.unsaved.hasChanges)
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun anEditMadeWhileThePassphrasePromptHoldsTheSaveIsSaved() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = shownPage(library, "Work")
      page.on(PageIntent.Edit(FormField.Key) { it.copy(key = KeyEdit.typing(SecretText("sk-made-up"))) })
      page.on(PageIntent.Save)
      withTimeout(WAIT_MILLIS) {
        page.state.first { (it as? ConnectionPageState.Shown)?.activity is PageActivity.SettingPassphrase }
      }

      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Home") })
      page.on(PageIntent.SetPassphrase(PassphraseIntent.Type(SecretText("passphrase-1"))))
      page.on(PageIntent.SetPassphrase(PassphraseIntent.TypeAgain(SecretText("passphrase-1"))))
      page.on(PageIntent.SetPassphrase(PassphraseIntent.Submit))

      val settled = page.settled { it.saved?.fields?.name == "Home" }
      assertTrue(checkNotNull(settled.saved).key != null)
      assertFalse(settled.unsaved.hasChanges)
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun aSaveWithNoEditWhileHeldLeavesNothingToSave() = LiveLibrary.use { library ->
    runBlocking {
      val page = shownPage(library, "Work")
      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Home") })

      page.on(PageIntent.Save)

      val settled = page.settled { it.saved?.fields?.name == "Home" }
      assertEquals("Home", settled.form.name)
      assertFalse(settled.unsaved.hasChanges)
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun aRetryAfterAFailedReadChecksTheConnectionsKeyAgain() = LiveLibrary.use { library ->
    runBlocking {
      val saved = library.created("Work", key = KeyInput.Entered("sk-1"))
      val vault = library.koin.get<KeyVault>()
      val opened = CopyOnWriteArrayList<SecretSlot>()
      val countingVault = object : KeyVault by vault {
        override suspend fun open(slot: SecretSlot, secret: SealedSecret) =
          vault.open(slot, secret).also { opened += slot }
      }
      val database = library.koin.get<GlorianaDatabase>()
      database.useWriterConnection { it.execSQL("ALTER TABLE connections RENAME TO connections_away") }
      val page = with(library.koin) { ConnectionViewModel(get(), get(), countingVault, get(), get(), get()) }
      launch(Dispatchers.Main) { page.state.collect {} }
      page.show(ConnectionRoute.Saved(saved.id))
      withTimeout(WAIT_MILLIS) { page.state.first { it is ConnectionPageState.CouldNotLoad } }
      database.useWriterConnection { it.execSQL("ALTER TABLE connections_away RENAME TO connections") }

      page.on(PageIntent.Retry)

      withTimeout(WAIT_MILLIS) { page.state.first { it is ConnectionPageState.Shown } }
      withTimeout(WAIT_MILLIS) { while (SecretSlot.ApiKey(saved.id) !in opened) delay(POLL_MILLIS) }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun aSavedNameTheStoreTrimsBackToTheStoredOneShowsTheStoredName() = LiveLibrary.use { library ->
    runBlocking {
      val page = shownPage(library, "Work")
      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Work ") })

      page.on(PageIntent.Save)
      withTimeout(WAIT_MILLIS) { library.koin.get<AppMessages>().messages.first() }

      val settled = page.settled { true }
      assertEquals("Work", settled.form.name)
      assertEquals("Work", settled.unsaved.baseline.name)
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun anEditMadeWhileARenameIsSavedReachesTheRenamedHeader() = LiveLibrary.use { library ->
    runBlocking {
      val page = shownPage(library, "Work", listOf(HeaderDraft("X-Old", HeaderValueInput.Entered("value-1"))))
      val row = assertIs<ConnectionPageState.Shown>(page.state.value).form.headers.single()
      val rename = { name: String -> PageIntent.Edit(FormField.HeaderName(row.id)) { form ->
        form.withHeaderChanged(row.id) { it.copy(name = name) }
      } }
      page.on(rename("X-New"))

      library.writes.hold()
      page.on(PageIntent.Save)
      library.writeHeld()
      page.on(rename("X-Newer"))
      library.writes.release()

      val settled = page.settled { it.saved?.headers?.singleOrNull()?.name == "X-New" }
      assertEquals(listOf("X-Newer"), settled.form.headers.map { it.name })
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun aSaveSaysTheConnectionWasSavedOnceItLeavesSaving() = LiveLibrary.use { library ->
    runBlocking {
      val page = shownPage(library, "Work")
      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Home") })

      page.on(PageIntent.Save)

      val message = withTimeout(WAIT_MILLIS) { library.koin.get<AppMessages>().messages.first() }
      assertEquals(getString(Res.string.snack_saved, "Home"), message.text)
      assertFalse(assertIs<ConnectionPageState.Shown>(page.state.value).unsaved.isSaving)
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun anEditMadeWhileANewConnectionsFirstSaveIsHeldReachesItsSavedPage() = LiveLibrary.use { library ->
    runBlocking {
      val creating = library.koin.get<ConnectionViewModel>()
      launch(Dispatchers.Main) { creating.state.collect {} }
      creating.show(ConnectionRoute.New(NewConnectionStart.FromNothing, Opening.fresh()))
      withTimeout(WAIT_MILLIS) { creating.state.first { it is ConnectionPageState.Shown } }
      creating.on(
        PageIntent.Edit(FormField.Name) {
          it.copy(
            name = "Work",
            format = ApiFormat.OpenAiChatCompletions,
            baseUrl = "https://api.example.com/v1",
            model = "model-1",
          )
        },
      )
      val opened = async { creating.events.first { it is PageEvent.Saved } }

      library.writes.hold()
      creating.on(PageIntent.Save)
      library.writeHeld()
      creating.on(PageIntent.Edit(FormField.Model) { it.copy(model = "model-late") })
      library.writes.release()
      val route = assertIs<PageEvent.Saved>(opened.await()).route
      val opening = assertIs<ConnectionPageState.Shown>(creating.state.value)
      assertEquals(PageActivity.OpeningSaved, opening.activity)
      assertFalse(opening.canEdit)
      creating.on(PageIntent.Edit(FormField.Model) { it.copy(model = "model-after") })

      val savedPage = library.koin.get<ConnectionViewModel>()
      launch(Dispatchers.Main) { savedPage.state.collect {} }
      savedPage.show(route)
      val shown = savedPage.settled { it.saved != null }
      assertEquals("model-1", shown.saved?.fields?.model)
      assertEquals("model-late", shown.form.model)
      assertEquals("Work", shown.form.name)
      assertTrue(shown.unsaved.hasChanges)
      coroutineContext.cancelChildren()
    }
  }
}
