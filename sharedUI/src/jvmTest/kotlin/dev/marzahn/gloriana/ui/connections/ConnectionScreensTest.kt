package dev.marzahn.gloriana.ui.connections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.default_mark
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.delete
import dev.marzahn.gloriana.ui.resources.delete_connection
import dev.marzahn.gloriana.ui.resources.destination_settings
import dev.marzahn.gloriana.ui.resources.discard
import dev.marzahn.gloriana.ui.resources.keep_editing
import dev.marzahn.gloriana.ui.resources.make_default
import dev.marzahn.gloriana.ui.resources.model_placeholder
import dev.marzahn.gloriana.ui.resources.model_settings
import dev.marzahn.gloriana.ui.resources.more_for
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.saving
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 10_000L

@OptIn(ExperimentalTestApi::class)
class ConnectionScreensTest {
  private suspend fun ComposeUiTest.openSettings(library: LiveLibrary) {
    setContent { library.Show { App() } }
    tap(hasContentDescription(getString(Res.string.destination_settings)) and hasClickAction())
  }

  private suspend fun ComposeUiTest.tap(text: StringResource) = tap(hasText(getString(text)) and hasClickAction())

  private fun ComposeUiTest.tap(matcher: SemanticsMatcher) {
    waitFor(matcher)
    onAllNodes(matcher).onFirst().performClick()
  }

  private fun ComposeUiTest.waitFor(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
  }

  private suspend fun ComposeUiTest.field(showing: String): SemanticsNodeInteraction {
    val matcher = hasSetTextAction() and (hasText(showing) or hasAnyDescendant(hasText(showing)))
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    return onAllNodes(matcher).onFirst()
  }

  private suspend fun menuOf(name: String) = hasContentDescription(getString(Res.string.more_for, name))

  private suspend fun ComposeUiTest.rowMenu(name: String) = tap(menuOf(name))

