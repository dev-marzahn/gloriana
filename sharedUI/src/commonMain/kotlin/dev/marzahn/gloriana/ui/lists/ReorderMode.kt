package dev.marzahn.gloriana.ui.lists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.marzahn.gloriana.ui.navigation.LocalShellNavigator

@Stable
class ReorderMode(state: MutableState<Boolean>) {
  var active by state
    private set

  fun start() {
    active = true
  }

  fun end() {
    active = false
  }
}

@Composable
fun rememberReorderMode(): ReorderMode {
  val state = rememberSaveable { mutableStateOf(false) }
  val mode = remember(state) { ReorderMode(state) }
  val reselections = LocalShellNavigator.current.reselections
  val reselectionsAtStart = remember { reselections }
  LaunchedEffect(reselections) { if (reselections != reselectionsAtStart) mode.end() }
  NavigationBackHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = mode.active,
    onBackCompleted = mode::end,
  )
  return mode
}
