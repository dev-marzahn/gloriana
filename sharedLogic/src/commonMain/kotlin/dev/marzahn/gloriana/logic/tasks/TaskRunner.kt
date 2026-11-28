package dev.marzahn.gloriana.logic.tasks

import dev.marzahn.gloriana.logic.app.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ProcessHold { Released, Held, Refused }

private class BackgroundTimeRanOut : CancellationException("Background time ran out")

private class RunningTask(val notice: Flow<TaskNotice>, val job: Job)

class TaskRunner(private val scope: ApplicationScope, private val keepAlive: KeepAlive) {
  private val running = MutableStateFlow<List<RunningTask>>(emptyList())
  private val mutableProcessHold = MutableStateFlow(ProcessHold.Released)
  private val bookkeeping = Mutex()
  private var holdGeneration = 0

  @OptIn(ExperimentalCoroutinesApi::class)
  private val shown: Flow<TaskNotice?> = running.flatMapLatest { tasks -> tasks.firstOrNull()?.notice ?: flowOf(null) }

  val processHold: StateFlow<ProcessHold> = mutableProcessHold.asStateFlow()

  fun launch(notice: Flow<TaskNotice>, onBackgroundTimeOut: suspend () -> Unit, work: suspend () -> Unit): Job =
    scope.launch {
      val task = RunningTask(notice, coroutineContext.job)
      register(task, opening = notice.first())
      val wakeHold = keepAlive.holdWhileRunning()
      try {
        work()
      } catch (ended: CancellationException) {
        if (ended is BackgroundTimeRanOut) withContext(NonCancellable) { onBackgroundTimeOut() }
        throw ended
      } finally {
        wakeHold.release()
        withContext(NonCancellable) { unregister(task) }
      }
    }

  private suspend fun register(task: RunningTask, opening: TaskNotice) = bookkeeping.withLock {
    val first = running.value.isEmpty()
    running.value += task
    if (first) {
      holdGeneration++
      mutableProcessHold.value = ProcessHold.Held
      keepAlive.start(opening, shown, Losses(holdGeneration))
    }
  }

  private suspend fun unregister(task: RunningTask) = bookkeeping.withLock {
    if (task !in running.value) return@withLock
    running.value -= task
    if (running.value.isEmpty()) release()
  }

  private fun release() {
    keepAlive.stop()
    mutableProcessHold.value = ProcessHold.Released
  }

  private inner class Losses(private val generation: Int) : KeepAliveLosses {
    override fun refused() {
      whileCurrent { mutableProcessHold.value = ProcessHold.Refused }
    }

    override fun backgroundTimeRanOut() {
      whileCurrent {
        val cutOff = running.value
        running.value = emptyList()
        release()
        cutOff.forEach { it.job.cancel(BackgroundTimeRanOut()) }
      }
    }

    private fun whileCurrent(action: () -> Unit) {
      scope.launch {
        bookkeeping.withLock {
          if (generation == holdGeneration && running.value.isNotEmpty()) action()
        }
      }
    }
  }
}
