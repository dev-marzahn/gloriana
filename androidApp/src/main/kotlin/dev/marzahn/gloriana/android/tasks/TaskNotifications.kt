package dev.marzahn.gloriana.android.tasks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.marzahn.gloriana.android.MainActivity
import dev.marzahn.gloriana.android.R
import dev.marzahn.gloriana.logic.tasks.TaskNotice

private const val CHANNEL_ID = "tasks"

internal const val TASK_NOTIFICATION_ID = 1

internal class TaskNotifications(private val context: Context) {
  private val manager = context.getSystemService(NotificationManager::class.java)
  private val openApp = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    PendingIntent.FLAG_IMMUTABLE,
  )

  private val channelName = context.getString(R.string.task_channel_name)

  val starting = TaskNotice(title = channelName, detail = "", progress = null)

  init {
    manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW))
  }

  fun show(notice: TaskNotice) {
    manager.notify(TASK_NOTIFICATION_ID, notification(notice))
  }

  fun notification(notice: TaskNotice): Notification = builder()
    .setContentTitle(notice.title)
    .setContentText(notice.detail)
    .apply { notice.progress?.let { setProgress(it.of, it.done, false) } }
    .build()

  private fun builder(): Notification.Builder = Notification.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_notification)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setShowWhen(false)
    .setContentIntent(openApp)
}
