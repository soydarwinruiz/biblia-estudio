package com.biblia.estudio.work

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.biblia.estudio.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Respaldo automático de los datos personales en una carpeta elegida por el usuario
 * (memoria interna, tarjeta SD o una carpeta sincronizada por otra app). No usa Internet.
 * Conserva los últimos [KEEP] archivos.
 */
object AutoBackup {
    private const val WORK = "auto_backup"
    private const val PREFIX = "codice-vivo-auto-"
    const val KEEP = 7

    fun schedule(context: Context, days: Int) {
        val wm = WorkManager.getInstance(context)
        if (days <= 0) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(12, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Escribe un respaldo ahora mismo. Devuelve el nombre del archivo creado. */
    suspend fun runNow(context: Context): String = withContext(Dispatchers.IO) {
        val c = context.app
        val s = c.settings.current()
        require(s.autoBackupUri.isNotBlank()) { "No hay carpeta de respaldo elegida" }
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(s.autoBackupUri)) ?: error("No se puede abrir la carpeta")
        require(dir.canWrite()) { "Sin permiso para escribir en la carpeta" }
        val name = "$PREFIX${LocalDate.now()}.json"
        dir.findFile(name)?.delete()
        val file = dir.createFile("application/json", name) ?: error("No se pudo crear el archivo")
        val json = c.user.exportJson()
        context.contentResolver.openOutputStream(file.uri)?.use { it.write(json.toByteArray()) } ?: error("No se pudo escribir")
        // Borrar los más antiguos
        dir.listFiles().filter { it.name?.startsWith(PREFIX) == true }.sortedByDescending { it.name }.drop(KEEP).forEach { it.delete() }
        c.settings.setLastAutoBackup(System.currentTimeMillis())
        name
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.app
        val s = c.settings.current()
        if (s.autoBackupDays <= 0 || s.autoBackupUri.isBlank()) return Result.success()
        val due = c.settings.lastAutoBackup() + s.autoBackupDays * 86_400_000L - 3_600_000L
        if (System.currentTimeMillis() < due) return Result.success()
        return try { AutoBackup.runNow(applicationContext); Result.success() } catch (e: Exception) { Result.retry() }
    }
}
