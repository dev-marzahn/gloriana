package dev.marzahn.gloriana.desktop.tasks

import dev.marzahn.gloriana.logic.tasks.KeepAlive
import dev.marzahn.gloriana.logic.tasks.KeepAliveLosses
import dev.marzahn.gloriana.logic.tasks.TaskHold
import dev.marzahn.gloriana.logic.tasks.TaskNotice
import kotlinx.coroutines.flow.Flow
import kotlin.concurrent.Volatile

private const val ACTIVITY_REASON = "Gloriana is running a task the user started"

fun interface ProcessActivity {
  fun end()
}

fun interface ProcessActivities {
  fun begin(reason: String): ProcessActivity

  companion object {
    val None = ProcessActivities { ProcessActivity { } }

    fun forOs(osName: String): ProcessActivities =
      if (osName.lowercase().startsWith("mac")) MacProcessActivities() else None
  }
}

class DesktopKeepAlive(private val activities: ProcessActivities) : KeepAlive {
  @Volatile
  private var activity: ProcessActivity? = null

  override fun start(opening: TaskNotice, shown: Flow<TaskNotice?>, losses: KeepAliveLosses) {
    activity?.end()
    activity = activities.begin(ACTIVITY_REASON)
  }

  override fun stop() {
    activity?.end()
    activity = null
  }

  override fun holdWhileRunning(): TaskHold = TaskHold { }
}
