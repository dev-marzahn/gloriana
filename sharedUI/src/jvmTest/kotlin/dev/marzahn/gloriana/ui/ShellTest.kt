package dev.marzahn.gloriana.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.marzahn.gloriana.ui.chats.ChatsRoute
import dev.marzahn.gloriana.ui.chats.ChatsScreen
import dev.marzahn.gloriana.ui.components.EmptyBlock
import dev.marzahn.gloriana.ui.library.LibraryContent
import dev.marzahn.gloriana.ui.library.LibraryRoute
import dev.marzahn.gloriana.ui.library.LibraryState
import dev.marzahn.gloriana.ui.navigation.Destination
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import dev.marzahn.gloriana.ui.navigation.TopLevelContent
import dev.marzahn.gloriana.ui.navigation.TopLevelScreen
import dev.marzahn.gloriana.ui.navigation.WhileRootShown
import dev.marzahn.gloriana.ui.navigation.rememberShellNavigator
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.app_name
import dev.marzahn.gloriana.ui.resources.chats_empty_heading
import dev.marzahn.gloriana.ui.resources.create
import dev.marzahn.gloriana.ui.resources.destination_chats
import dev.marzahn.gloriana.ui.resources.destination_library
import dev.marzahn.gloriana.ui.resources.destination_settings
import dev.marzahn.gloriana.ui.resources.ic_person_add
import dev.marzahn.gloriana.ui.resources.library_empty
import dev.marzahn.gloriana.ui.settings.ConnectionsList
import dev.marzahn.gloriana.ui.settings.SettingsActions
import dev.marzahn.gloriana.ui.settings.SettingsContent
import dev.marzahn.gloriana.ui.settings.SettingsRoute
import dev.marzahn.gloriana.ui.settings.SettingsState
import dev.marzahn.gloriana.ui.testing.rememberUnstoredMoves
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val WindowHeight = 700.dp

@OptIn(ExperimentalTestApi::class)
class ShellTest {
  private data object Pushed : NavKey

  private class BackInput : NavigationEventInput() {
    fun back() = dispatchOnBackCompleted()
  }

  private class Harness(val back: BackInput) {
    lateinit var navigator: ShellNavigator
  }

  private fun ComposeUiTest.showApp(
    width: Dp,
    hasSystemBack: Boolean = false,
    library: @Composable () -> Unit = { LibraryContent(LibraryState.Empty) },
  ): Harness {
    val harness = Harness(BackInput())
    setContent {
      val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
      remember { dispatcher.addInput(harness.back) }
      harness.navigator = rememberShellNavigator()
      Box(Modifier.size(width, WindowHeight)) {
        GlorianaApp(
          entryProvider<NavKey> {
            entry<ChatsRoute> { ChatsScreen() }
            entry<LibraryRoute> { library() }
            entry<SettingsRoute> {
              SettingsContent(
                SettingsState(ConnectionsList.Loaded(emptyList())),
                SettingsActions(onOpen = {}, onCreate = {}, onIntent = {}),
                selected = null,
                moves = rememberUnstoredMoves(),
              )
            }
            entry<Pushed> { Text(PUSHED) }
          },
          navigator = harness.navigator,
          hasSystemBack = hasSystemBack,
        )
      }
    }
    return harness
  }

  @Test
  fun compactOpensToTheEmptyLibraryWithTheBottomBar() = runComposeUiTest {
    showApp(400.dp)
    onNodeWithText(getString(Res.string.library_empty)).assertIsDisplayed()
    assertEquals(2, onAllNodesWithText(getString(Res.string.destination_library)).fetchSemanticsNodes().size)
    val settings = onNodeWithText(getString(Res.string.destination_settings)).getBoundsInRoot()
    assertTrue(settings.top > WindowHeight - 100.dp, "the bar sits at the bottom")
  }

  @Test
  fun pickingADestinationShowsIt() = runComposeUiTest {
    showApp(400.dp)
    onNodeWithText(getString(Res.string.destination_chats)).performClick()
    waitForIdle()
    onNodeWithText(getString(Res.string.chats_empty_heading)).assertIsDisplayed()
  }

  @Test
  fun mediumShowsTheLabelledRailInsteadOfTheBar() = runComposeUiTest {
    showApp(700.dp)
    val settings = onNodeWithText(getString(Res.string.destination_settings)).getBoundsInRoot()
    assertTrue(settings.right < 100.dp, "the labels sit in the rail at the start")
  }

