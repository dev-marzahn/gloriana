package dev.marzahn.gloriana.android.tasks

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import dev.marzahn.gloriana.logic.tasks.KeepAlive
import dev.marzahn.gloriana.logic.tasks.KeepAliveLosses
import dev.marzahn.gloriana.logic.tasks.TaskHold
import dev.marzahn.gloriana.logic.tasks.TaskNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.minutes

private val cpuHeldAtMostPerTask = 30.minutes

internal const val SESSION_EXTRA = "session"

internal const val NO_SESSION = 0

internal class KeepAliveSession(
  val id: Int,
  val opening: TaskNotice,
  val shown: Flow<TaskNotice?>,
  val losses: KeepAliveLosses,
)

internal inline fun <T> foregroundStartOr(refused: T, sdk: Int, start: () -> T): T = try {
  start()
} catch (failure: IllegalStateException) {
  if (!isForegroundStartRefusal(failure, sdk)) throw failure
  refused
}

@SuppressLint("NewApi")
internal fun isForegroundStartRefusal(failure: IllegalStateException, sdk: Int): Boolean =
  sdk >= Build.VERSION_CODES.S && failure is ForegroundServiceStartNotAllowedException

enum class ServiceStart { Started, Refused }

fun interface TaskServiceStart {
  fun start(session: Int): ServiceStart

  companion object {
    fun of(context: Context) = TaskServiceStart { session ->
      foregroundStartOr(ServiceStart.Refused, Build.VERSION.SDK_INT) {
        context.startForegroundService(Intent(context, TaskService::class.java).putExtra(SESSION_EXTRA, session))
        ServiceStart.Started
      }
    }
  }
}

fun interface WakeLocks {
  fun acquire(): TaskHold

  companion object {
    fun of(context: Context) = WakeLocks {
      val power = context.getSystemService(PowerManager::class.java)
      val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gloriana:task").apply {
        setReferenceCounted(false)
        acquire(cpuHeldAtMostPerTask.inWholeMilliseconds)
      }
      TaskHold { lock.release() }
    }
  }
}

class AndroidKeepAlive(private val service: TaskServiceStart, private val wakeLocks: WakeLocks) : KeepAlive {
  private val current = MutableStateFlow<KeepAliveSession?>(null)
  private var started = NO_SESSION

  internal val session: StateFlow<KeepAliveSession?> = current.asStateFlow()

  @Volatile
  internal var lastStarted: KeepAliveSession? = null
    private set

  override fun start(opening: TaskNotice, shown: Flow<TaskNotice?>, losses: KeepAliveLosses) {
    val session = KeepAliveSession(++started, opening, shown, losses)
    lastStarted = session
    current.value = session
    if (service.start(session.id) == ServiceStart.Refused) losses.refused()
  }

  override fun stop() {
    current.value = null
  }

  override fun holdWhileRunning(): TaskHold = wakeLocks.acquire()
}
