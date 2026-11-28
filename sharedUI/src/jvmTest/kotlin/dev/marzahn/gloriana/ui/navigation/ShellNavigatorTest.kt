package dev.marzahn.gloriana.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import dev.marzahn.gloriana.ui.characters.CharacterRoute
import dev.marzahn.gloriana.ui.characters.openCharacter
import dev.marzahn.gloriana.ui.characters.pickCharacter
import dev.marzahn.gloriana.ui.characters.showLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ShellNavigatorTest {
  private data class Item(val name: String) : NavKey

  private fun navigator(start: Destination = Destination.Library) =
    ShellNavigator(
      Destination.entries.associateWith { mutableListOf(it.root) },
      mutableStateOf(start),
      mutableStateOf(LibraryKind.Characters),
    )

  @Test
  fun opensAtTheStartDestinationsRoot() {
    val navigator = navigator()
    assertEquals(Destination.Start, navigator.current)
    assertEquals(listOf(Destination.Start.root), navigator.stack(Destination.Start))
  }

  @Test
  fun eachDestinationKeepsItsStackWhileTheUserIsElsewhere() {
    val navigator = navigator()
    navigator.push(Item("a"))
    navigator.select(Destination.Chats)
    navigator.push(Item("b"))
    navigator.select(Destination.Library)
    assertEquals(listOf(Destination.Library.root, Item("a")), navigator.stack(Destination.Library))
    assertEquals(listOf(Destination.Chats.root, Item("b")), navigator.stack(Destination.Chats))
  }

  @Test
  fun pickingTheShownDestinationReturnsItToItsRoot() {
    val navigator = navigator()
    navigator.push(Item("a"))
    navigator.push(Item("b"))
    navigator.select(Destination.Library)
    assertEquals(listOf(Destination.Library.root), navigator.stack(Destination.Library))
  }

  @Test
  fun popRemovesThePushedScreenAndStaysInTheDestination() {
    val navigator = navigator()
    navigator.push(Item("a"))
    assertFalse(navigator.canLeaveToHome)
    navigator.pop(Destination.Library)
    assertEquals(Destination.Library, navigator.current)
    assertTrue(navigator.atRoot)
  }

  @Test
  fun popOnARootDoesNothing() {
    val navigator = navigator()
    navigator.pop(Destination.Library)
    assertEquals(listOf(Destination.Library.root), navigator.stack(Destination.Library))
  }

  @Test
  fun aDoubleBackPopsOnceAndThenDoesNothing() {
    val navigator = navigator()
    navigator.push(Item("a"))
    navigator.pop(Destination.Library)
    navigator.pop(Destination.Library)
    assertEquals(listOf(Destination.Library.root), navigator.stack(Destination.Library))
    assertEquals(Destination.Library, navigator.current)
  }

  @Test
  fun backDuringADestinationSwitchPopsTheStackItCameFrom() {
    val navigator = navigator()
    navigator.push(Item("a"))
    navigator.select(Destination.Chats)
    navigator.push(Item("b"))
    navigator.pop(Destination.Library)
    assertEquals(listOf(Destination.Library.root), navigator.stack(Destination.Library))
    assertEquals(listOf(Destination.Chats.root, Item("b")), navigator.stack(Destination.Chats))
    assertEquals(Destination.Chats, navigator.current)
  }

  @Test
  fun librarysAndSettingsRootsLeaveToChats() {
    listOf(Destination.Library, Destination.Settings).forEach { start ->
      val navigator = navigator(start)
      assertTrue(navigator.canLeaveToHome)
      navigator.leaveToHome()
      assertEquals(Destination.Chats, navigator.current)
    }
  }

  @Test
  fun leavingToHomeFromChatsOrWithAPushedScreenDoesNothing() {
    val atChats = navigator(Destination.Chats)
    assertFalse(atChats.canLeaveToHome)
    atChats.leaveToHome()
    assertEquals(Destination.Chats, atChats.current)

    val pushed = navigator()
    pushed.push(Item("a"))
    pushed.leaveToHome()
    assertEquals(Destination.Library, pushed.current)
    assertEquals(listOf(Destination.Library.root, Item("a")), pushed.stack(Destination.Library))
  }

  private class Unsaved {
    var changed = true
    var discards = 0
    val page
      get() = UnsavedPage(
        "Discard?",
        "leave?",
        "page",
        discard = {
          discards++
          changed = false
        },
      ).takeIf { changed }
  }

  private fun guarded(unsaved: Unsaved, key: NavKey = Item("page")) = navigator(Destination.Settings).apply {
    push(key)
    this.unsaved.guard(key) { unsaved.page }
  }

  @Test
  fun leavingAPageWithUnsavedChangesAsksFirst() {
    val unsaved = Unsaved()
    val navigator = guarded(unsaved)

    navigator.pop(Destination.Settings)

    assertEquals(unsaved.page?.leavingMessage, navigator.pendingLeave?.page?.leavingMessage)
    assertEquals(2, navigator.stack(Destination.Settings).size)
  }

  @Test
  fun keepEditingStaysOnThePage() {
    val unsaved = Unsaved()
    val navigator = guarded(unsaved)
    navigator.pop(Destination.Settings)

    checkNotNull(navigator.pendingLeave).keepEditing()

    assertNull(navigator.pendingLeave)
    assertEquals(0, unsaved.discards)
    assertEquals(2, navigator.stack(Destination.Settings).size)
  }

  @Test
  fun discardAndLeaveDropsTheChangesAndLeaves() {
    val unsaved = Unsaved()
    val navigator = guarded(unsaved)
    navigator.pop(Destination.Settings)

    checkNotNull(navigator.pendingLeave).discardAndLeave()

    assertNull(navigator.pendingLeave)
    assertEquals(1, unsaved.discards)
    assertTrue(navigator.atRoot)
    assertTrue(navigator.unsavedPages.isEmpty())
  }

  @Test
  fun aPageWithoutChangesLeavesWithoutAsking() {
    val navigator = guarded(Unsaved().apply { changed = false })

    navigator.replaceTop(Destination.Settings, Item("other"))

    assertNull(navigator.pendingLeave)
    assertEquals(listOf(Destination.Settings.root, Item("other")), navigator.stack(Destination.Settings))
  }

  @Test
  fun replacingTheTopAsksAboutTheUnsavedPageItReplaces() {
    val navigator = guarded(Unsaved())

    navigator.replaceTop(Destination.Settings, Item("other"))
    assertEquals(listOf(Destination.Settings.root, Item("page")), navigator.stack(Destination.Settings))

    checkNotNull(navigator.pendingLeave).discardAndLeave()
    assertEquals(listOf(Destination.Settings.root, Item("other")), navigator.stack(Destination.Settings))
  }

  @Test
  fun closeLeavesWithoutAskingAndForgetsTheGuard() {
    val navigator = guarded(Unsaved())

    navigator.closeWithoutAsking(Destination.Settings, Item("page"))

    assertNull(navigator.pendingLeave)
    assertTrue(navigator.atRoot)
    assertTrue(navigator.unsavedPages.isEmpty())
  }

  @Test
  fun swappingTheShownPageForItsSavedRouteNeverAsks() {
    val navigator = guarded(Unsaved())

    navigator.replaceWithoutAsking(Destination.Settings, shown = Item("page"), with = Item("saved"))

    assertNull(navigator.pendingLeave)
    assertEquals(listOf(Destination.Settings.root, Item("saved")), navigator.stack(Destination.Settings))
  }

  @Test
  fun closingARouteThatIsNotShownChangesNothing() {
    val navigator = guarded(Unsaved())

    navigator.closeWithoutAsking(Destination.Settings, Item("elsewhere"))

    assertEquals(listOf(Destination.Settings.root, Item("page")), navigator.stack(Destination.Settings))
  }

  @Test
  fun quittingListsEveryUnsavedPageAcrossDestinations() {
    val navigator = guarded(Unsaved())
    navigator.select(Destination.Library)
    navigator.push(Item("clean"))
    navigator.unsaved.guard(Item("clean")) { null }

    assertEquals(listOf("page"), navigator.unsavedPages.map { it.quittingName })
  }

  private fun editedPresetPage(unsaved: Unsaved): ShellNavigator = navigator(Destination.Chats).apply {
    showLibraryKind(LibraryKind.Presets) { push(Item("preset"), Destination.Library) }
    this.unsaved.guard(Item("preset")) { unsaved.page }
    select(Destination.Chats)
  }

  @Test
  fun aCharacterSnackbarsOpenOverAnEditedPresetAsksAndThenOpensTheCharacter() {
    val unsaved = Unsaved()
    val navigator = editedPresetPage(unsaved)
    val character = Uuid.random()

    navigator.openCharacter(character)

    assertEquals(Destination.Library, navigator.current)
    assertEquals(LibraryKind.Presets, navigator.libraryKind)
    assertEquals(listOf(Destination.Library.root, Item("preset")), navigator.stack(Destination.Library))
    checkNotNull(navigator.pendingLeave).discardAndLeave()
    assertEquals(1, unsaved.discards)
    assertEquals(LibraryKind.Characters, navigator.libraryKind)
    assertEquals(listOf(Destination.Library.root, CharacterRoute(character)), navigator.stack(Destination.Library))
  }

  @Test
  fun keepEditingOverAnEditedPresetLeavesTheLibraryKindAndStack() {
    val navigator = editedPresetPage(Unsaved())

    navigator.showLibrary()
    checkNotNull(navigator.pendingLeave).keepEditing()

    assertNull(navigator.pendingLeave)
    assertEquals(LibraryKind.Presets, navigator.libraryKind)
    assertEquals(listOf(Destination.Library.root, Item("preset")), navigator.stack(Destination.Library))
  }

  @Test
  fun showingTheLibraryWithoutChangesSwitchesBackToCharacters() {
    val navigator = editedPresetPage(Unsaved().apply { changed = false })

    navigator.showLibrary()

    assertNull(navigator.pendingLeave)
    assertEquals(LibraryKind.Characters, navigator.libraryKind)
    assertTrue(navigator.atRoot)
  }

  @Test
  fun pickingACharacterAsksAboutTheEditedPageItReplaces() {
    val navigator = editedPresetPage(Unsaved())
    navigator.select(Destination.Library)
    val character = Uuid.random()

    navigator.pickCharacter(character)
    assertEquals(listOf(Destination.Library.root, Item("preset")), navigator.stack(Destination.Library))

    checkNotNull(navigator.pendingLeave).discardAndLeave()
    assertEquals(listOf(Destination.Library.root, CharacterRoute(character)), navigator.stack(Destination.Library))
  }

  @Test
  fun leavingAsksAboutEveryUnsavedPageItDrops() {
    val first = Unsaved()
    val second = Unsaved()
    val navigator = guarded(first, Item("first")).apply {
      push(Item("second"))
      unsaved.guard(Item("second")) { second.page }
    }

    navigator.returnToRoot(Destination.Settings)
    checkNotNull(navigator.pendingLeave).discardAndLeave()

    assertEquals(1, first.discards)
    assertEquals(3, navigator.stack(Destination.Settings).size)
    checkNotNull(navigator.pendingLeave).discardAndLeave()
    assertEquals(1, second.discards)
    assertNull(navigator.pendingLeave)
    assertTrue(navigator.atRoot)
  }

  @Test
  fun keepEditingOnALaterPageStaysWithEveryPage() {
    val second = Unsaved()
    val navigator = guarded(Unsaved(), Item("first")).apply {
      push(Item("second"))
      unsaved.guard(Item("second")) { second.page }
    }

    navigator.returnToRoot(Destination.Settings)
    checkNotNull(navigator.pendingLeave).discardAndLeave()
    checkNotNull(navigator.pendingLeave).keepEditing()

    assertEquals(0, second.discards)
    assertEquals(3, navigator.stack(Destination.Settings).size)
  }

  @Test
  fun aPageClearedByAnEarlierDiscardIsNotAskedAgain() {
    val shared = Unsaved()
    val navigator = guarded(shared, Item("first")).apply {
      push(Item("second"))
      unsaved.guard(Item("second")) { shared.page }
    }

    navigator.returnToRoot(Destination.Settings)
    checkNotNull(navigator.pendingLeave).discardAndLeave()

    assertNull(navigator.pendingLeave)
    assertTrue(navigator.atRoot)
  }
}
