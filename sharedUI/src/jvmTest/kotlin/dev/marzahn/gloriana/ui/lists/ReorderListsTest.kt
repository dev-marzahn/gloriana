package dev.marzahn.gloriana.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.characters.DesktopPickedFile
import dev.marzahn.gloriana.logic.connection.ConnectionWritePoint
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.presets.PresetImports
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.destination_library
import dev.marzahn.gloriana.ui.resources.destination_settings
import dev.marzahn.gloriana.ui.resources.done
import dev.marzahn.gloriana.ui.resources.library_kind_presets
import dev.marzahn.gloriana.ui.resources.model_settings
import dev.marzahn.gloriana.ui.resources.move_down
import dev.marzahn.gloriana.ui.resources.reorder_kind
import dev.marzahn.gloriana.ui.resources.reorder_move_row
import dev.marzahn.gloriana.ui.resources.reorder_not_moved
import dev.marzahn.gloriana.ui.resources.settings_group_connections
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 10_000L

private const val PROMPTS =
  """"prompts": [{"identifier": "main", "name": "Main Prompt", "role": "system", "content": "Be vivid."}],
  "prompt_order": [{"character_id": 100001, "order": [{"identifier": "main", "enabled": true}]}]"""

@OptIn(ExperimentalTestApi::class)
class ReorderListsTest {
  private val directory: Path = Files.createTempDirectory("gloriana-reorder")

  @OptIn(ExperimentalPathApi::class)
  @AfterTest
  fun removeFiles() = directory.deleteRecursively()

  private fun ComposeUiTest.show(library: LiveLibrary) {
    setContent { library.Show { Box(Modifier.size(1200.dp, 900.dp)) { App() } } }
  }

  private fun ComposeUiTest.tap(matcher: SemanticsMatcher) {
    waitFor(matcher)
    onAllNodes(matcher).onFirst().performClick()
  }

  private suspend fun ComposeUiTest.tapDestination(destination: StringResource) {
    val label = getString(destination)
    tap((hasText(label) or hasContentDescription(label)) and hasClickAction())
  }

  private fun ComposeUiTest.waitFor(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun ComposeUiTest.waitForNone(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
  }

  private fun ComposeUiTest.top(matcher: SemanticsMatcher): Float =
    onAllNodes(matcher).onFirst().fetchSemanticsNode().boundsInRoot.top

  private suspend fun ComposeUiTest.moveDown(name: String) = moveDown(hasText(name))

  private suspend fun ComposeUiTest.moveDownByHandle(name: String) =
    moveDown(hasContentDescription(getString(Res.string.reorder_move_row, name)))

  private suspend fun ComposeUiTest.moveDown(row: SemanticsMatcher) {
    val movable = row and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)
    waitFor(movable)
    onAllNodes(movable).onFirst().performCustomAccessibilityActionWithLabel(getString(Res.string.move_down))
  }

  private suspend fun reorderButton(kind: StringResource) =
    hasContentDescription(getString(Res.string.reorder_kind, getString(kind)))

  private suspend fun doneButton() = hasText(getString(Res.string.done)) and hasClickAction()

  private suspend fun createButton() = hasText(getString(Res.string.create)) and hasClickAction()

  private fun LiveLibrary.importedPreset(name: String): String {
    val file = directory.resolve("$name.json").apply { writeText("{ $PROMPTS }") }
    koin.get<PresetImports>().start(DesktopPickedFile(file))
    return name
  }

  private fun LiveLibrary.presetNames(): List<String> =
    runBlocking { koin.get<PresetRepository>().presets().first().map { it.name } }

  private fun LiveLibrary.connectionNames(): List<String> = connections().map { it.fields.name }

  @Test
  fun presetsReorderAndTheMoveIsSaved() = runComposeUiTest {
    LiveLibrary.use { library ->
      val shipped = library.shippedPreset().name
      val added = library.importedPreset("Vivid")
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.presetNames().size == 2 }
      show(library)
      tapDestination(Res.string.destination_library)
      tap(hasText(getString(Res.string.library_kind_presets)) and hasClickAction())

      tap(reorderButton(Res.string.library_kind_presets))
      waitFor(doneButton())
      moveDownByHandle(shipped)

      waitUntil(timeoutMillis = WAIT_MILLIS) { library.presetNames() == listOf(added, shipped) }
    }
  }

  @Test
  fun reorderingConnectionsHidesTheOtherGroupsAndSwapsCreateForDone() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.created("Alpha")
      library.created("Beta")
      show(library)
      tapDestination(Res.string.destination_settings)
      val modelSettings = hasText(getString(Res.string.model_settings))
      waitFor(modelSettings)
      waitFor(createButton())

      tap(reorderButton(Res.string.settings_group_connections))

      waitFor(doneButton())
      waitForNone(modelSettings)
      waitForNone(createButton())
      moveDownByHandle("Alpha")
      waitUntil(timeoutMillis = WAIT_MILLIS) { library.connectionNames() == listOf("Beta", "Alpha") }

      tap(doneButton())
      waitFor(modelSettings)
      waitFor(createButton())
    }
  }

  @Test
  fun theNotMovedNoteSitsAtTheTopOfTheConnectionsGroup() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.created("Alpha")
      library.created("Beta")
      library.failingWrite = ConnectionWritePoint.MoveRowsWritten
      show(library)
      tapDestination(Res.string.destination_settings)

      moveDown("Alpha")

      val note = hasText(getString(Res.string.reorder_not_moved, "Alpha"), substring = true)
      waitFor(note)
      val header = hasText(getString(Res.string.settings_group_connections))
      assertTrue(top(header) < top(note), "the note sits below the group's header")
      assertTrue(top(note) < top(hasText("Alpha")), "the note sits above the first Connection")
      assertEquals(listOf("Alpha", "Beta"), library.connectionNames())
    }
  }
}