  @Test
  fun expandedShowsTheWorkbenchWithAnIconOnlyRail() = runComposeUiTest {
    showApp(1000.dp)
    onNodeWithText(getString(Res.string.library_empty)).assertIsDisplayed()
    onNodeWithContentDescription(getString(Res.string.destination_settings)).assertExists()
    onNodeWithText(getString(Res.string.destination_settings)).assertDoesNotExist()
    assertEquals(1, onAllNodesWithText(getString(Res.string.destination_library)).fetchSemanticsNodes().size)
  }

  @Test
  fun expandedOpensAPushedScreenInTheDetailPaneBesideTheList() = runComposeUiTest {
    val harness = showApp(1000.dp)
    runOnIdle { harness.navigator.push(Pushed) }
    onNodeWithText(PUSHED).assertIsDisplayed()
    assertTrue(onNodeWithText(PUSHED).getBoundsInRoot().left > 330.dp)
    onNodeWithText(getString(Res.string.destination_library)).assertIsDisplayed()
  }

  private fun ComposeUiTest.backPopsAPushedScreen(hasSystemBack: Boolean) {
    val harness = showApp(400.dp, hasSystemBack = hasSystemBack)
    runOnIdle { harness.navigator.push(Pushed) }
    onNodeWithText(PUSHED).assertIsDisplayed()
    runOnIdle { harness.back.back() }
    waitForIdle()
    onNodeWithText(PUSHED).assertDoesNotExist()
    assertEquals(Destination.Library, harness.navigator.current)
    assertEquals(listOf(Destination.Library.root), harness.navigator.stack(Destination.Library))
  }

  @Test
  fun backWithoutSystemBackPopsAPushedScreen() = runComposeUiTest { backPopsAPushedScreen(hasSystemBack = false) }

  @Test
  fun systemBackPopsAPushedScreenAndStaysInTheDestination() =
    runComposeUiTest { backPopsAPushedScreen(hasSystemBack = true) }

  @Test
  fun systemBackOnLibrarysRootGoesToChats() = runComposeUiTest {
    val harness = showApp(400.dp, hasSystemBack = true)
    runOnIdle { harness.back.back() }
    waitForIdle()
    assertEquals(Destination.Chats, harness.navigator.current)
  }

  @Test
  fun backWithoutSystemBackOnATopLevelRootDoesNothing() = runComposeUiTest {
    val harness = showApp(1000.dp, hasSystemBack = false)
    runOnIdle { harness.back.back() }
    waitForIdle()
    assertEquals(Destination.Library, harness.navigator.current)
  }

  @Test
  fun aDoubleBackPopsOnceAndDoesNotCrash() = runComposeUiTest {
    val harness = showApp(400.dp, hasSystemBack = true)
    runOnIdle { harness.navigator.push(Pushed) }
    waitForIdle()
    runOnIdle {
      harness.back.back()
      harness.back.back()
    }
    waitForIdle()
    assertEquals(listOf(Destination.Library.root), harness.navigator.stack(Destination.Library))
  }

  @Test
  fun backDuringADestinationSwitchDoesNotCrash() = runComposeUiTest {
    val harness = showApp(400.dp)
    runOnIdle { harness.navigator.push(Pushed) }
    waitForIdle()
    mainClock.autoAdvance = false
    runOnIdle { harness.navigator.select(Destination.Chats) }
    mainClock.advanceTimeBy(SWITCH_MIDWAY_MILLIS)
    runOnIdle { harness.back.back() }
    mainClock.autoAdvance = true
    waitForIdle()
    assertEquals(Destination.Chats, harness.navigator.current)
    assertEquals(listOf(Destination.Chats.root), harness.navigator.stack(Destination.Chats))
  }

