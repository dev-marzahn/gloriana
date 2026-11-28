package dev.marzahn.gloriana.android.tasks

import android.app.ForegroundServiceStartNotAllowedException
import android.os.Build
import dev.marzahn.gloriana.logic.tasks.KeepAliveLosses
import dev.marzahn.gloriana.logic.tasks.TaskHold
import dev.marzahn.gloriana.logic.tasks.TaskNotice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class RecordingLosses : KeepAliveLosses {
  val reported = mutableListOf<String>()

  override fun refused() {
    reported += "refused"
  }

  override fun backgroundTimeRanOut() {
    reported += "background time ran out"
  }
}

private class RecordingForeground : Foreground {
  val events = mutableListOf<String>()
  var refuses = false

  override fun enter(notice: TaskNotice): Boolean {
    events += "enter ${notice.detail}"
    return !refuses
  }

  override fun show(notice: TaskNotice) {
    events += "show ${notice.detail}"
  }

  override fun leave() {
    events += "leave"
  }
}

private fun notice(detail: String) = TaskNotice("Importing", detail, null)

private fun session(id: Int, shown: MutableStateFlow<TaskNotice?>, losses: KeepAliveLosses) =
  KeepAliveSession(id, notice("opening $id"), shown, losses)

@OptIn(ExperimentalCoroutinesApi::class)
class TaskForegroundTest {
  private val losses = RecordingLosses()
  private val shown = MutableStateFlow<TaskNotice?>(null)
  private val first = session(1, shown, losses)
  private val sessions = MutableStateFlow<KeepAliveSession?>(first)
  private var lastStarted: KeepAliveSession? = first
  private val foreground = RecordingForeground()

  private fun TestScope.tasks() =
    TaskForeground(
      sessions,
      { lastStarted },
      notice("starting"),
      foreground,
      backgroundScope + UnconfinedTestDispatcher(testScheduler),
    )

  @Test
  fun `the service enters the foreground with the session's opening notice`() = runTest {
    tasks().started(session = 1)
    assertEquals(listOf("enter opening 1"), foreground.events)
  }

  @Test
  fun `a refused foreground start tells its own session and stops the service`() = runTest {
    foreground.refuses = true
    tasks().started(session = 1)
    assertEquals(listOf("refused"), losses.reported)
    assertEquals(listOf("enter opening 1", "leave"), foreground.events)
  }

  @Test
  fun `a refused start of an earlier session tells the current one nothing`() = runTest {
    val later = RecordingLosses()
    sessions.value = session(2, MutableStateFlow(null), later)
    foreground.refuses = true
    tasks().started(session = 1)
    assertEquals(emptyList(), later.reported)
    assertEquals(emptyList(), losses.reported)
  }

  @Test
  fun `the system's time-out leaves the foreground and winds the tasks down`() = runTest {
    val tasks = tasks()
    tasks.started(session = 1)
    tasks.timedOut()
    assertEquals(listOf("background time ran out"), losses.reported)
    assertEquals(listOf("enter opening 1", "leave"), foreground.events)
  }

  @Test
  fun `a time-out after the session ended tells nobody`() = runTest {
    val tasks = tasks()
    tasks.started(session = 1)
    sessions.value = null
    tasks.timedOut()
    assertEquals(emptyList(), losses.reported)
  }

  @Test
  fun `the notification follows the session's notice and leaves when the session ends`() = runTest {
    val tasks = tasks()
    tasks.started(session = 1)
    shown.value = notice("1 of 2")
    shown.value = notice("2 of 2")
    sessions.value = null
    assertEquals(listOf("enter opening 1", "show 1 of 2", "show 2 of 2", "leave"), foreground.events)
  }

  @Test
  fun `a later start keeps the notice already shown`() = runTest {
    val tasks = tasks()
    tasks.started(session = 1)
    shown.value = notice("1 of 2")
    tasks.started(session = 1)
    assertEquals(listOf("enter opening 1", "show 1 of 2", "enter 1 of 2"), foreground.events)
  }

  @Test
  fun `a new session's start never shows the notice of the session before`() = runTest {
    val tasks = tasks()
    tasks.started(session = 1)
    shown.value = notice("1 of 2")
    sessions.value = session(2, MutableStateFlow(null), losses)
    tasks.started(session = 2)
    assertEquals("enter opening 2", foreground.events.last())
  }

  @Test
  fun `a start that comes after its session ended enters with that session's notice and leaves`() = runTest {
    sessions.value = null
    tasks().started(session = 1)
    assertEquals(listOf("enter opening 1", "leave"), foreground.events)
  }

  @Test
  fun `a start before any session enters with the starting notice and leaves`() = runTest {
    sessions.value = null
    lastStarted = null
    tasks().started(session = NO_SESSION)
    assertEquals(listOf("enter starting", "leave"), foreground.events)
  }
}

class ForegroundStartTest {
  private val refusal = ForegroundServiceStartNotAllowedException("not allowed")

  @Test
  fun `a refused foreground start from Android 12 on gives the refused answer`() {
    assertEquals("refused", foregroundStartOr("refused", Build.VERSION_CODES.S) { throw refusal })
  }

  @Test
  fun `a start that goes through gives its own answer`() {
    assertEquals("started", foregroundStartOr("refused", Build.VERSION_CODES.S) { "started" })
  }

  @Test
  fun `before Android 12 nothing counts as a refusal`() {
    assertFailsWith<IllegalStateException> { foregroundStartOr("refused", Build.VERSION_CODES.R) { throw refusal } }
  }

  @Test
  fun `any other failure is passed on`() {
    assertFailsWith<IllegalStateException> {
      foregroundStartOr("refused", Build.VERSION_CODES.S) { throw IllegalStateException("broken") }
    }
  }
}

class AndroidKeepAliveTest {
  private val losses = RecordingLosses()
  private var answer = ServiceStart.Started
  private val startedSessions = mutableListOf<Int>()
  private val keepAlive = AndroidKeepAlive({ session -> answer.also { startedSessions += session } }) { TaskHold { } }

  @Test
  fun `a refused service start is reported as refused`() {
    answer = ServiceStart.Refused
    keepAlive.start(notice("opening"), MutableStateFlow(null), losses)
    assertEquals(listOf("refused"), losses.reported)
  }

  @Test
  fun `each start names a new session and stop ends it`() {
    keepAlive.start(notice("opening"), MutableStateFlow(null), losses)
    keepAlive.stop()
    keepAlive.start(notice("opening"), MutableStateFlow(null), losses)
    assertEquals(listOf(1, 2), startedSessions)
    assertEquals(2, keepAlive.session.value?.id)
    keepAlive.stop()
    assertEquals(null, keepAlive.session.value)
    assertEquals(2, keepAlive.lastStarted?.id)
    assertEquals(emptyList(), losses.reported)
  }
}
