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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.marzahn.gloriana.logic.characters.DesktopExportDestination
import dev.marzahn.gloriana.logic.characters.DesktopPickedFile
import dev.marzahn.gloriana.logic.files.OpenedFile
import dev.marzahn.gloriana.logic.files.PickedFile
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetImportFailure
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.StoredPreset
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.files.SaveTarget
import dev.marzahn.gloriana.ui.navigation.QuitRequests
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.add_group
import dev.marzahn.gloriana.ui.resources.add_group_confirm
import dev.marzahn.gloriana.ui.resources.changes_not_saved
import dev.marzahn.gloriana.ui.resources.delete_preset
import dev.marzahn.gloriana.ui.resources.depth_error
import dev.marzahn.gloriana.ui.resources.destination_library
import dev.marzahn.gloriana.ui.resources.discard
import dev.marzahn.gloriana.ui.resources.export
import dev.marzahn.gloriana.ui.resources.import_waiting
import dev.marzahn.gloriana.ui.resources.keep_editing
import dev.marzahn.gloriana.ui.resources.library_kind_presets
import dev.marzahn.gloriana.ui.resources.make_default
import dev.marzahn.gloriana.ui.resources.more_for
import dev.marzahn.gloriana.ui.resources.move_down
import dev.marzahn.gloriana.ui.resources.offer_copy
import dev.marzahn.gloriana.ui.resources.offer_dont_copy
import dev.marzahn.gloriana.ui.resources.preset_default_cant_delete
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.save_changes_first
import dev.marzahn.gloriana.ui.testing.LiveLibrary
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
    waitFor(hasText(library.shippedPreset().name))
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

  private fun LiveLibrary.entries(id: Uuid): List<PresetEntry> = runBlocking {
    val stored = koin.get<PresetRepository>().preset(id).first() as StoredPreset.Readable
    stored.preset.content.entries
  }

  private fun draftNames(library: LiveLibrary, id: Uuid): List<String> =
    library.koin.get<PresetSessions>().current(id).unsaved?.draft?.entries.orEmpty().map { it.name }

  private fun ComposeUiTest.waitForPreset(library: LiveLibrary): Uuid {
    waitUntil(timeoutMillis = WAIT_MILLIS) { library.addedPresets().isNotEmpty() }
    return library.addedPresets().single().id
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
      waitFor(hasContentDescription(getString(Res.string.more_for, "Tuned")))
    }
  }

  @Test
  fun decliningTheOfferChangesNoConnection() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.created("Work")
      openPresets(library)
      library.imported(presetFile("Tuned.json", samplers = """"temperature": 0.7,"""))

      tap(Res.string.offer_dont_copy)

      waitFor(hasContentDescription(getString(Res.string.more_for, "Tuned")))
      assertNull(library.connections().single().fields.samplers.temperature)
      assertEquals(listOf("Tuned"), library.addedPresets().map { it.name })
    }
  }

  @Test
  fun movingAndTurningOffEntriesIsSavedOnlyOnSave() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      val id = waitForPreset(library)
      waitFor(hasContentDescription(getString(Res.string.more_for, "Plain")))

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
  fun theDefaultPresetHidesMakeDefaultAndCantBeDeleted() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      val name = library.shippedPreset().name
      tap(hasText(name) and hasClickAction())
      tap(hasContentDescription(getString(Res.string.more_for, name)))

      waitFor(hasText(getString(Res.string.preset_default_cant_delete)))
      assertTrue(onAllNodes(hasText(getString(Res.string.make_default))).fetchSemanticsNodes().isEmpty())
      onAllNodes(hasText(getString(Res.string.delete_preset)) and hasClickAction()).onFirst().assertIsNotEnabled()
    }
  }

  @Test
  fun exportIsOfferedOnlyOnceSavedAndWritesTheSavedPreset() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      val id = waitForPreset(library)
      val menu = hasContentDescription(getString(Res.string.more_for, "Plain"))
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

      waitFor(hasContentDescription(getString(Res.string.more_for, "Plain")))
      val failures = library.koin.get<PresetImports>().state.value.failures
      assertEquals(listOf("Broken.json" to PresetImportFailure.CantBeOpened), failures.map { it.fileName to it.reason })
    }
  }

  @Test
  fun leavingAPresetWithChangesAsksAndDiscardDropsThem() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      val id = waitForPreset(library)
      val page = hasContentDescription(getString(Res.string.more_for, "Plain"))
      waitFor(page)
      onAllNodes(isToggleable()).onFirst().performClick()
      val libraryItem = hasContentDescription(getString(Res.string.destination_library)) and hasClickAction()
      val sessions = library.koin.get<PresetSessions>()

      tap(libraryItem)
      tap(Res.string.keep_editing)
      waitFor(page)
      assertTrue((sessions.current(id).unsaved?.hasChanges == true))

      tap(libraryItem)
      tap(Res.string.discard)
      waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(page).fetchSemanticsNodes().isEmpty() }
      assertFalse((sessions.current(id).unsaved?.hasChanges == true))
      assertTrue(library.entries(id).all { it.enabled })
    }
  }

  @Test
  fun quittingWithUnsavedPresetChangesAsksFirst() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      waitForPreset(library)
      waitFor(hasContentDescription(getString(Res.string.more_for, "Plain")))
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
  fun wrapperTextFollowsTheDraftWhenItIsDiscarded() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      val id = waitForPreset(library)
      val sessions = library.koin.get<PresetSessions>()
      waitFor(hasContentDescription(getString(Res.string.more_for, "Plain")))
      tap(hasText(getString(WrapperPrompt.entries.first().title)) and hasClickAction())
      waitFor(hasSetTextAction())

      onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("Write in second person.")
      onAllNodes(hasText(getString(Res.string.save)) and hasClickAction()).onFirst().assertIsEnabled()
      sessions.discard(id)

      waitUntil(timeoutMillis = WAIT_MILLIS) {
        onAllNodes(hasText("Write in second person.")).fetchSemanticsNodes().isEmpty()
      }
    }
  }

  private fun deepFile(): DesktopPickedFile {
    val file = directory.resolve("Deep.json")
    file.writeText(
      """{"prompts": [
        {"identifier": "main", "name": "Main Prompt", "role": "system", "content": "Be vivid."},
        {"identifier": "nsfw", "name": "Auxiliary Prompt", "role": "system", "content": "Stay in character.",
         "injection_position": 1, "injection_depth": 4}
      ],
      "prompt_order": [{"character_id": 100001, "order": [
        {"identifier": "main", "enabled": true}, {"identifier": "nsfw", "enabled": true}
      ]}]}""",
    )
    return DesktopPickedFile(file)
  }

  private fun inChatDepths(library: LiveLibrary, id: Uuid): List<Int?> =
    library.koin.get<PresetSessions>().current(id).unsaved?.draft?.entries.orEmpty()
      .map { (it.placement as? EntryPlacement.InChat)?.depth }

  @Test
  fun anEmptyDepthBlocksSaveWithItsErrorAndAWholeNumberClearsIt() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(deepFile())
      val id = waitForPreset(library)
      waitFor(hasContentDescription(getString(Res.string.more_for, "Deep")))
      tap(hasText("Auxiliary Prompt") and hasClickAction())
      val depthField = hasSetTextAction() and hasText("4")
      waitFor(depthField)

      onAllNodes(depthField).onFirst().performTextReplacement("5")
      onAllNodes(hasSetTextAction() and hasText("5")).onFirst().performTextReplacement("")
      tap(Res.string.save)

      waitFor(hasText(getString(Res.string.depth_error)))
      assertEquals(listOf(null, 5), inChatDepths(library, id))
      assertEquals(4, (library.entries(id).last().placement as EntryPlacement.InChat).depth)

      onAllNodes(hasSetTextAction() and hasText("")).onFirst().performTextReplacement("7")
      val error = hasText(getString(Res.string.depth_error))
      waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(error).fetchSemanticsNodes().isEmpty() }
      assertEquals(listOf(null, 7), inChatDepths(library, id))
    }
  }

  @Test
  fun theAddGroupSheetAddsANamedGroupToTheDraft() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      val id = waitForPreset(library)
      waitFor(hasContentDescription(getString(Res.string.more_for, "Plain")))

      tap(Res.string.add_group)
      waitFor(hasSetTextAction())
      onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("Extras")
      tap(Res.string.add_group_confirm)

      waitUntil(timeoutMillis = WAIT_MILLIS) {
        library.koin.get<PresetSessions>().current(id).unsaved?.draft?.rows.orEmpty()
          .filterIsInstance<PresetGroup>().map { it.name } == listOf("Extras")
      }
    }
  }

  @Test
  fun draggingAnEntryJustPastHalfARowMovesItOnePlace() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPresets(library)
      library.imported(presetFile("Plain.json"))
      val id = waitForPreset(library)
      waitFor(hasContentDescription(getString(Res.string.more_for, "Plain")))
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
