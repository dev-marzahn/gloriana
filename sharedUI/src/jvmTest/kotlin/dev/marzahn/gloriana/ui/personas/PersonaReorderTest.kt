package dev.marzahn.gloriana.ui.personas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.marzahn.gloriana.logic.personas.PersonaWritePoint
import dev.marzahn.gloriana.ui.App
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.create_persona
import dev.marzahn.gloriana.ui.resources.destination_library
import dev.marzahn.gloriana.ui.resources.done
import dev.marzahn.gloriana.ui.resources.library_kind_personas
import dev.marzahn.gloriana.ui.resources.library_kind_presets
import dev.marzahn.gloriana.ui.resources.more_for
import dev.marzahn.gloriana.ui.resources.move_down
import dev.marzahn.gloriana.ui.resources.reorder_kind
import dev.marzahn.gloriana.ui.resources.reorder_move_row
import dev.marzahn.gloriana.ui.resources.reorder_not_moved
import dev.marzahn.gloriana.ui.resources.reorder_not_moved_storage
import dev.marzahn.gloriana.ui.resources.reorder_step
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WAIT_MILLIS = 10_000L
private const val DRAG_STEPS = 12
private const val ROWS_DRAGGED = 1.5f

private val EXPANDED_WIDTH = 1200.dp
private val COMPACT_WIDTH = 400.dp
private val WINDOW_HEIGHT = 900.dp

@OptIn(ExperimentalTestApi::class)
class PersonaReorderTest {
  private class BackInput : NavigationEventInput() {
    fun back() = dispatchOnBackCompleted()
  }

  private val backInput = BackInput()

  private suspend fun ComposeUiTest.openPersonas(library: LiveLibrary, width: Dp = EXPANDED_WIDTH) {
    setContent {
      val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
      remember { dispatcher.addInput(backInput) }
      library.Show { Box(Modifier.size(width, WINDOW_HEIGHT)) { App() } }
    }
    tapLibrary()
    tap(hasText(getString(Res.string.library_kind_personas)) and hasClickAction())
    waitFor(hasText(library.defaultPersona().fields.name))
    waitForIdle()
  }

  private suspend fun ComposeUiTest.tapLibrary() {
    val destination = getString(Res.string.destination_library)
    tap((hasText(destination) or hasContentDescription(destination)) and hasClickAction())
  }

  private fun ComposeUiTest.tap(matcher: SemanticsMatcher) {
    waitFor(matcher)
    onAllNodes(matcher).onFirst().performClick()
  }

  private fun ComposeUiTest.waitFor(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun ComposeUiTest.waitForNone(matcher: SemanticsMatcher) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
  }

  private suspend fun reorderButton() = hasContentDescription(getString(Res.string.reorder_kind, personasKind()))

  private suspend fun personasKind() = getString(Res.string.library_kind_personas)

  private suspend fun doneButton() = hasText(getString(Res.string.done)) and hasClickAction()

  private suspend fun ComposeUiTest.startReordering() {
    tap(reorderButton())
    waitFor(doneButton())
  }

  private suspend fun ComposeUiTest.waitForReorderingToEnd() {
    waitForNone(doneButton())
    waitFor(reorderButton())
  }

  private suspend fun handleMatcher(name: String) = hasContentDescription(getString(Res.string.reorder_move_row, name))

  private suspend fun ComposeUiTest.handle(name: String): SemanticsNodeInteraction {
    val handle = handleMatcher(name)
    waitFor(handle)
    return onAllNodes(handle).onFirst()
  }

  private suspend fun ComposeUiTest.focusedHandle(name: String) =
    handle(name).also { it.performSemanticsAction(SemanticsActions.RequestFocus) }

