package dev.marzahn.gloriana.ui.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.connections.LiveLibrary
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.destination_settings
import dev.marzahn.gloriana.ui.resources.key_missing_key_short
import dev.marzahn.gloriana.ui.resources.keys_unreadable
import dev.marzahn.gloriana.ui.resources.start_over
import dev.marzahn.gloriana.ui.resources.start_over_confirm
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

private const val WAIT_MILLIS = 10_000L

@OptIn(ExperimentalTestApi::class)
class StartOverTest {
  private fun ComposeUiTest.waitFor(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun ComposeUiTest.tap(matcher: SemanticsMatcher) {
    waitFor(matcher)
    onAllNodes(matcher).onFirst().performClick()
  }

  @Test
  fun keysTheDeviceCanNoLongerOpenOfferStartOverWhichErasesThem() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work", KeyInput.Entered("sk-made-up"))
      library.damageDeviceKeyset()
      library.restart()
      setContent { library.Show { App() } }
      tap(hasContentDescription(getString(Res.string.destination_settings)) and hasClickAction())

      val note = hasText(getString(Res.string.keys_unreadable), substring = true)
      waitFor(note)
      tap(hasText(getString(Res.string.start_over)) and hasClickAction())
      tap(hasText(getString(Res.string.start_over_confirm)) and hasClickAction())

      waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(note).fetchSemanticsNodes().isEmpty() }
      waitFor(hasText(getString(Res.string.key_missing_key_short)))
    }
  }
}
