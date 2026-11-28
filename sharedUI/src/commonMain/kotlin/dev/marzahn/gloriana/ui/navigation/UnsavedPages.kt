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

internal typealias UnsavedPageGuard = () -> UnsavedPage?

@Stable
class UnsavedGuards {
  private val guards = mutableMapOf<NavKey, UnsavedPageGuard>()

  var pending: PendingLeave? by mutableStateOf(null)
    private set

  val pages: List<UnsavedPage> get() = guards.values.mapNotNull { it() }

  internal fun guard(key: NavKey, guard: UnsavedPageGuard) {
    guards[key] = guard
  }

  internal fun keepOnly(shown: Set<NavKey>) {
    guards.keys.retainAll(shown)
  }

  internal fun askAbout(dropping: List<NavKey>, proceed: () -> Unit) {
    val firstUnsaved = dropping.withIndex().firstNotNullOfOrNull { (index, key) ->
      guards[key]?.invoke()?.let { index to it }
    }
    if (firstUnsaved == null) {
      pending = null
      proceed()
      return
    }
    val (index, page) = firstUnsaved
    pending = PendingLeave(
      page = page,
      discardAndLeave = {
        page.discard()
        askAbout(dropping.drop(index + 1), proceed)
      },
      keepEditing = { pending = null },
    )
  }
}

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
  GuardUnsavedChanges(route) { page }
}

@Composable
fun GuardUnsavedChanges(route: NavKey, page: () -> UnsavedPage?) {
  val navigator = LocalShellNavigator.current
  val current by rememberUpdatedState(page)
  LaunchedEffect(navigator, route) { navigator.unsaved.guard(route) { current() } }
}
