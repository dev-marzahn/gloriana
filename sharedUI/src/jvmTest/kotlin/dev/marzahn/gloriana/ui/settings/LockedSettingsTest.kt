package dev.marzahn.gloriana.ui.settings

import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.ui.connections.GateIntent
import dev.marzahn.gloriana.ui.connections.KeysGate
import dev.marzahn.gloriana.ui.connections.KeysGateViewModel
import dev.marzahn.gloriana.ui.connections.PassphraseIntent
import dev.marzahn.gloriana.ui.connections.SecretText
import dev.marzahn.gloriana.ui.connections.UnlockPrompt
import dev.marzahn.gloriana.ui.connections.UnlockStep
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import dev.marzahn.gloriana.ui.testing.NoKeychain
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val WAIT_MILLIS = 20_000L
private const val PASSPHRASE = "correct horse"
private const val QUIET_MILLIS = 500L

@OptIn(ExperimentalCoroutinesApi::class)
class LockedSettingsTest {
  @BeforeTest
  fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

  @AfterTest
  fun reset() = Dispatchers.resetMain()

  private fun LiveLibrary.lockedWith(vararg keyed: String, plain: String? = null) {
    protectWithPassphrase(PASSPHRASE)
    keyed.forEach { created(it, KeyInput.Entered("sk-made-up")) }
    plain?.let { created(it) }
    restart()
  }

  @Test
  fun notNowLeavesTheVaultLockedAndOpensTheShell() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      library.lockedWith("Work")
      val gate = library.koin.get<KeysGateViewModel>()
      withTimeout(WAIT_MILLIS) { gate.gate.first { it is KeysGate.Unlocking } }

      gate.on(GateIntent.NotNow)

      assertEquals(KeysGate.Open, gate.gate.value)
      val settings = library.koin.get<SettingsViewModel>()
      launch(Dispatchers.Main) { settings.state.collect {} }
      withTimeout(WAIT_MILLIS) { settings.state.first { it.locked == listOf("Work") } }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun theLockedNoteNamesOnlyConnectionsThatHoldKeysAndLeavesOnceUnlocked() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      library.lockedWith("Work", "Home", plain = "Local")
      library.needKeys()
      val settings = library.koin.get<SettingsViewModel>()
      launch(Dispatchers.Main) { settings.state.collect {} }
      withTimeout(WAIT_MILLIS) { settings.state.first { it.locked == listOf("Home", "Work") } }

      settings.on(SettingsIntent.AskToUnlock)
      assertEquals(UnlockPrompt.Unlocking(), settings.state.first { it.unlock != null }.unlock)
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Type(SecretText(PASSPHRASE))))
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Submit))

      withTimeout(WAIT_MILLIS) { settings.state.first { it.locked.isEmpty() && it.unlock == null } }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun notNowFromAKeyFileThatCantBeCheckedReadsLockedAndUnlocksOnceItCanBeRead() =
    LiveLibrary.use(NoKeychain) { library ->
      runBlocking {
        library.protectWithPassphrase(PASSPHRASE)
        library.created("Work", KeyInput.Entered("sk-made-up"))
        library.passphraseKeysetReadable(false)
        library.restart()
        val gate = library.koin.get<KeysGateViewModel>()
        withTimeout(WAIT_MILLIS) { gate.gate.first { it == KeysGate.CouldNotCheck } }
        gate.on(GateIntent.NotNow)
        val settings = library.koin.get<SettingsViewModel>()
        launch(Dispatchers.Main) { settings.state.collect {} }
        withTimeout(WAIT_MILLIS) { settings.state.first { it.locked == listOf("Work") } }

        settings.on(SettingsIntent.AskToUnlock)
        settings.on(SettingsIntent.Passphrase(PassphraseIntent.Type(SecretText(PASSPHRASE))))
        settings.on(SettingsIntent.Passphrase(PassphraseIntent.Submit))
        withTimeout(WAIT_MILLIS) {
          settings.state.first { (it.unlock as? UnlockPrompt.Unlocking)?.entry?.step == UnlockStep.CouldNotCheck }
        }
        library.passphraseKeysetReadable(true)
        settings.on(SettingsIntent.Passphrase(PassphraseIntent.Retry))
        settings.on(SettingsIntent.Passphrase(PassphraseIntent.Submit))

        withTimeout(WAIT_MILLIS) { settings.state.first { it.locked.isEmpty() && it.unlock == null } }
        coroutineContext.cancelChildren()
      }
    }

  @Test
  fun forgotPassphraseFromSettingsResetsAndTheNoteLeaves() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      library.lockedWith("Work")
      library.needKeys()
      val settings = library.koin.get<SettingsViewModel>()
      launch(Dispatchers.Main) { settings.state.collect {} }
      withTimeout(WAIT_MILLIS) { settings.state.first { it.locked == listOf("Work") } }

      settings.on(SettingsIntent.AskToUnlock)
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Forgot))
      val resetting = withTimeout(WAIT_MILLIS) { settings.state.first { it.unlock is UnlockPrompt.Resetting } }
      assertEquals(1, (resetting.unlock as UnlockPrompt.Resetting).holding)
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Type(SecretText("a new passphrase"))))
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.TypeAgain(SecretText("a new passphrase"))))
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Submit))

      withTimeout(WAIT_MILLIS) { settings.state.first { it.locked.isEmpty() && it.unlock == null } }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun forgotThenCancelBeforeTheCountReturnsOpensNoReset() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      library.lockedWith("Work")
      library.needKeys()
      val settings = library.koin.get<SettingsViewModel>()
      launch(Dispatchers.Main) { settings.state.collect {} }

      settings.on(SettingsIntent.AskToUnlock)
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Forgot))
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Cancel))

      assertNull(withTimeoutOrNull(QUIET_MILLIS) { settings.state.first { it.unlock != null } })
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun aDamagedKeysetWhoseCountFailsOffersRetryAndNoPassphraseField() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      library.protectWithPassphrase(PASSPHRASE)
      library.damagePassphraseKeyset()
      library.restartWithConnectionsUnreadable()
      library.needKeys()
      val settings = library.koin.get<SettingsViewModel>()
      launch(Dispatchers.Main) { settings.state.collect {} }

      settings.on(SettingsIntent.AskToUnlock)
      withTimeout(WAIT_MILLIS) { settings.state.first { it.unlock == UnlockPrompt.DamagedNotCounted } }
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Retry))

      withTimeout(WAIT_MILLIS) { settings.state.first { it.unlock == UnlockPrompt.DamagedNotCounted } }
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Cancel))
      withTimeout(WAIT_MILLIS) { settings.state.first { it.unlock == null } }
      coroutineContext.cancelChildren()
    }
  }

  @Test
  fun aWrongPassphraseKeepsTheSheetAndCancelClosesIt() = LiveLibrary.use(NoKeychain) { library ->
    runBlocking {
      library.lockedWith("Work")
      library.needKeys()
      val settings = library.koin.get<SettingsViewModel>()
      launch(Dispatchers.Main) { settings.state.collect {} }

      settings.on(SettingsIntent.AskToUnlock)
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Type(SecretText("not the passphrase"))))
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Submit))
      withTimeout(WAIT_MILLIS) {
        settings.state.first { (it.unlock as? UnlockPrompt.Unlocking)?.entry?.step == UnlockStep.Wrong }
      }
      settings.on(SettingsIntent.Passphrase(PassphraseIntent.Cancel))

      withTimeout(WAIT_MILLIS) { settings.state.first { it.unlock == null && it.locked == listOf("Work") } }
      coroutineContext.cancelChildren()
    }
  }
}
