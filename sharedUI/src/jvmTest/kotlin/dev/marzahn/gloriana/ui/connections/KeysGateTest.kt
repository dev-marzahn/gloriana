package dev.marzahn.gloriana.ui.connections

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.hasAnyDescendant
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.GlorianaApp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.damaged_no_keys
import dev.marzahn.gloriana.ui.resources.destination_settings
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.show_key
import dev.marzahn.gloriana.ui.resources.show_passphrase
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import dev.marzahn.gloriana.ui.resources.reset
import dev.marzahn.gloriana.ui.resources.reset_confirm
import dev.marzahn.gloriana.ui.resources.no_keys_held
import dev.marzahn.gloriana.ui.resources.unlock
import dev.marzahn.gloriana.ui.resources.unlock_title
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

private const val GATE_WAIT_MILLIS = 20_000L
private const val PASSPHRASE = "correct horse"

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
