package dev.marzahn.gloriana.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey

class UnsavedPage(val leavingMessage: String, val quittingName: String, val discard: () -> Unit)

class PendingLeave(val page: UnsavedPage, val discardAndLeave: () -> Unit, val keepEditing: () -> Unit)

class QuitRequest(val quit: () -> Unit, val stay: () -> Unit)

@Stable
class QuitRequests {
  var pending: QuitRequest? by mutableStateOf(null)
    private set

  fun request(quit: () -> Unit, stay: () -> Unit = {}) {
    if (pending == null) pending = QuitRequest(quit, stay) else stay()
  }

  fun quit() {
    pending?.let {
      pending = null
      it.quit()
    }
  }

  fun stay() {
    pending?.let {
      pending = null
      it.stay()
    }
  }
}

@Composable
fun GuardUnsavedChanges(route: NavKey, page: UnsavedPage?) {
  val navigator = LocalShellNavigator.current
  val current by rememberUpdatedState(page)
  LaunchedEffect(navigator, route) { navigator.guard(route) { current } }
}
