package dev.marzahn.gloriana.ui.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.connections_error
import dev.marzahn.gloriana.ui.resources.destination_settings
import dev.marzahn.gloriana.ui.resources.discard
import dev.marzahn.gloriana.ui.resources.key_file_unreadable_detail
import dev.marzahn.gloriana.ui.resources.key_missing_key_short
import dev.marzahn.gloriana.ui.resources.keys_unreadable
import dev.marzahn.gloriana.ui.resources.keys_unreadable_until_loaded_detail
import dev.marzahn.gloriana.ui.resources.model_settings
import dev.marzahn.gloriana.ui.resources.no_keys_held
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.show_key
import dev.marzahn.gloriana.ui.resources.start_over
import dev.marzahn.gloriana.ui.resources.start_over_confirm
import dev.marzahn.gloriana.ui.resources.start_over_keys_erased
import dev.marzahn.gloriana.ui.resources.start_over_text
import dev.marzahn.gloriana.ui.resources.unreadable_note_saved
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import dev.marzahn.gloriana.ui.testing.MemoryKeychain
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 10_000L

@OptIn(ExperimentalTestApi::class)
class StartOverTest {
  private fun ComposeUiTest.waitFor(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun ComposeUiTest.waitGone(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
  }

  private fun ComposeUiTest.tap(matcher: SemanticsMatcher) {
    waitFor(matcher)
    onAllNodes(matcher).onFirst().performClick()
  }

  private suspend fun ComposeUiTest.openSettings(library: LiveLibrary) {
    setContent { library.Show { App() } }
    tap(hasContentDescription(getString(Res.string.destination_settings)) and hasClickAction())
  }

  private suspend fun ComposeUiTest.startOver() {
    tap(hasText(getString(Res.string.start_over)) and hasClickAction())
    tap(hasText(getString(Res.string.start_over_confirm)) and hasClickAction())
  }

  @Test
  fun keysTheDeviceCanNoLongerOpenOfferStartOverWhichErasesThem() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work", KeyInput.Entered("sk-made-up"))
      library.damageDeviceKeyset()
      library.restart()
      openSettings(library)

      val note = hasText(getString(Res.string.keys_unreadable), substring = true)
      waitFor(note)
      startOver()

      waitGone(note)
      waitFor(hasText(getString(Res.string.key_missing_key_short)))
    }
  }

  @Test
  fun settingsWithNoKeyAsksNoKeychainAndWritesNoKeyFile() {
    val keychain = MemoryKeychain()
    LiveLibrary.use(keychain) { library ->
      runComposeUiTest {
        library.created("Work")
        library.restart()
        openSettings(library)
        waitFor(hasText("Work"))
        waitFor(hasText(getString(Res.string.model_settings)))
        waitForIdle()

        assertEquals(emptySet(), keychain.accounts)
        assertEquals(emptyList(), library.keysetFiles())
      }
    }
  }

  @Test
  fun aKeyFileThatASaveCantOpenOffersStartOverEvenWhenNoConnectionHoldsAKey() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work")
      library.damageDeviceKeyset()
      library.restart()
      openSettings(library)
      tap(hasText("Work") and hasClickAction() and !hasSetTextAction())
      val keyField = hasSetTextAction() and hasAnyDescendant(hasContentDescription(getString(Res.string.show_key)))
      waitFor(keyField)
      onAllNodes(keyField).onFirst().performTextReplacement("sk-made-up")
      tap(hasText(getString(Res.string.save)) and hasClickAction())
      waitFor(hasText(getString(Res.string.unreadable_note_saved, "Work"), substring = true))
      tap(hasContentDescription(getString(Res.string.destination_settings)) and hasClickAction())
      tap(hasText(getString(Res.string.discard)) and hasClickAction())

      waitFor(hasText(getString(Res.string.key_file_unreadable_detail), substring = true))
      tap(hasText(getString(Res.string.start_over)) and hasClickAction())
      waitFor(hasText(getString(Res.string.no_keys_held)))
    }
  }

  @Test
  fun whileTheConnectionsCantLoadTheNoteShowsWithoutACountOrStartOver() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work", KeyInput.Entered("sk-made-up"))
      library.damageDeviceKeyset()
      library.restartWithConnectionsUnreadable()
      openSettings(library)

      waitFor(hasText(getString(Res.string.connections_error), substring = true))
      waitFor(hasText(getString(Res.string.keys_unreadable_until_loaded_detail), substring = true))
      waitForIdle()
      val noKeyNote = hasText(getString(Res.string.key_file_unreadable_detail), substring = true)
      assertTrue(onAllNodes(noKeyNote).fetchSemanticsNodes().isEmpty())
      assertTrue(onAllNodes(hasText(getString(Res.string.start_over))).fetchSemanticsNodes().isEmpty())
    }
  }

  @Test
  fun aStartOverThatErasedTheKeysButWroteNoKeyFileKeepsTheNoteAndStartOver() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work", KeyInput.Entered("sk-made-up"))
      library.damageDeviceKeyset()
      library.restart()
      library.keysetWritesFail = true
      openSettings(library)
      waitFor(hasText(getString(Res.string.keys_unreadable), substring = true))
      startOver()

      val erasedNote = hasText(getString(Res.string.start_over_keys_erased), substring = true)
      waitFor(erasedNote)
      waitFor(hasText(getString(Res.string.start_over)) and hasClickAction())

      library.keysetWritesFail = false
      startOver()
      waitGone(erasedNote)
      waitFor(hasText(getString(Res.string.key_missing_key_short)))
    }
  }

  @Test
  fun whenTheCheckAfterAFailedStartOverWritesANewKeyFileTheStartOverIsDone() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work", KeyInput.Entered("sk-made-up"))
      library.damageDeviceKeyset()
      library.restart()
      library.refuseTheNextKeysetWrite { library.loseTheDeviceKeysetFile() }
      openSettings(library)
      waitFor(hasText(getString(Res.string.keys_unreadable), substring = true))
      startOver()

      waitFor(hasText(getString(Res.string.key_missing_key_short)))
      waitForIdle()
      val erasedNote = hasText(getString(Res.string.start_over_keys_erased), substring = true)
      assertTrue(onAllNodes(erasedNote).fetchSemanticsNodes().isEmpty())
      assertTrue(onAllNodes(hasText(getString(Res.string.start_over))).fetchSemanticsNodes().isEmpty())
      assertEquals(listOf("keyset-device.json"), library.keysetFiles())
    }
  }

  @Test
  fun whenTheCheckAfterAFailedStartOverCantReachTheKeychainTheErasedNoteStays() {
    val keychain = MemoryKeychain()
    LiveLibrary.use(keychain) { library ->
      runComposeUiTest {
        library.created("Work", KeyInput.Entered("sk-made-up"))
        library.damageDeviceKeyset()
        library.restart()
        library.refuseTheNextKeysetWrite {
          library.loseTheDeviceKeysetFile()
          keychain.unreachable = true
        }
        openSettings(library)
        waitFor(hasText(getString(Res.string.keys_unreadable), substring = true))
        startOver()

        waitFor(hasText(getString(Res.string.start_over_keys_erased), substring = true))
        waitFor(hasText(getString(Res.string.start_over)) and hasClickAction())
        assertEquals(emptyList(), library.keysetFiles())
      }
    }
  }

  @Test
  fun theStartOverQuestionCountsTheConnectionsHoldingKeysNowNotWhenItOpened() = LiveLibrary.use { library ->
    runComposeUiTest {
      val work = library.created("Work", KeyInput.Entered("sk-made-up"))
      library.created("Home", KeyInput.Entered("sk-made-up-too"))
      library.damageDeviceKeyset()
      library.restart()
      openSettings(library)
      tap(hasText(getString(Res.string.start_over)) and hasClickAction())
      waitFor(hasText(getPluralString(Res.plurals.start_over_text, 2, 2)))

      library.koin.get<ConnectionRepository>().delete(work.id)

      waitFor(hasText(getPluralString(Res.plurals.start_over_text, 1, 1)))
    }
  }
}
