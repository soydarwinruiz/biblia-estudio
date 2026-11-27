package com.biblia.estudio.work

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.biblia.estudio.MainActivity
import com.biblia.estudio.R
import com.biblia.estudio.app
import com.biblia.estudio.data.user.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Recordatorio diario y refresco del widget con AlarmManager.
 * (WorkManager puede retrasar un trabajo varias horas cuando el teléfono está en reposo;
 * una alarma «allow while idle» llega a su hora o con pocos minutos de diferencia.)
 */
object Notifications {
    const val CHANNEL = "daily"
    const val CHANNEL_EVENTS = "events"
    const val CHANNEL_UPDATES = "updates"
    private const val LEGACY_WORK = "daily_verse"
    const val ACTION_REMIND = "com.biblia.estudio.action.REMIND"
    const val ACTION_MIDNIGHT = "com.biblia.estudio.action.MIDNIGHT"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Devocional diario", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Versículo del día y recordatorio de lectura"
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(ch)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_EVENTS, "Agenda de la iglesia", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos de cultos, ayunos, vigilias y demás actividades del Diario"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_UPDATES, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nuevas versiones de Códice vivo y sus novedades"
        })
    }

    private fun pending(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(context, code, Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Milisegundos epoch de la próxima vez que el reloj marque hh:mm. */
    fun nextTrigger(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next
    }

    private fun LocalDateTime.millis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun setAlarm(context: Context, at: Long, pi: PendingIntent, exact: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact && canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun schedule(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        // Versiones ≤ 1.4 usaban WorkManager: se cancela ese trabajo para no duplicar el aviso.
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK) }
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, ACTION_REMIND, 1)
        if (!enabled) am.cancel(pi)
        else setAlarm(context, nextTrigger(LocalDateTime.now(), hour, minute).millis(), pi, exact = true)
        scheduleMidnight(context)
    }

    /** Pasada la medianoche se refresca el widget con el nuevo versículo del día. */
    fun scheduleMidnight(context: Context) =
        setAlarm(context, nextTrigger(LocalDateTime.now(), 0, 1).millis(), pending(context, ACTION_MIDNIGHT, 2), exact = false)

    /** Enlace interno a un pasaje: codicevivo://ref?b=43&c=3&v=16&ve=16 */
    fun refUri(r: com.biblia.estudio.data.content.PassageRef): android.net.Uri =
        android.net.Uri.parse("codicevivo://ref?b=${r.book}&c=${r.chapter}&v=${r.verse}&ve=${r.verseEnd}")

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    suspend fun showDaily(context: Context) {
        if (!canNotify(context)) return
        val c = context.app
        c.init()
        val settings = c.settings.current()
        val ref = c.content.verseOfDay(today())
        val text = c.content.passageText(settings.version, ref)
        val label = c.content.label(ref)
        // Tocar la notificación abre el versículo directamente en el lector
        val intent = Intent(Intent.ACTION_VIEW, refUri(ref), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val due = c.user.dao.dueMemoryCountNow(today())
        val extra = if (due > 0) "\n\nTienes $due versículo${if (due > 1) "s" else ""} para repasar de memoria." else ""
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(com.biblia.estudio.domain.Greeting.now(settings.userName.substringBefore(" ")) + " · " + label)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n\nTu pasaje, lección, oración y preguntas de hoy te esperan.$extra"))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(1, n)
    }
}

/** Recibe las alarmas: muestra el recordatorio o refresca el widget, y programa la siguiente. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                if (intent.action == EventReminders.ACTION_EVENT) {
                    runCatching { EventReminders.show(app, intent.getLongExtra(EventReminders.EXTRA_ID, -1), intent.getLongExtra(EventReminders.EXTRA_AT, 0)) }
                    return@launch
                }
                withTimeoutOrNull(8_000) {
                    if (intent.action == Notifications.ACTION_REMIND) runCatching { Notifications.showDaily(app) }
                    runCatching { VerseWidget.refreshAll(app) }
                }
                val s = app.app.settings.current()
                if (intent.action == Notifications.ACTION_REMIND) Notifications.schedule(app, s.notifications, s.notifyHour, s.notifyMinute)
                else Notifications.scheduleMidnight(app)
            } finally { pending.finish() }
        }
    }
}

/** Tras reiniciar el teléfono, cambiar la hora/zona o actualizar la app, las alarmas se pierden: se vuelven a programar. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                val s = app.app.settings.current()
                Notifications.schedule(app, s.notifications, s.notifyHour, s.notifyMinute)
                AutoBackup.schedule(app, s.autoBackupDays)
                runCatching { EventReminders.rescheduleAll(app) }
                runCatching { UpdateCheck.schedule(app, s.updateCheck) }
                runCatching { VerseWidget.refreshAll(app) }
            } finally { pending.finish() }
        }
    }
}