  private suspend fun ComposeUiTest.pageMenu(name: String) {
    val menu = menuOf(name)
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(menu).fetchSemanticsNodes().size == 2 }
    onAllNodes(menu).onLast().performClick()
  }

  private suspend fun rowMarkedDefault(name: String) =
    hasText(name) and hasText(getString(Res.string.default_mark)) and hasClickAction()

  private fun listRow(name: String) = hasText(name) and hasClickAction() and !hasSetTextAction()

  private fun ComposeUiTest.openRow(name: String) = tap(listRow(name))

  private suspend fun ComposeUiTest.noDiscardQuestion() {
    assertTrue(onAllNodesWithText(getString(Res.string.discard)).fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun creatingFromATemplateSavesItAndListsIt() = LiveLibrary.use { library ->
    runComposeUiTest {
      openSettings(library)
      tap(Res.string.create)
      tap(hasText("Anthropic") and hasClickAction())
      field(getString(Res.string.model_placeholder)).performTextReplacement("claude-test")
      tap(Res.string.save)

      waitUntil(timeoutMillis = WAIT_MILLIS) { library.connections().isNotEmpty() }
      val saved = library.connections().single()
      assertEquals("Anthropic", saved.fields.name)
      assertEquals("claude-test", saved.fields.model)
      waitFor(listRow("Anthropic"))
      waitForIdle()
      noDiscardQuestion()

      field("claude-test").performTextReplacement("claude-next")
      tap(Res.string.save)
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.connections().single().fields.model == "claude-next" }
      assertEquals(1, library.connections().size)
    }
  }

  @Test
  fun editingASavedConnectionSavesTheChange() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work")
      openSettings(library)
      openRow("Work")
      field("Work").performTextReplacement("Home")
      tap(Res.string.save)

      waitUntil(timeoutMillis = WAIT_MILLIS) { library.connections().single().fields.name == "Home" }
      waitFor(listRow("Home"))
    }
  }

  @Test
  fun makingAConnectionTheDefaultFromItsRowMovesTheMark() = LiveLibrary.use { library ->
    runComposeUiTest {
      val work = library.created("Work")
      val home = library.created("Home")
      assertEquals(work.id, library.default())
      openSettings(library)
      waitFor(rowMarkedDefault("Work"))
      rowMenu("Home")
      tap(Res.string.make_default)

      waitFor(rowMarkedDefault("Home"))
      assertEquals(home.id, library.default())
      assertTrue(onAllNodes(rowMarkedDefault("Work")).fetchSemanticsNodes().isEmpty())
    }
  }

  @Test
  fun deletingFromTheRowRemovesIt() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work")
      openSettings(library)
      rowMenu("Work")
      tap(Res.string.delete_connection)
      tap(Res.string.delete)

      waitUntil(timeoutMillis = WAIT_MILLIS) { library.connections().isEmpty() }
      waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithText("Work").fetchSemanticsNodes().isEmpty() }
      noDiscardQuestion()
    }
  }

  @Test
  fun deletingFromItsPageClosesThePageWithoutAskingToDiscard() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work")
      openSettings(library)
      openRow("Work")
      field("Work").performTextReplacement("Work, edited")
      pageMenu("Work")
      tap(Res.string.delete_connection)
      tap(Res.string.delete)

      waitUntil(timeoutMillis = WAIT_MILLIS) { library.connections().isEmpty() }
      waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithText("Work, edited").fetchSemanticsNodes().isEmpty() }
      noDiscardQuestion()
    }
  }

  @Test
  fun createAsksAboutUnsavedChangesOnlyOnceATemplateIsPicked() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work")
      setContent { library.Show { Box(Modifier.size(1000.dp, 700.dp)) { App() } } }
      tap(hasContentDescription(getString(Res.string.destination_settings)) and hasClickAction())
      openRow("Work")
      field("Work").performTextReplacement("Work, edited")

      tap(Res.string.create)
      waitFor(hasText("Anthropic") and hasClickAction())
      noDiscardQuestion()
      tap(Res.string.cancel)
      waitForIdle()
      noDiscardQuestion()
      field("Work, edited")

      tap(Res.string.create)
      tap(hasText("Anthropic") and hasClickAction())
      tap(Res.string.keep_editing)
      field("Work, edited")
    }
  }

  @Test
  fun anEditMadeWhileAConnectionSavesStaysUnsaved() = LiveLibrary.use { library ->
    runComposeUiTest {
      library.created("Work")
      openSettings(library)
      openRow("Work")
      field("Work").performTextReplacement("Home")
      library.writes.hold()
      tap(Res.string.save)
      waitFor(hasText(getString(Res.string.saving)) and isNotEnabled())
      field("Home").assertIsEnabled().performTextReplacement("Home, later")
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.writes.writesCaught > 0 }
      library.writes.release()

      waitUntil(timeoutMillis = WAIT_MILLIS) { library.connections().single().fields.name == "Home" }
      waitForIdle()
      field("Home, later")
      tap(Res.string.save)
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.connections().single().fields.name == "Home, later" }
    }
  }

  @Test
  fun anEditMadeWhileModelSettingsSaveStaysUnsaved() = LiveLibrary.use { library ->
    runComposeUiTest {
      openSettings(library)
      tap(Res.string.model_settings)
      val temperature = hasSetTextAction()
      waitFor(temperature)
      onAllNodes(temperature).onFirst().performTextReplacement("0.5")
      library.writes.hold()
      tap(Res.string.save)
      waitFor(hasText(getString(Res.string.saving)))
      field("0.5").performTextReplacement("0.9")
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.writes.writesCaught > 0 }
      library.writes.release()

      waitUntil(timeoutMillis = WAIT_MILLIS) { library.globalSamplers().temperature == 0.5 }
      waitForIdle()
      field("0.9")
      tap(Res.string.save)
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.globalSamplers().temperature == 0.9 }
    }
  }

  @Test
  fun discardingModelSettingsPutsBackWhatIsSaved() = LiveLibrary.use { library ->
    runComposeUiTest {
      openSettings(library)
      tap(Res.string.model_settings)
      val temperature = hasSetTextAction()
      waitFor(temperature)
      onAllNodes(temperature).onFirst().performTextReplacement("0.5")
      tap(hasContentDescription(getString(Res.string.destination_settings)) and hasClickAction())
      tap(Res.string.discard)

      tap(Res.string.model_settings)
      waitFor(temperature)
      assertTrue(onAllNodesWithText("0.5").fetchSemanticsNodes().isEmpty())
      assertEquals(null, library.globalSamplers().temperature)
    }
  }
}
