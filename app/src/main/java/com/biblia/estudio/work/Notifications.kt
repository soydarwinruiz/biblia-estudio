package com.biblia.estudio.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.biblia.estudio.MainActivity
import com.biblia.estudio.R
import com.biblia.estudio.app
import com.biblia.estudio.data.user.today
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Notifications {
    const val CHANNEL = "daily"
    private const val WORK = "daily_verse"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Devocional diario", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Versículo del día y recordatorio de lectura"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun schedule(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val req = OneTimeWorkRequestBuilder<DailyWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.app
        try {
            c.init()
            val settings = c.settings.current()
            val ref = c.content.verseOfDay(today())
            val text = c.content.passageText(settings.version, ref)
            val label = c.content.label(ref)
            if (Notifications.canNotify(applicationContext)) {
                val intent = Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pi = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val n = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Versículo del día · $label")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n\nTu pasaje, lección, oración y preguntas de hoy te esperan."))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                @Suppress("MissingPermission")
                NotificationManagerCompat.from(applicationContext).notify(1, n)
            }
            Notifications.schedule(applicationContext, settings.notifications, settings.notifyHour, settings.notifyMinute)
        } catch (e: Exception) {
            return Result.retry()
        }
        return Result.success()
    }
}
