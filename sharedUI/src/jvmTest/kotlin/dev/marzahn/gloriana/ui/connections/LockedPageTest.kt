package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.HeaderValueInput
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.SecretsState
import kotlinx.coroutines.CoroutineScope
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val WAIT_MILLIS = 20_000L
private const val PASSPHRASE = "correct horse"
private const val HEADER = "X-Token"

@OptIn(ExperimentalCoroutinesApi::class)
class LockedPageTest {
  @BeforeTest
  fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

  @AfterTest
  fun reset() = Dispatchers.resetMain()

  private suspend fun CoroutineScope.lockedPage(
    library: LiveLibrary,
    damaged: Boolean = false,
    key: KeyInput = KeyInput.Entered("sk-made-up"),
  ): ConnectionViewModel {
    library.protectWithPassphrase(PASSPHRASE)
    val saved = library.created(
      "Work",
      key,
      listOf(HeaderDraft(HEADER, HeaderValueInput.Entered("token"))),
      baseUrl = "http://127.0.0.1:1/v1",
    )
    if (damaged) library.damagePassphraseKeyset()
    library.restart()
    library.needKeys()
    val page = library.koin.get<ConnectionViewModel>()
    launch(Dispatchers.Main) { page.state.collect {} }
    page.show(ConnectionRoute.Saved(saved.id))
    return page
  }

  private suspend fun ConnectionViewModel.shown(matches: (ConnectionPageState.Shown) -> Boolean) =
    withTimeout(WAIT_MILLIS) { state.first { it is ConnectionPageState.Shown && matches(it) } }
      .let { assertIs<ConnectionPageState.Shown>(it) }

  private suspend fun ConnectionViewModel.unlocksWhenAsked(): AfterUnlock {
    val asking = assertIs<PageActivity.Unlocking>(shown { it.activity is PageActivity.Unlocking }.activity)
    on(PageIntent.Passphrase(PassphraseIntent.Type(SecretText(PASSPHRASE))))
    on(PageIntent.Passphrase(PassphraseIntent.Submit))
    return asking.then
  }

  private fun ConnectionPageState.Shown.header() = form.headers.single { it.storedName == HEADER }

