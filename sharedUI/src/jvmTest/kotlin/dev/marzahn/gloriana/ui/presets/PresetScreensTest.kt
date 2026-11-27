package dev.marzahn.gloriana.ui.presets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.marzahn.gloriana.logic.characters.DesktopExportDestination
import dev.marzahn.gloriana.logic.characters.DesktopPickedFile
import dev.marzahn.gloriana.logic.files.OpenedFile
import dev.marzahn.gloriana.logic.files.PickedFile
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetImportFailure
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.StoredPreset
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.connections.LiveLibrary
import dev.marzahn.gloriana.ui.files.SaveTarget
import dev.marzahn.gloriana.ui.navigation.QuitRequests
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.changes_not_saved
import dev.marzahn.gloriana.ui.resources.destination_library
import dev.marzahn.gloriana.ui.resources.discard
import dev.marzahn.gloriana.ui.resources.export
import dev.marzahn.gloriana.ui.resources.import_waiting
import dev.marzahn.gloriana.ui.resources.keep_editing
import dev.marzahn.gloriana.ui.resources.library_kind_presets
import dev.marzahn.gloriana.ui.resources.move_down
import dev.marzahn.gloriana.ui.resources.offer_copy
import dev.marzahn.gloriana.ui.resources.offer_dont_copy
import dev.marzahn.gloriana.ui.resources.preset_more
import dev.marzahn.gloriana.ui.resources.presets_empty
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.save_changes_first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private const val WAIT_MILLIS = 10_000L

private const val PROMPTS =
  """"prompts": [
    {"identifier": "main", "name": "Main Prompt", "role": "system", "content": "Be vivid."},
    {"identifier": "chatHistory", "name": "Chat History", "marker": true},
    {"identifier": "nsfw", "name": "Auxiliary Prompt", "role": "system", "content": "Stay in character."}
  ],
  "prompt_order": [{"character_id": 100001, "order": [
    {"identifier": "main", "enabled": true},
    {"identifier": "chatHistory", "enabled": true},
    {"identifier": "nsfw", "enabled": true}
  ]}]"""

@OptIn(ExperimentalTestApi::class)
class PresetScreensTest {
  private val directory: Path = Files.createTempDirectory("gloriana-presets")

  @OptIn(ExperimentalPathApi::class)
  @AfterTest
  fun removeFiles() = directory.deleteRecursively()

  private fun presetFile(name: String, samplers: String = ""): DesktopPickedFile {
    val file = directory.resolve(name)
    file.writeText("{ $samplers $PROMPTS }")
    return DesktopPickedFile(file)
  }

  private suspend fun ComposeUiTest.openPresets(library: LiveLibrary) {
    setContent { library.Show { App() } }
    tap(hasContentDescription(getString(Res.string.destination_library)) and hasClickAction())
    tap(hasText(getString(Res.string.library_kind_presets)) and hasClickAction())
    waitFor(hasText(getString(Res.string.presets_empty)))
    waitForIdle()
  }

  private suspend fun ComposeUiTest.tap(text: StringResource) = tap(hasText(getString(text)) and hasClickAction())

  private fun ComposeUiTest.tap(matcher: SemanticsMatcher) {
    waitFor(matcher)
    onAllNodes(matcher).onFirst().performClick()
  }

