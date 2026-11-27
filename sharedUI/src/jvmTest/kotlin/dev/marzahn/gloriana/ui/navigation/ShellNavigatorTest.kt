package dev.marzahn.gloriana.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