  @Test
  fun theKeyRowsUnlockOpensTheKeys() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library)
      page.shown { it.secrets is SecretsState.Locked }

      page.on(PageIntent.Unlock)
      assertEquals(AfterUnlock.OnlyUnlock, page.unlocksWhenAsked())

      page.shown { it.secrets == SecretsState.Usable && it.activity == PageActivity.Idle }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun testAsksForThePassphraseThenTests() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library)
      page.shown { it.secrets is SecretsState.Locked }

      page.on(PageIntent.Test)
      assertEquals(AfterUnlock.Test, page.unlocksWhenAsked())

      page.shown { it.test != null && it.activity == PageActivity.Idle }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun pickAModelAsksForThePassphraseThenAsksTheHost() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library)
      page.shown { it.secrets is SecretsState.Locked }

      page.on(PageIntent.OpenModelList)
      assertEquals(AfterUnlock.ModelList, page.unlocksWhenAsked())

      page.shown { (it.activity as? PageActivity.PickingModel)?.list is ModelList.Failed }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun enteringAHeaderValueAsksForThePassphraseThenOpensTheField() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library)
      val row = page.shown { it.secrets is SecretsState.Locked }.header()
      val enter: FormChange = {
        it.withHeaderChanged(row.id) { header -> header.copy(value = ValueEdit.Typed(SecretText.Empty)) }
      }

      page.on(PageIntent.Edit(FormField.HeaderValue(row.id), enter))
      assertEquals(ValueEdit.Kept, page.shown { it.activity is PageActivity.Unlocking }.header().value)
      assertEquals(FormField.HeaderValue(row.id), assertIs<AfterUnlock.Edit>(page.unlocksWhenAsked()).field)

      page.shown { it.activity == PageActivity.Idle && it.header().value == ValueEdit.Typed(SecretText.Empty) }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun renamingASavedHeaderAsksForThePassphraseThenRenames() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library)
      val row = page.shown { it.secrets is SecretsState.Locked }.header()
      val rename: FormChange = { it.withHeaderChanged(row.id) { header -> header.copy(name = "X-Key") } }

      page.on(PageIntent.Edit(FormField.HeaderName(row.id), rename))
      assertEquals(FormField.HeaderName(row.id), assertIs<AfterUnlock.Edit>(page.unlocksWhenAsked()).field)

      page.shown { it.activity == PageActivity.Idle && it.header().name == "X-Key" }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun typingIntoANewHeadersValueAsksForThePassphraseFirst() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library)
      page.shown { it.secrets is SecretsState.Locked }
      page.on(PageIntent.Toggle { it.withHeader() })
      val added = page.shown { shown -> shown.form.headers.any { it.storedName == null } }
        .form.headers.single { it.storedName == null }
      val typed: FormChange = {
        it.withHeaderChanged(added.id) { header -> header.copy(value = ValueEdit.Typed(SecretText("v"))) }
      }

      page.on(PageIntent.Edit(FormField.HeaderValue(added.id), typed))
      assertIs<AfterUnlock.Edit>(page.unlocksWhenAsked())

      page.shown { shown ->
        shown.activity == PageActivity.Idle &&
          shown.form.headers.single { it.id == added.id }.value == ValueEdit.Typed(SecretText("v"))
      }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun typingAKeyIntoALockedConnectionWithOnlyHeaderValuesAsksFirst() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library, key = KeyInput.Kept)
      page.shown { it.secrets is SecretsState.Locked }

      page.on(PageIntent.Edit(FormField.Key) { it.copy(key = KeyEdit.Typed(SecretText("s"))) })
      assertEquals(FormField.Key, assertIs<AfterUnlock.Edit>(page.unlocksWhenAsked()).field)

      page.shown { it.activity == PageActivity.Idle && it.form.key == KeyEdit.Typed(SecretText("s")) }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun editsThatTouchNoStoredValueDoNotAsk() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library)
      val row = page.shown { it.secrets is SecretsState.Locked }.header()

      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Home") })
      page.on(PageIntent.Toggle { it.withoutHeader(row.id) })

      val edited = page.shown { it.form.name == "Home" && it.form.headers.isEmpty() }
      assertEquals(PageActivity.Idle, edited.activity)
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun anOpenVaultEditsAHeaderWithoutAsking() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library)
      page.shown { it.secrets is SecretsState.Locked }
      library.unlocks(PASSPHRASE)
      val row = page.shown { it.secrets == SecretsState.Usable }.header()

      page.on(
        PageIntent.Edit(FormField.HeaderName(row.id)) {
          it.withHeaderChanged(row.id) { header -> header.copy(name = "X-Key") }
        },
      )

      page.shown { it.activity == PageActivity.Idle && it.header().name == "X-Key" }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun aSaveThatStoresNoKeyOrHeaderValueSavesWithoutAsking() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library)
      page.shown { it.secrets is SecretsState.Locked }

      page.on(PageIntent.Edit(FormField.Name) { it.copy(name = "Home") })
      page.on(PageIntent.Edit(FormField.Model) { it.copy(model = "model-2") })
      page.on(PageIntent.Save)

      val saved = page.shown { it.saved?.fields?.name == "Home" && it.activity == PageActivity.Idle }
      assertEquals("model-2", saved.saved?.fields?.model)
      assertIs<SecretsState.Locked>(saved.secrets)
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun aLockedActionOnADamagedKeysetOffersOnlyReset() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library, damaged = true)
      page.shown { it.secrets is SecretsState.Locked }

      page.on(PageIntent.Test)
      page.shown { (it.activity as? PageActivity.Unlocking)?.prompt == UnlockPrompt.CountedDamaged(1) }
      page.on(PageIntent.Passphrase(PassphraseIntent.Forgot))
      page.shown { (it.activity as? PageActivity.Unlocking)?.prompt is UnlockPrompt.Resetting }
      page.on(PageIntent.Passphrase(PassphraseIntent.Type(SecretText("a new passphrase"))))
      page.on(PageIntent.Passphrase(PassphraseIntent.TypeAgain(SecretText("a new passphrase"))))
      page.on(PageIntent.Passphrase(PassphraseIntent.Submit))

      page.shown { it.activity == PageActivity.Idle && it.secrets is SecretsState.Missing }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun forgotPassphraseOnThePageResetsAndShowsTheValuesMissing() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      val page = lockedPage(library)
      page.shown { it.secrets is SecretsState.Locked }

      page.on(PageIntent.Unlock)
      page.on(PageIntent.Passphrase(PassphraseIntent.Forgot))
      val resetting = page.shown { (it.activity as? PageActivity.Unlocking)?.prompt is UnlockPrompt.Resetting }
      assertEquals(1, ((resetting.activity as PageActivity.Unlocking).prompt as UnlockPrompt.Resetting).holding)
      page.on(PageIntent.Passphrase(PassphraseIntent.Type(SecretText("a new passphrase"))))
      page.on(PageIntent.Passphrase(PassphraseIntent.TypeAgain(SecretText("a new passphrase"))))
      page.on(PageIntent.Passphrase(PassphraseIntent.Submit))

      page.shown { it.activity == PageActivity.Idle && it.secrets is SecretsState.Missing }
      coroutineContext.cancelChildren()
    }
  }
}
