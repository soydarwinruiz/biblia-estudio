package com.biblia.estudio.work

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.biblia.estudio.MainActivity
import com.biblia.estudio.R
import com.biblia.estudio.app
import com.biblia.estudio.data.content.ContentRepository
import com.biblia.estudio.data.user.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Widget de pantalla de inicio con el versículo del día. */
class VerseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val c = context.app
            val prepared = context.getDatabasePath(ContentRepository.DB_NAME).exists()
            val (text, ref) = if (!prepared) "Abre la app una vez para preparar la biblioteca." to "Códice vivo"
            else runCatching {
                c.init()
                val version = c.settings.current().version
                val r = c.content.verseOfDay(today())
                c.content.passageText(version, r) to "${c.content.label(r)} · $version"
            }.getOrElse { "Toca para abrir la Biblia." to "Códice vivo" }
            val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_verse).apply {
                    setTextViewText(R.id.widget_text, "«$text»")
                    setTextViewText(R.id.widget_ref, ref)
                    setOnClickPendingIntent(R.id.widget_root, intent)
                }
                manager.updateAppWidget(id, views)
            }
        }

        /** Refresca todos los widgets (se llama a diario y al cambiar la versión). */
        suspend fun refreshAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, VerseWidget::class.java))
            if (ids.isNotEmpty()) render(context, m, ids)
            runCatching { ProgressWidget.refreshAll(context) }
        }
    }
}