  private fun ComposeUiTest.waitFor(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun LiveLibrary.imported(file: PickedFile) = koin.get<PresetImports>().start(file)

  private fun LiveLibrary.presets() = runBlocking { koin.get<PresetRepository>().presets().first() }

  private fun LiveLibrary.entries(id: Uuid): List<PresetEntry> = runBlocking {
    val stored = koin.get<PresetRepository>().preset(id).first() as StoredPreset.Readable
    stored.preset.content.entries
  }

  private fun draftNames(library: LiveLibrary, id: Uuid): List<String> =
    library.koin.get<PresetSessions>().current(id).draft?.entries.orEmpty().map { it.name }

  private fun ComposeUiTest.waitForPreset(library: LiveLibrary): Uuid {
    waitUntil(timeoutMillis = WAIT_MILLIS) { library.presets().isNotEmpty() }
    return library.presets().single().id
  }

  @Test
  fun importingAndAcceptingTheOfferCopiesTheSamplers() = runComposeUiTest {
    LiveLibrary.use { library ->
      val connection = library.created("Work")
      openPresets(library)
      library.imported(presetFile("Tuned.json", samplers = """"temperature": 0.7,"""))

      tap(hasText(getPluralString(Res.plurals.offer_copy, 1, 1)) and hasClickAction())

      waitUntil(timeoutMillis = WAIT_MILLIS) {
        library.connections().single { it.id == connection.id }.fields.samplers.temperature == 0.7
      }
      waitFor(hasContentDescription(getString(Res.string.preset_more, "Tuned")))
    }
  }

  @Test
  fun decliningTheOfferChangesNoConnection() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.created("Work")
      openPresets(library)
      library.imported(presetFile("Tuned.json", samplers = """"temperature": 0.7,"""))

      tap(Res.string.offer_dont_copy)

      waitFor(hasContentDescription(getString(Res.string.preset_more, "Tuned")))
      assertNull(library.connections().single().fields.samplers.temperature)
      assertEquals(listOf("Tuned"), library.presets().map { it.name })
    }
  }

  @Test
  fun movingAndTurningOffEntriesIsSavedOnlyOnSave() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      val id = waitForPreset(library)
      waitFor(hasContentDescription(getString(Res.string.preset_more, "Plain")))

      onAllNodes(hasText("Main Prompt") and hasClickAction()).onFirst()
        .performCustomAccessibilityActionWithLabel(getString(Res.string.move_down))
      onAllNodes(isToggleable()).onFirst().performClick()
      waitForIdle()
      assertEquals(listOf("Main Prompt", "Chat History", "Auxiliary Prompt"), library.entries(id).map { it.name })

      tap(Res.string.save)

      waitUntil(timeoutMillis = WAIT_MILLIS) { library.entries(id).first().name == "Chat History" }
      val saved = library.entries(id)
      assertEquals(listOf("Chat History", "Main Prompt", "Auxiliary Prompt"), saved.map { it.name })
      assertEquals(listOf(false, true, true), saved.map { it.enabled })
    }
  }

  @Test
  fun exportIsOfferedOnlyOnceSavedAndWritesTheSavedPreset() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      val id = waitForPreset(library)
      val menu = hasContentDescription(getString(Res.string.preset_more, "Plain"))
      val export = hasText(getString(Res.string.export)) and hasClickAction()
      waitFor(menu)
      onAllNodes(isToggleable()).onFirst().performClick()
      tap(menu)
      waitFor(hasText(getString(Res.string.save_changes_first)))
      onAllNodes(export).onFirst().assertIsNotEnabled()
      onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(1f, 1f)) }
      waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(export).fetchSemanticsNodes().isEmpty() }

      tap(Res.string.save)
      waitUntil(timeoutMillis = WAIT_MILLIS) { !library.entries(id).first().enabled }
      tap(menu)
      waitFor(export)
      onAllNodes(export).onFirst().assertIsEnabled()
      assertTrue(onAllNodes(hasText(getString(Res.string.save_changes_first))).fetchSemanticsNodes().isEmpty())

      val target = directory.resolve("out.json")
      library.koin.get<PresetExports>().start(id, SaveTarget(DesktopExportDestination(target), "out.json"))
      waitUntil(timeoutMillis = WAIT_MILLIS) { Files.exists(target) && target.readText().contains("Main Prompt") }
      val written = target.readText().filterNot(Char::isWhitespace)
      assertTrue(written.contains("{\"identifier\":\"main\",\"enabled\":false}"))
    }
  }

  @Test
  fun aFileQueuedBehindAFailingImportWaitsAndTheFailureStays() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      val gate = CompletableDeferred<Unit>()
      val held = object : PickedFile {
        override val name = "Broken.json"

        override fun open(): OpenedFile = runBlocking {
          gate.await()
          OpenedFile.Unavailable
        }
      }

      library.imported(held)
      library.imported(presetFile("Plain.json"))
      waitFor(hasText("Plain.json"))
      waitFor(hasText(getString(Res.string.import_waiting)))
      gate.complete(Unit)

      waitFor(hasContentDescription(getString(Res.string.preset_more, "Plain")))
      val failures = library.koin.get<PresetImports>().state.value.failures
      assertEquals(listOf(ImportFailureNote("Broken.json", PresetImportFailure.CantBeOpened)), failures)
    }
  }

  @Test
  fun leavingAPresetWithChangesAsksAndDiscardDropsThem() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      val id = waitForPreset(library)
      val page = hasContentDescription(getString(Res.string.preset_more, "Plain"))
      waitFor(page)
      onAllNodes(isToggleable()).onFirst().performClick()
      val libraryItem = hasContentDescription(getString(Res.string.destination_library)) and hasClickAction()
      val sessions = library.koin.get<PresetSessions>()

      tap(libraryItem)
      tap(Res.string.keep_editing)
      waitFor(page)
      assertTrue(sessions.hasChanges(id))

      tap(libraryItem)
      tap(Res.string.discard)
      waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(page).fetchSemanticsNodes().isEmpty() }
      assertFalse(sessions.hasChanges(id))
      assertTrue(library.entries(id).all { it.enabled })
    }
  }

  @Test
  fun quittingWithUnsavedPresetChangesAsksFirst() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      waitForPreset(library)
      waitFor(hasContentDescription(getString(Res.string.preset_more, "Plain")))
      onAllNodes(isToggleable()).onFirst().performClick()
      var quit = false

      library.koin.get<QuitRequests>().request(quit = { quit = true })

      waitFor(hasText(getString(Res.string.changes_not_saved, "Plain")))
      assertFalse(quit)
      tap(Res.string.discard)
      waitUntil(timeoutMillis = WAIT_MILLIS) { quit }
    }
  }

  @Test
  fun draggingAnEntryJustPastHalfARowMovesItOnePlace() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      val id = waitForPreset(library)
      waitFor(hasContentDescription(getString(Res.string.preset_more, "Plain")))
      val row = onAllNodes(hasText("Main Prompt") and hasClickAction()).onFirst()
      val height = row.fetchSemanticsNode().size.height.toFloat()

      row.performTouchInput {
        down(Offset(HANDLE_X, height / 2))
        moveBy(Offset(0f, viewConfiguration.touchSlop))
        repeat(DRAG_STEPS) { moveBy(Offset(0f, height * DRAG_SHARE / DRAG_STEPS)) }
        up()
      }

      waitUntil(timeoutMillis = WAIT_MILLIS) { draftNames(library, id).firstOrNull() == "Chat History" }
      assertEquals(listOf("Chat History", "Main Prompt", "Auxiliary Prompt"), draftNames(library, id))
    }
  }
}

private const val HANDLE_X = 24f
private const val DRAG_STEPS = 10
private const val DRAG_SHARE = 0.65f
