package com.biblia.estudio.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.biblia.estudio.MainActivity
import com.biblia.estudio.R
import com.biblia.estudio.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Aviso de nuevas versiones. Una vez al día (solo con conexión) lee un archivo público y pequeño
 * con el número de la última versión. No envía ningún dato personal. Se puede desactivar en Ajustes.
 */
object UpdateCheck {
    /** Archivo en la raíz de este repositorio (público): se edita al publicar cada versión. */
    const val URL_VERSION = "https://raw.githubusercontent.com/soydarwinruiz/biblia-estudio/main/android-version.json"
    private const val WORK = "update_check"
    private const val NOTIF_UPDATE = 3
    private const val NOTIF_WHATS_NEW = 4

    data class Info(val versionCode: Long, val versionName: String, val url: String, val notes: String)

    fun parse(json: String): Info? = runCatching {
        val o = JSONObject(json)
        Info(o.getLong("versionCode"), o.optString("versionName"), o.getString("url"), o.optString("notes"))
    }.getOrNull()

    fun currentVersion(context: Context): Pair<Long, String> {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        return p.longVersionCode to (p.versionName ?: "")
    }

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    suspend fun fetch(): Info? = withContext(Dispatchers.IO) {
        val con = URL(URL_VERSION).openConnection() as HttpURLConnection
        try {
            con.connectTimeout = 10_000; con.readTimeout = 10_000; con.useCaches = false
            if (con.responseCode != 200) null else parse(con.inputStream.bufferedReader().readText())
        } finally { con.disconnect() }
    }

    /** Busca una versión nueva. Devuelve la información si hay una más nueva que la instalada. */
    suspend fun check(context: Context, notify: Boolean): Info? {
        val info = fetch() ?: return null
        if (info.versionCode <= currentVersion(context).first) return null
        val s = context.app.settings
        if (notify && s.lastUpdateNotified() < info.versionCode) {
            notifyUpdate(context, info)
            s.setLastUpdateNotified(info.versionCode)
        }
        return info
    }

    fun notifyUpdate(context: Context, info: Info) {
        if (!Notifications.canNotify(context)) return
        val open = PendingIntent.getActivity(context, 30, Intent(Intent.ACTION_VIEW, Uri.parse(info.url)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = "Toca para descargar la versión ${info.versionName}." + if (info.notes.isNotBlank()) "\n\n${info.notes}" else ""
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nueva versión de Códice vivo: ${info.versionName}")
            .setContentText("Toca para descargarla")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIF_UPDATE, n)
    }

    /** Tras instalar una versión nueva, avisa una vez con sus novedades. */
    suspend fun notifyIfUpdated(context: Context) {
        val s = context.app.settings
        val (code, name) = currentVersion(context)
        val seen = s.lastSeenVersion()
        if (seen == code) return
        s.setLastSeenVersion(code)
        // seen = 0: instalación nueva, o actualización desde una versión que no guardaba este dato (≤ 1.5)
        val updated = seen in 1 until code || (seen == 0L && s.current().onboarded)
        if (!updated || !Notifications.canNotify(context)) return
        val open = PendingIntent.getActivity(context, 31, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notes = context.getString(R.string.whats_new)
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Códice vivo se actualizó a la versión $name")
            .setContentText("Mira las novedades")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notes))
            .setContentIntent(open).setAutoCancel(true).build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIF_WHATS_NEW, n)
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!applicationContext.app.settings.current().updateCheck) return Result.success()
        return try { UpdateCheck.check(applicationContext, notify = true); Result.success() } catch (e: Exception) { Result.retry() }
    }
}
