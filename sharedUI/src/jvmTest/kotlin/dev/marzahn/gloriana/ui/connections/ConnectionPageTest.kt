package dev.marzahn.gloriana.ui.connections

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.HeaderValueInput
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.navigation.QuitRequests
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.change_passphrase
import dev.marzahn.gloriana.ui.resources.changes_not_saved
import dev.marzahn.gloriana.ui.resources.destination_settings
import dev.marzahn.gloriana.ui.resources.discard
import dev.marzahn.gloriana.ui.resources.model_list_failed
import dev.marzahn.gloriana.ui.resources.passphrase
import dev.marzahn.gloriana.ui.resources.pick_model
import dev.marzahn.gloriana.ui.resources.reason_unreachable
import dev.marzahn.gloriana.ui.resources.saved_secret
import dev.marzahn.gloriana.ui.resources.snack_passphrase_changed
import dev.marzahn.gloriana.ui.resources.test
import dev.marzahn.gloriana.ui.resources.test_failed
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import dev.marzahn.gloriana.ui.testing.NoKeychain
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 10_000L
private const val KEY_MARKER = "sk-marker-4417"
private const val HEADER_MARKER = "hv-marker-9023"
private const val OLD_PASSPHRASE = "old passphrase"
private const val NEW_PASSPHRASE = "new passphrase"
private const val CLOSED_PORT_URL = "http://127.0.0.1:9/v1"

@OptIn(ExperimentalTestApi::class)
class ConnectionPageTest {
  private fun ComposeUiTest.waitFor(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun ComposeUiTest.tap(matcher: SemanticsMatcher) {
    waitFor(matcher)
    onAllNodes(matcher).onFirst().performClick()
  }

  private suspend fun ComposeUiTest.openSettings(library: LiveLibrary) {
    setContent { library.Show { App() } }
    tap(settingsItem())
  }

  private suspend fun settingsItem() =
    hasContentDescription(getString(Res.string.destination_settings)) and hasClickAction()

  private fun ComposeUiTest.openRow(name: String) = tap(hasText(name) and hasClickAction() and !hasSetTextAction())

  private fun ComposeUiTest.nameField(name: String) = onAllNodes(hasSetTextAction() and hasText(name)).onFirst()

  @Test
  fun reopeningShowsTheSavedKeyAndHeaderValueOnlyMasked() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created(
        "Work",
        key = KeyInput.Entered(KEY_MARKER),
        headers = listOf(HeaderDraft("X-Team", HeaderValueInput.Entered(HEADER_MARKER))),
      )
      library.restart()
      openSettings(library)
      openRow("Work")

      waitFor(hasText("X-Team"))
      val masked = hasText(getString(Res.string.saved_secret))
      waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(masked).fetchSemanticsNodes().size >= 2 }
      listOf(KEY_MARKER, HEADER_MARKER).forEach { marker ->
        assertTrue(onAllNodes(hasText(marker, substring = true)).fetchSemanticsNodes().isEmpty(), marker)
      }
    }
  }

  @Test
  fun leavingWithUnsavedChangesAsksWithTheSavedNameAndDiscardDropsThem() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work")
      openSettings(library)
      openRow("Work")
      waitFor(hasSetTextAction() and hasText("Work"))
      nameField("Work").performTextReplacement("")

      tap(settingsItem())
      waitFor(hasText(getString(Res.string.changes_not_saved, "Work")))
      tap(hasText(getString(Res.string.discard)) and hasClickAction())

      openRow("Work")
      waitFor(hasSetTextAction() and hasText("Work"))
      assertEquals("Work", library.connections().single().fields.name)
    }
  }

  @Test
  fun quittingWithUnsavedChangesAsksFirst() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work")
      openSettings(library)
      openRow("Work")
      waitFor(hasSetTextAction() and hasText("Work"))
      nameField("Work").performTextReplacement("Home")
      waitFor(hasText("Home") and !hasSetTextAction())
      var quit = false

      library.koin.get<QuitRequests>().request(quit = { quit = true })

      waitFor(hasText(getString(Res.string.changes_not_saved, "Home")))
      assertFalse(quit)
      tap(hasText(getString(Res.string.discard)) and hasClickAction())
      waitUntil(timeoutMillis = WAIT_MILLIS) { quit }
      assertEquals("Work", library.connections().single().fields.name)
    }
  }

  @Test
  fun testAndTheModelListOnThePageSayTheHostCantBeReached() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Local", baseUrl = CLOSED_PORT_URL)
      openSettings(library)
      openRow("Local")
      val unreachable = getString(Res.string.reason_unreachable)

      tap(hasText(getString(Res.string.test)) and hasClickAction())
      waitFor(hasText(getString(Res.string.test_failed, unreachable), substring = true))

      val pickModel = hasContentDescription(getString(Res.string.pick_model))
      onAllNodes(pickModel, useUnmergedTree = true).onFirst().onParent()
        .performSemanticsAction(SemanticsActions.OnClick)
      waitFor(hasText(getString(Res.string.model_list_failed, unreachable), substring = true))
    }
  }

  @Test
  fun changingThePassphraseKeepsTheKeysUnderTheNewOne() = LiveLibrary.use(NoKeychain) { library ->
    runComposeUiTest {
      library.protectWithPassphrase(OLD_PASSPHRASE)
      library.created("Work", key = KeyInput.Entered(KEY_MARKER))
      openSettings(library)
      tap(hasText(getString(Res.string.passphrase)) and hasClickAction())
      waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 3 }
      val fields = onAllNodes(hasSetTextAction())
      fields[0].performTextReplacement(OLD_PASSPHRASE)
      fields[1].performTextReplacement(NEW_PASSPHRASE)
      fields[2].performTextReplacement(NEW_PASSPHRASE)
      tap(hasText(getString(Res.string.change_passphrase)) and hasClickAction())

      waitFor(hasText(getString(Res.string.snack_passphrase_changed)))
      library.restart()
      assertEquals(PassphraseResult.WrongPassphrase, library.unlocks(OLD_PASSPHRASE))
      assertEquals(PassphraseResult.Accepted, library.unlocks(NEW_PASSPHRASE))
    }
  }
}
