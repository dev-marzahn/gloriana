package dev.marzahn.gloriana.logic.tasks

import dev.marzahn.gloriana.logic.app.ApplicationScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingKeepAlive : KeepAlive {
  val events = mutableListOf<String>()
  val sessions = mutableListOf<Pair<Flow<TaskNotice?>, KeepAliveLosses>>()
  val openings = mutableListOf<String>()
  var wakeHolds = 0

  override fun start(opening: TaskNotice, shown: Flow<TaskNotice?>, losses: KeepAliveLosses) {
    sessions += shown to losses
    openings += opening.title
    events += "start"
  }

  override fun stop() {
    events += "stop"
  }

  override fun holdWhileRunning(): TaskHold {
    wakeHolds++
    return TaskHold { wakeHolds-- }
  }
}

private fun notice(title: String) = flowOf(TaskNotice(title, "", null))

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRunnerTest {
  private val dispatcher = StandardTestDispatcher()
  private val scope = ApplicationScope(dispatcher) { throw it }
  private val keepAlive = RecordingKeepAlive()
  private val runner = TaskRunner(scope, keepAlive)

  @AfterTest
  fun stop() = scope.cancel()

  private fun TaskRunner.launchUntil(
    release: CompletableDeferred<Unit>,
    title: String = "task",
    onBackgroundTimeOut: suspend () -> Unit = {},
  ) = launch(notice(title), onBackgroundTimeOut) { release.await() }

  @Test
  fun `a task outlives the screen that started it`() = runTest(dispatcher) {
    val screen = CoroutineScope(Job())
    val release = CompletableDeferred<Unit>()
    var finished = false
    screen.launch { runner.launch(notice("task"), {}) { release.await().also { finished = true } } }.join()
    advanceUntilIdle()
    screen.cancel()
    release.complete(Unit)
    advanceUntilIdle()
    assertEquals(true, finished)
  }

  @Test
  fun `the process is held from the first task until the last one ends`() = runTest(dispatcher) {
    val first = CompletableDeferred<Unit>()
    val second = CompletableDeferred<Unit>()
    runner.launchUntil(first)
    runner.launchUntil(second)
    advanceUntilIdle()
    assertEquals(listOf("start"), keepAlive.events)
    assertEquals(ProcessHold.Held, runner.processHold.value)
    assertEquals(2, keepAlive.wakeHolds)
    first.complete(Unit)
    advanceUntilIdle()
    assertEquals(listOf("start"), keepAlive.events)
    assertEquals(1, keepAlive.wakeHolds)
    second.complete(Unit)
    advanceUntilIdle()
    assertEquals(listOf("start", "stop"), keepAlive.events)
    assertEquals(ProcessHold.Released, runner.processHold.value)
    assertEquals(0, keepAlive.wakeHolds)
  }

  @Test
  fun `the notice shown is the oldest running task's`() = runTest(dispatcher) {
    val first = CompletableDeferred<Unit>()
    val second = CompletableDeferred<Unit>()
    runner.launchUntil(first, title = "first")
    runner.launchUntil(second, title = "second")
    advanceUntilIdle()
    val shown = keepAlive.sessions.single().first
    assertEquals(listOf("first"), keepAlive.openings)
    assertEquals("first", shown.first()?.title)
    first.complete(Unit)
    advanceUntilIdle()
    assertEquals("second", shown.first()?.title)
    second.complete(Unit)
  }

  @Test
  fun `a refused start lets the task run on and says so until it ends`() = runTest(dispatcher) {
    val release = CompletableDeferred<Unit>()
    var finished = false
    runner.launch(notice("task"), {}) { release.await().also { finished = true } }
    advanceUntilIdle()
    keepAlive.sessions.single().second.refused()
    advanceUntilIdle()
    assertEquals(ProcessHold.Refused, runner.processHold.value)
    release.complete(Unit)
    advanceUntilIdle()
    assertEquals(true, finished)
    assertEquals(ProcessHold.Released, runner.processHold.value)
  }

  @Test
  fun `a refusal from an earlier hold changes nothing`() = runTest(dispatcher) {
    val first = CompletableDeferred<Unit>().apply { complete(Unit) }
    runner.launchUntil(first)
    advanceUntilIdle()
    val second = CompletableDeferred<Unit>()
    runner.launchUntil(second)
    advanceUntilIdle()
    keepAlive.sessions.first().second.refused()
    advanceUntilIdle()
    assertEquals(ProcessHold.Held, runner.processHold.value)
    second.complete(Unit)
  }

  @Test
  fun `background time running out winds down every task and releases the process`() = runTest(dispatcher) {
    val wound = mutableListOf<String>()
    var cancelled = 0
    repeat(2) { index ->
      runner.launch(notice("task"), { wound += "task $index" }) {
        try {
          awaitCancellation()
        } finally {
          cancelled++
        }
      }
    }
    advanceUntilIdle()
    keepAlive.sessions.single().second.backgroundTimeRanOut()
    advanceUntilIdle()
    assertEquals(2, cancelled)
    assertEquals(listOf("task 0", "task 1"), wound)
    assertEquals(listOf("start", "stop"), keepAlive.events)
    assertEquals(ProcessHold.Released, runner.processHold.value)
    assertEquals(0, keepAlive.wakeHolds)
  }

  @Test
  fun `a task that finishes on its own never winds down`() = runTest(dispatcher) {
    var wound = false
    runner.launch(notice("task"), { wound = true }) { }
    advanceUntilIdle()
    keepAlive.sessions.single().second.backgroundTimeRanOut()
    advanceUntilIdle()
    assertEquals(false, wound)
  }

  @Test
  fun `a task started after background time ran out holds the process again`() = runTest(dispatcher) {
    runner.launch(notice("task"), {}) { awaitCancellation() }
    advanceUntilIdle()
    keepAlive.sessions.single().second.backgroundTimeRanOut()
    val release = CompletableDeferred<Unit>()
    runner.launchUntil(release)
    advanceUntilIdle()
    assertEquals(listOf("start", "stop", "start"), keepAlive.events)
    assertEquals(ProcessHold.Held, runner.processHold.value)
    release.complete(Unit)
  }
}
