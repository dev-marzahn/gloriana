package dev.marzahn.gloriana.android.tasks

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.marzahn.gloriana.logic.tasks.TaskNotice
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.koin.android.ext.android.inject

class TaskService : Service() {
  private val keepAlive: AndroidKeepAlive by inject()
  private val serviceScope = MainScope()
  private val notifications by lazy { TaskNotifications(this) }
  private val tasks by lazy {
    TaskForeground(
      keepAlive.session,
      { keepAlive.lastStarted },
      notifications.starting,
      ServiceForeground(),
      serviceScope,
    )
  }
  private var lastStartId = 0

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    lastStartId = startId
    tasks.started(intent?.getIntExtra(SESSION_EXTRA, NO_SESSION) ?: NO_SESSION)
    return START_NOT_STICKY
  }

  override fun onTimeout(startId: Int, fgsType: Int) {
    tasks.timedOut()
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  private inner class ServiceForeground : Foreground {
    override fun enter(notice: TaskNotice): Boolean = foregroundStartOr(refused = false, Build.VERSION.SDK_INT) {
      val notification = notifications.notification(notice)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(TASK_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
      } else {
        startForeground(TASK_NOTIFICATION_ID, notification)
      }
      true
    }

    override fun show(notice: TaskNotice) {
      notifications.show(notice)
    }

    override fun leave() {
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf(lastStartId)
    }
  }
}
