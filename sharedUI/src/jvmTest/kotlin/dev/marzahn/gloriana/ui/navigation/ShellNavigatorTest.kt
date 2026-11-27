package dev.marzahn.gloriana.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellNavigatorTest {
  private data class Item(val name: String) : NavKey

  private fun navigator(start: Destination = Destination.Library) =
    ShellNavigator(Destination.entries.associateWith { mutableListOf(it.root) }, mutableStateOf(start))

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
    val page get() = UnsavedPage("leave?", "page", discard = { discards++ }).takeIf { changed }
  }

  private fun guarded(unsaved: Unsaved, key: NavKey = Item("page")) = navigator(Destination.Settings).apply {
    push(key)
    guard(key) { unsaved.page }
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

    navigator.close(Destination.Settings, Item("page"))

    assertNull(navigator.pendingLeave)
    assertTrue(navigator.atRoot)
    assertTrue(navigator.unsavedPages.isEmpty())
  }

  @Test
  fun swappingTheShownPageForItsSavedRouteNeverAsks() {
    val navigator = guarded(Unsaved())

    navigator.swap(Destination.Settings, shown = Item("page"), with = Item("saved"))

    assertNull(navigator.pendingLeave)
    assertEquals(listOf(Destination.Settings.root, Item("saved")), navigator.stack(Destination.Settings))
  }

  @Test
  fun closingARouteThatIsNotShownChangesNothing() {
    val navigator = guarded(Unsaved())

    navigator.close(Destination.Settings, Item("elsewhere"))

    assertEquals(listOf(Destination.Settings.root, Item("page")), navigator.stack(Destination.Settings))
  }

  @Test
  fun quittingListsEveryUnsavedPageAcrossDestinations() {
    val navigator = guarded(Unsaved())
    navigator.select(Destination.Library)
    navigator.push(Item("clean"))
    navigator.guard(Item("clean")) { null }

    assertEquals(listOf("page"), navigator.unsavedPages.map { it.quittingName })
  }
}
