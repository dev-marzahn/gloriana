package dev.marzahn.gloriana.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

internal typealias UnsavedPageGuard = () -> UnsavedPage?

@Stable
class ShellNavigator(
  private val stacks: Map<Destination, MutableList<NavKey>>,
  currentState: MutableState<Destination>,
) {
  private val guards = mutableMapOf<NavKey, UnsavedPageGuard>()

  var current: Destination by currentState
    private set

  var pendingLeave: PendingLeave? by mutableStateOf(null)
    private set

  fun stack(destination: Destination): List<NavKey> = stacks.getValue(destination)

  val atRoot: Boolean get() = stack(current).size == 1

  val canLeaveToHome: Boolean get() = atRoot && current != Destination.Home

  val unsavedPages: List<UnsavedPage> get() = guards.values.mapNotNull { it() }

  fun select(destination: Destination) {
    if (destination == current) leaving(current) { stacks.getValue(current).popToRoot() } else current = destination
  }

  fun push(key: NavKey) {
    stacks.getValue(current).add(key)
  }

  fun pop(destination: Destination) {
    leaving(destination) {
      val stack = stacks.getValue(destination)
      if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
  }

  fun replaceTop(destination: Destination, key: NavKey) {
    leaving(destination) {
      val stack = stacks.getValue(destination)
      stack.popToRoot()
      stack.add(key)
    }
  }

  fun swap(destination: Destination, shown: NavKey, with: NavKey) {
    leaveAndForget {
      val stack = stacks.getValue(destination)
      val index = stack.indexOf(shown)
      if (index > 0) stack[index] = with
    }
  }

  fun leaving(destination: Destination, leave: () -> Unit) {
    val page = stack(destination).drop(1).firstNotNullOfOrNull { guards[it]?.invoke() }
    pendingLeave = page?.let {
      PendingLeave(
        page = it,
        discardAndLeave = {
          pendingLeave = null
          it.discard()
          leaveAndForget(leave)
        },
        keepEditing = { pendingLeave = null },
      )
    }
    if (page == null) leaveAndForget(leave)
  }

  fun close(destination: Destination, shown: NavKey) {
    leaveAndForget {
      val stack = stacks.getValue(destination)
      val index = stack.indexOf(shown)
      if (index > 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
    }
  }

  fun leaveToHome() {
    if (canLeaveToHome) current = Destination.Home
  }

  internal fun guard(key: NavKey, guard: UnsavedPageGuard) {
    guards[key] = guard
  }

  private fun leaveAndForget(leave: () -> Unit) {
    leave()
    val shown = stacks.values.flatten().toSet()
    guards.keys.retainAll(shown)
  }
}

private fun MutableList<NavKey>.popToRoot() {
  while (size > 1) removeAt(lastIndex)
}

val LocalShellNavigator = staticCompositionLocalOf<ShellNavigator> { error("No Shell above this screen") }

@Composable
internal fun rememberShellNavigator(): ShellNavigator {
  val stacks = Destination.entries.associateWith { rememberNavBackStack(backStackConfiguration, it.root) }
  val current = rememberSaveable { mutableStateOf(Destination.Start) }
  return remember(stacks, current) { ShellNavigator(stacks, current) }
}
