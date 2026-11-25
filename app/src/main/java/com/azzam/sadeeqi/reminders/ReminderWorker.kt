package com.azzam.sadeeqi.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.azzam.sadeeqi.MainActivity
import com.azzam.sadeeqi.R

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "تذكيرات صديقي",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "تنبيهات المواعيد والمهام التي تحددها في صديقي" }
            )
        }

        val openApp = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("تذكير من صديقي")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext)
                .notify(id.hashCode(), notification)
            ReminderScheduler.markDelivered(applicationContext, id)
            Result.success()
        } catch (_: SecurityException) {
            ReminderScheduler.markDelivered(applicationContext, id)
            Result.failure()
        }
    }

    companion object {
        const val KEY_ID = "reminder_id"
        const val KEY_TITLE = "reminder_title"
        private const val CHANNEL_ID = "sadeeqi_reminders"
    }
}
