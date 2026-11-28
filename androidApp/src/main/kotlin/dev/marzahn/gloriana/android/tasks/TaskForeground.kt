package dev.marzahn.gloriana.android.tasks

import dev.marzahn.gloriana.logic.tasks.TaskNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

internal interface Foreground {
  fun enter(notice: TaskNotice): Boolean

  fun show(notice: TaskNotice)

  fun leave()
}

private class Shown(val session: KeepAliveSession, val notice: TaskNotice)

internal class TaskForeground(
  private val sessions: StateFlow<KeepAliveSession?>,
  private val lastStarted: () -> KeepAliveSession?,
  private val foreground: Foreground,
  private val scope: CoroutineScope,
) {
  private var following: Job? = null
  private var shown: Shown? = null
  private var enteredFor: KeepAliveSession? = null

  fun started(session: Int) {
    val current = sessions.value
    val notice = shown?.takeIf { it.session === current }?.notice ?: (current ?: lastStarted())?.opening
    if (notice == null || !foreground.enter(notice)) {
      current?.takeIf { it.id == session }?.losses?.refused()
      foreground.leave()
      return
    }
    if (current == null) {
      foreground.leave()
      return
    }
    enteredFor = current
    if (following == null) following = scope.launch { follow() }
  }

  fun timedOut() {
    following?.cancel()
    following = null
    foreground.leave()
    enteredFor?.takeIf { it === sessions.value }?.losses?.backgroundTimeRanOut()
    enteredFor = null
  }

  private suspend fun follow() {
    sessions.collectLatest { session ->
      if (session == null) {
        foreground.leave()
      } else {
        session.shown.filterNotNull().collect { notice ->
          shown = Shown(session, notice)
          foreground.show(notice)
        }
      }
    }
  }
}
