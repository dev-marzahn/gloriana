package dev.marzahn.gloriana.ui.connections

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.GlorianaApp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.damaged_no_keys
import dev.marzahn.gloriana.ui.resources.destination_settings
import dev.marzahn.gloriana.ui.resources.keys_locked_one
import dev.marzahn.gloriana.ui.resources.no_keys_held
import dev.marzahn.gloriana.ui.resources.not_now
import dev.marzahn.gloriana.ui.resources.reset
import dev.marzahn.gloriana.ui.resources.reset_confirm
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.secrets_locked_line
import dev.marzahn.gloriana.ui.resources.show_key
import dev.marzahn.gloriana.ui.resources.show_passphrase
import dev.marzahn.gloriana.ui.resources.unlock
import dev.marzahn.gloriana.ui.resources.unlock_title
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import dev.marzahn.gloriana.ui.testing.NoKeychain
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.WidthClass
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GATE_WAIT_MILLIS = 20_000L
private const val PASSPHRASE = "correct horse"
private val PHONE_WIDTH = 412.dp

@OptIn(ExperimentalTestApi::class, KoinExperimentalAPI::class)
class KeysGateTest {
  private fun ComposeUiTest.waitFor(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = GATE_WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun ComposeUiTest.tap(matcher: SemanticsMatcher) {
    waitFor(matcher)
    onAllNodes(matcher).onFirst().performClick()
  }

  private suspend fun ComposeUiTest.waitForTheShell() =
    waitFor(hasContentDescription(getString(Res.string.destination_settings)) and hasClickAction())

  @Test
  fun passphraseProtectedKeysAskForThePassphraseEvenWithNoKeyHeld() = LiveLibrary.use(NoKeychain) { library ->
    runComposeUiTest {
      library.protectWithPassphrase(PASSPHRASE)
      library.restart()
      setContent { library.Show { App() } }

      waitFor(hasText(getString(Res.string.unlock_title)))
      onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(PASSPHRASE)
      tap(hasText(getString(Res.string.unlock)) and hasClickAction())

      waitForTheShell()
    }
  }

  @Test
  fun aLockedVaultPastTheGateAsksForThePassphraseOnThePageThenSaves() = LiveLibrary.use(NoKeychain) { library ->
    runComposeUiTest {
      library.protectWithPassphrase(PASSPHRASE)
      library.created("Work")
      library.restart()
      setContent { library.Show { GlorianaApp(koinEntryProvider()) } }
      tap(hasContentDescription(getString(Res.string.destination_settings)) and hasClickAction())
      tap(hasText("Work") and hasClickAction() and !hasSetTextAction())
      val keyField = hasSetTextAction() and hasAnyDescendant(hasContentDescription(getString(Res.string.show_key)))
      waitFor(keyField)
      onAllNodes(keyField).onFirst().performTextReplacement("sk-made-up")
      tap(hasText(getString(Res.string.save)) and hasClickAction())

      waitFor(hasText(getString(Res.string.unlock_title)))
      val passphraseField =
        hasSetTextAction() and hasAnyDescendant(hasContentDescription(getString(Res.string.show_passphrase)))
      onAllNodes(passphraseField).onFirst().performTextReplacement(PASSPHRASE)
      tap(hasText(getString(Res.string.unlock)) and hasClickAction())

      waitUntil(timeoutMillis = GATE_WAIT_MILLIS) { library.connections().single().key != null }
    }
  }

  @Test
  fun notNowOpensTheAppWithTheKeysLockedUntilSettingsUnlocksThem() = LiveLibrary.use(NoKeychain) { library ->
    runComposeUiTest {
      library.protectWithPassphrase(PASSPHRASE)
      library.created("Work", KeyInput.Entered("sk-made-up"))
      library.restart()
      setContent { library.Show { App() } }

      tap(hasText(getString(Res.string.not_now)) and hasClickAction())
      tap(hasContentDescription(getString(Res.string.destination_settings)) and hasClickAction())
      val lockedLine = hasText(getString(Res.string.secrets_locked_line))
      waitFor(lockedLine)
      waitFor(hasText(getString(Res.string.keys_locked_one, "Work")))

      tap(hasText(getString(Res.string.unlock)) and hasClickAction())
      val passphraseField =
        hasSetTextAction() and hasAnyDescendant(hasContentDescription(getString(Res.string.show_passphrase)))
      waitFor(passphraseField)
      onAllNodes(passphraseField).onFirst().performTextReplacement(PASSPHRASE)
      onAllNodes(hasText(getString(Res.string.unlock)) and hasClickAction()).onLast().performClick()

      waitUntil(timeoutMillis = GATE_WAIT_MILLIS) { onAllNodes(lockedLine).fetchSemanticsNodes().isEmpty() }
    }
  }

  @Test
  fun notNowIsDisabledWhileThePassphraseIsChecking() = runComposeUiTest {
    val checking = KeysGate.Unlocking(holding = 1, entry = UnlockEntry(step = UnlockStep.Checking))
    setContent { GlorianaTheme(WidthClass.of(PHONE_WIDTH)) { UnlockContent(checking, {}) } }

    onNode(hasText(runBlocking { getString(Res.string.not_now) })).assertIsNotEnabled()
  }

  @Test
  fun notNowCanBeTakenUnlessAPassphraseIsBeingCheckedOrWritten() {
    val busyReset = ResetSheet.Open(NewPassphraseEntry(step = EntryStep.Busy))

    assertTrue(KeysGate.Unlocking(holding = 1).canSkip)
    assertTrue(KeysGate.CouldNotCheck.canSkip)
    assertTrue(KeysGate.Damaged(holding = 1).canSkip)
    assertFalse(KeysGate.Unlocking(holding = 1, entry = UnlockEntry(step = UnlockStep.Checking)).canSkip)
    assertFalse(KeysGate.Unlocking(holding = 1, reset = busyReset).canSkip)
    assertFalse(KeysGate.Damaged(holding = 1, reset = busyReset).canSkip)
  }

  @Test
  fun aDamagedPassphraseFileWithNoKeyHeldSaysSoAndResets() = LiveLibrary.use(NoKeychain) { library ->
    runComposeUiTest {
      library.protectWithPassphrase(PASSPHRASE)
      library.damagePassphraseKeyset()
      library.restart()
      setContent { library.Show { App() } }

      waitFor(hasText(getString(Res.string.damaged_no_keys)))
      tap(hasText(getString(Res.string.reset)) and hasClickAction())
      waitFor(hasText(getString(Res.string.no_keys_held)))
      val fields = onAllNodes(hasSetTextAction())
      fields[0].performTextReplacement("a new passphrase")
      fields[1].performTextReplacement("a new passphrase")
      tap(hasText(getString(Res.string.reset_confirm)) and hasClickAction())

      waitForTheShell()
    }
  }
}