  private fun movable(name: String) = hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)

  private suspend fun ComposeUiTest.moveDown(name: String) =
    onAllNodes(movable(name)).onFirst().performCustomAccessibilityActionWithLabel(getString(Res.string.move_down))

  private fun LiveLibrary.names() = personas().map { it.fields.name }

  private fun ComposeUiTest.waitForOrder(library: LiveLibrary, names: List<String>) {
    waitUntil(timeoutMillis = WAIT_MILLIS) { library.names() == names }
  }

  @Test
  fun reorderShowsOnlyOnceThereAreTwoPersonas() = runComposeUiTest {
    LiveLibrary.use { library ->
      openPersonas(library)
      waitFor(hasText(getString(Res.string.create)) and hasClickAction())
      assertTrue(onAllNodes(reorderButton()).fetchSemanticsNodes().isEmpty())

      library.createdPersona("Wren")
      waitFor(reorderButton())
    }
  }

  @Test
  fun reorderModeSwapsCreateForDoneHidesTheAddRowAndOpensNothing() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersonas(library, COMPACT_WIDTH)
      startReordering()

      waitForNone(hasText(getString(Res.string.create_persona)))
      handle("Wren")
      assertTrue(onAllNodes(hasText("Wren") and hasClickAction()).fetchSemanticsNodes().isEmpty())

      tap(doneButton())
      waitFor(hasText(getString(Res.string.create_persona)) and hasClickAction())
      tap(hasText("Wren") and hasClickAction())
      waitFor(hasContentDescription(getString(Res.string.more_for, "Wren")))
    }
  }

  @Test
  fun moveDownIsSavedAndKeptAfterARestart() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      val shipped = library.defaultPersona().fields.name
      openPersonas(library)

      moveDown(shipped)

      waitForOrder(library, listOf("Wren", shipped))
      library.restart()
      assertEquals(listOf("Wren", shipped), library.names())
    }
  }

  @Test
  fun spaceLiftsDownMovesAndSpaceDropsTheRow() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      library.createdPersona("Alba")
      val shipped = library.defaultPersona().fields.name
      openPersonas(library)
      startReordering()

      focusedHandle("Wren").performKeyInput {
        pressKey(Key.Spacebar)
        pressKey(Key.DirectionDown)
        pressKey(Key.Spacebar)
      }

      waitForOrder(library, listOf(shipped, "Alba", "Wren"))
    }
  }

  @Test
  fun everyKeyStepIsAnnounced() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      library.createdPersona("Alba")
      val shipped = library.defaultPersona().fields.name
      openPersonas(library)
      startReordering()
      val handle = focusedHandle(shipped)
      handle.performKeyInput { pressKey(Key.Spacebar) }

      handle.performKeyInput { pressKey(Key.DirectionDown) }
      waitFor(hasContentDescription(getString(Res.string.reorder_step, shipped, 2, 3)))
      handle.performKeyInput { pressKey(Key.DirectionDown) }
      waitFor(hasContentDescription(getString(Res.string.reorder_step, shipped, 3, 3)))
    }
  }

  @Test
  fun escapePutsALiftedRowBackThenEndsReordering() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      val shipped = library.defaultPersona().fields.name
      openPersonas(library)
      startReordering()
      val handle = focusedHandle("Wren")

      handle.performKeyInput {
        pressKey(Key.Spacebar)
        pressKey(Key.DirectionUp)
        pressKey(Key.Escape)
      }
      waitForIdle()
      assertTrue(onAllNodes(doneButton()).fetchSemanticsNodes().isNotEmpty())
      handle.performKeyInput { pressKey(Key.Escape) }

      waitForReorderingToEnd()
      assertEquals(listOf(shipped, "Wren"), library.names())
    }
  }

  @Test
  fun escapeWithDoneFocusedEndsReordering() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersonas(library)
      startReordering()
      val done = onAllNodes(doneButton()).onFirst()

      done.performSemanticsAction(SemanticsActions.RequestFocus)
      done.performKeyInput { pressKey(Key.Escape) }

      waitForReorderingToEnd()
    }
  }

  @Test
  fun backEndsReordering() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersonas(library, COMPACT_WIDTH)
      startReordering()

      backInput.back()

      waitForReorderingToEnd()
    }
  }

  @Test
  fun pickingTheLibraryAgainEndsReordering() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersonas(library)
      startReordering()

      tapLibrary()

      waitForReorderingToEnd()
    }
  }

  @Test
  fun switchingTheKindEndsReordering() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      openPersonas(library)
      startReordering()

      tap(hasText(getString(Res.string.library_kind_presets)) and hasClickAction())
      waitForNone(handleMatcher("Wren"))
      tap(hasText(personasKind()) and hasClickAction())

      waitForReorderingToEnd()
    }
  }

  @Test
  fun draggingTheHandleMovesTheRow() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      val shipped = library.defaultPersona().fields.name
      openPersonas(library)
      startReordering()
      val pitch = handle("Wren").fetchSemanticsNode().boundsInRoot.top -
        handle(shipped).fetchSemanticsNode().boundsInRoot.top

      handle(shipped).performTouchInput {
        down(center)
        repeat(DRAG_STEPS) { moveBy(Offset(0f, pitch * ROWS_DRAGGED / DRAG_STEPS)) }
        up()
      }

      waitForOrder(library, listOf("Wren", shipped))
    }
  }

  @Test
  fun aMoveThatFailsPutsTheSavedOrderBackAndSaysSo() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      val shipped = library.defaultPersona().fields.name
      library.failingWrite = PersonaWritePoint.MoveRowsWritten
      openPersonas(library)

      moveDown(shipped)

      waitFor(hasText(getString(Res.string.reorder_not_moved, shipped), substring = true))
      assertEquals(listOf(shipped, "Wren"), library.names())
      val shippedTop = onAllNodes(hasText(shipped)).onFirst().fetchSemanticsNode().boundsInRoot.top
      val wrenTop = onAllNodes(hasText("Wren")).onFirst().fetchSemanticsNode().boundsInRoot.top
      assertTrue(shippedTop < wrenTop)
    }
  }

  @Test
  fun aMoveRefusedForStorageSaysThereIsNotEnoughStorage() = runComposeUiTest {
    LiveLibrary.use { library ->
      library.createdPersona("Wren")
      val shipped = library.defaultPersona().fields.name
      library.storageFullAt = PersonaWritePoint.MoveRowsWritten
      openPersonas(library)

      moveDown(shipped)

      waitFor(hasText(getString(Res.string.reorder_not_moved_storage, shipped), substring = true))
      assertEquals(listOf(shipped, "Wren"), library.names())
    }
  }
}
