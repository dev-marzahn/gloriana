package dev.marzahn.gloriana.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

@Stable
class ShellNavigator(
  private val stacks: Map<Destination, MutableList<NavKey>>,
  currentState: MutableState<Destination>,
  libraryKindState: MutableState<LibraryKind>,
) {
  internal val unsaved = UnsavedGuards()

  var current: Destination by currentState
    private set

  var libraryKind: LibraryKind by libraryKindState
    private set

  val pendingLeave: PendingLeave? get() = unsaved.pending

  val unsavedPages: List<UnsavedPage> get() = unsaved.pages

  fun stack(destination: Destination): List<NavKey> = stacks.getValue(destination)

  val atRoot: Boolean get() = isAtRoot(current)

  val canLeaveToHome: Boolean get() = atRoot && current != Destination.Home

  var reselections by mutableIntStateOf(0)
    private set

  fun select(destination: Destination) {
    if (destination == current) {
      reselections++
      returnToRoot(current)
    } else {
      current = destination
    }
  }

  fun showLibraryKind(kind: LibraryKind, then: () -> Unit = {}) {
    current = Destination.Library
    returnToRoot(Destination.Library) {
      libraryKind = kind
      then()
    }
  }

  fun push(key: NavKey, destination: Destination = current) {
    stacks.getValue(destination).add(key)
  }

  fun returnToRoot(destination: Destination, then: () -> Unit = {}) {
    leaving(destination) {
      stacks.getValue(destination).popToRoot()
      then()
    }
  }

  fun pop(destination: Destination) {
    val stack = stacks.getValue(destination)
    if (stack.size <= 1) return
    leaving(destination, dropping = listOf(stack.last())) { stack.removeAt(stack.lastIndex) }
  }

  fun leaving(destination: Destination, dropping: List<NavKey> = stack(destination).drop(1), leave: () -> Unit) {
    unsaved.askAbout(dropping) { leaveAndForget(leave) }
  }

  fun replaceWithoutAsking(destination: Destination, shown: NavKey, with: NavKey) {
    leaveAndForget {
      val stack = stacks.getValue(destination)
      val index = stack.indexOf(shown)
      if (index > 0) stack[index] = with
    }
  }

  fun closeWithoutAsking(destination: Destination, shown: NavKey) {
    leaveAndForget {
      val stack = stacks.getValue(destination)
      val index = stack.indexOf(shown)
      if (index > 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
    }
  }

  fun leaveToHome() {
    if (canLeaveToHome) current = Destination.Home
  }

  private fun leaveAndForget(leave: () -> Unit) {
    leave()
    unsaved.keepOnly(stacks.values.flatten().toSet())
  }
}

fun ShellNavigator.isAtRoot(destination: Destination): Boolean = stack(destination).size == 1

fun ShellNavigator.replaceTop(destination: Destination, key: NavKey) =
  returnToRoot(destination) { push(key, destination) }

private fun MutableList<NavKey>.popToRoot() {
  while (size > 1) removeAt(lastIndex)
}

val LocalShellNavigator = staticCompositionLocalOf<ShellNavigator> { error("No Shell above this screen") }

@Composable
internal fun rememberShellNavigator(): ShellNavigator {
  val stacks = Destination.entries.associateWith { rememberNavBackStack(backStackConfiguration, it.root) }
  val current = rememberSaveable { mutableStateOf(Destination.Start) }
  val libraryKind = rememberSaveable { mutableStateOf(LibraryKind.Characters) }
  return remember(stacks, current, libraryKind) { ShellNavigator(stacks, current, libraryKind) }
}