  @Test
  fun workbenchRailIsOneSelectableGroup() = runComposeUiTest {
    showApp(1000.dp)
    val chats = hasContentDescription(getString(Res.string.destination_chats))
    onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup) and hasAnyDescendant(chats))
      .assertExists()
  }

  @Test
  fun workbenchRailSpacingFollowsTheLayout() = runComposeUiTest {
    showApp(1000.dp)
    val mark = onNodeWithContentDescription(getString(Res.string.app_name)).getBoundsInRoot()
    val chats = onNodeWithContentDescription(getString(Res.string.destination_chats)).getBoundsInRoot()
    val library = onNodeWithContentDescription(getString(Res.string.destination_library)).getBoundsInRoot()
    val settings = onNodeWithContentDescription(getString(Res.string.destination_settings)).getBoundsInRoot()
    assertEquals(12.dp, mark.top, "top edge")
    assertEquals(14.dp, chats.top - mark.bottom, "mark to first item")
    assertEquals(6.dp, library.top - chats.bottom, "between items")
    assertEquals(12.dp, WindowHeight - settings.bottom, "bottom edge")
  }

  private val creatingLibrary: @Composable () -> Unit = {
    val navigator = LocalShellNavigator.current
    val content = TopLevelContent.Items(nothingPicked, onCreate = { navigator.push(Pushed) }) { header ->
      Column { header() }
    }
    TopLevelScreen(Destination.Library, content)
  }

  @Test
  fun expandedShowsNothingPickedInTheDetailPaneWhileNothingIsPushed() = runComposeUiTest {
    val harness = showApp(1000.dp, library = creatingLibrary)
    onNodeWithText(NOTHING_PICKED).assertIsDisplayed()
    assertTrue(onNodeWithText(NOTHING_PICKED).getBoundsInRoot().left > 330.dp)
    runOnIdle { harness.navigator.push(Pushed) }
    waitForIdle()
    onNodeWithText(NOTHING_PICKED).assertDoesNotExist()
  }

  @Test
  fun createSitsInTheTitleRowBelowExpandedAndRunsItsAction() = runComposeUiTest {
    val harness = showApp(400.dp, library = creatingLibrary)
    onNodeWithText(getString(Res.string.create)).performClick()
    waitForIdle()
    onNodeWithText(PUSHED).assertIsDisplayed()
    assertEquals(listOf(Destination.Library.root, Pushed), harness.navigator.stack(Destination.Library))
  }

  @Test
  fun createSitsInTheTopBarOnExpanded() = runComposeUiTest {
    showApp(1000.dp, library = creatingLibrary)
    val create = onNodeWithText(getString(Res.string.create)).getBoundsInRoot()
    assertTrue(create.right > 950.dp && create.bottom < 48.dp, "Create ends the top bar: $create")
  }

  private class OpeningLibrary {
    val pages = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)
    val listening: Boolean get() = pages.subscriptionCount.value > 0

    val screen: @Composable () -> Unit = {
      val navigator = LocalShellNavigator.current
      WhileRootShown(Destination.Library) { pages.collect { navigator.push(it, Destination.Library) } }
      LibraryContent(LibraryState.Empty)
    }
  }

  @Test
  fun libraryOpensAPageWhileItsRootIsShown() = runComposeUiTest {
    val library = OpeningLibrary()
    val harness = showApp(400.dp, library = library.screen)
    waitForIdle()
    assertTrue(library.listening)
    runOnIdle { library.pages.tryEmit(Pushed) }
    waitForIdle()
    assertEquals(listOf(Destination.Library.root, Pushed), harness.navigator.stack(Destination.Library))
  }

  @Test
  fun libraryStopsListeningWhileItFadesOut() = runComposeUiTest {
    val library = OpeningLibrary()
    val harness = showApp(400.dp, library = library.screen)
    waitForIdle()
    mainClock.autoAdvance = false
    runOnIdle { harness.navigator.select(Destination.Chats) }
    mainClock.advanceTimeBy(SWITCH_MIDWAY_MILLIS)
    onNodeWithText(getString(Res.string.library_empty)).assertExists()
    assertFalse(library.listening, "Library still fading out counts as not shown")
  }

  @Test
  fun libraryStopsListeningAsAScreenSlidesOverIt() = runComposeUiTest {
    val library = OpeningLibrary()
    val harness = showApp(400.dp, library = library.screen)
    waitForIdle()
    mainClock.autoAdvance = false
    runOnIdle { harness.navigator.push(Pushed) }
    mainClock.advanceTimeBy(PUSH_MIDWAY_MILLIS)
    onNodeWithText(getString(Res.string.library_empty)).assertExists()
    assertFalse(library.listening, "Library sliding under a push counts as not shown")
  }

  @Test
  fun libraryKeepsListeningBesideTheDetailPane() = runComposeUiTest {
    val library = OpeningLibrary()
    val harness = showApp(1000.dp, library = library.screen)
    runOnIdle { harness.navigator.push(Pushed) }
    waitForIdle()
    assertTrue(library.listening, "the list pane is still on screen beside a pushed page")
  }

  private val nothingPicked = EmptyBlock(Res.drawable.ic_person_add, NOTHING_PICKED, PUSHED)

  private companion object {
    const val PUSHED = "pushed"
    const val NOTHING_PICKED = "nothing picked"
    const val SWITCH_MIDWAY_MILLIS = 100L
    const val PUSH_MIDWAY_MILLIS = 100L
  }
}
