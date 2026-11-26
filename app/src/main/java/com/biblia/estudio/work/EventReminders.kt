package com.biblia.estudio.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.biblia.estudio.MainActivity
import com.biblia.estudio.R
import com.biblia.estudio.app
import com.biblia.estudio.data.user.JournalEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Avisos de las actividades de la agenda del Diario (cultos, ayunos, vigilias…). */
object EventReminders {
    const val ACTION_EVENT = "com.biblia.estudio.action.EVENT"
    const val EXTRA_ID = "event_id"
    private const val BASE = 100_000

    private fun pending(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(context, BASE + id.toInt(),
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_EVENT).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Momento del aviso, o null si no tiene aviso o ya pasó. */
    fun triggerAt(e: JournalEntry, now: Long = System.currentTimeMillis()): Long? {
        if (e.kind != "event" || e.reminderMinutes < 0) return null
        val t = e.eventAt - e.reminderMinutes * 60_000L
        return if (t > now) t else null
    }

    fun schedule(context: Context, e: JournalEntry) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, e.id)
        val at = triggerAt(e) ?: run { am.cancel(pi); return }
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, id: Long) = context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))

    /** Reprograma todos los avisos futuros (tras reiniciar el teléfono o restaurar un respaldo). */
    suspend fun rescheduleAll(context: Context) {
        context.app.user.dao.eventsFrom(System.currentTimeMillis()).forEach { schedule(context, it) }
    }

    suspend fun show(context: Context, id: Long) {
        if (!Notifications.canNotify(context)) return
        val e = context.app.user.dao.journalEntry(id) ?: return
        if (e.kind != "event") return
        val whenTxt = SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale("es")).format(Date(e.eventAt))
        val title = "${e.eventType.ifBlank { "Actividad" }}: ${e.title.ifBlank { e.eventType }}"
        val body = listOfNotNull(whenTxt.replaceFirstChar { it.uppercase() }, e.place.takeIf { it.isNotBlank() }?.let { "📍 $it" }, e.text.takeIf { it.isNotBlank() }).joinToString("\n")
        val open = PendingIntent.getActivity(context, BASE + id.toInt(),
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("codicevivo://journal"), context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(BASE + id.toInt(), n)
    }
}
