package dev.marzahn.gloriana.logic.tasks

import kotlinx.coroutines.flow.Flow

data class TaskProgress(val done: Int, val of: Int)

data class TaskNotice(val title: String, val detail: String, val progress: TaskProgress?)

interface KeepAliveLosses {
  fun refused()

  fun backgroundTimeRanOut()
}

fun interface TaskHold {
  fun release()
}

interface KeepAlive {
  fun start(opening: TaskNotice, shown: Flow<TaskNotice?>, losses: KeepAliveLosses)

  fun stop()

  fun holdWhileRunning(): TaskHold
}
