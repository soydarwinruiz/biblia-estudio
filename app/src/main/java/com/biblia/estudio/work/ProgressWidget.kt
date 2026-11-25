package com.biblia.estudio.work

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.biblia.estudio.MainActivity
import com.biblia.estudio.R
import com.biblia.estudio.app
import com.biblia.estudio.data.content.ContentRepository
import com.biblia.estudio.data.user.today
import com.biblia.estudio.domain.Gamification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Widget «Mi día»: racha, lectura del plan de hoy y versículos pendientes de repaso. */
class ProgressWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        private fun open(context: Context, uri: String, code: Int) = PendingIntent.getActivity(context, code,
            Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val c = context.app
            var streak = 0; var due = 0; var plan = "Elige un plan"; var planLabel = "planes de lectura"
            if (context.getDatabasePath(ContentRepository.DB_NAME).exists()) runCatching {
                c.init()
                val t = today()
                streak = Gamification.streak(c.user.dao.activity().first(), t)
                due = c.user.dao.dueMemoryCountNow(t)
                val e = c.user.dao.enrollments().first().firstOrNull()
                if (e != null) {
                    val p = c.content.plans().firstOrNull { it.id == e.planId }
                    val dayN = (t - e.startDay + 1).toInt()
                    if (p != null && dayN in 1..p.days) {
                        plan = c.content.planDays(p.id)[dayN - 1].joinToString(", ") { c.content.readingLabel(it) }
                        planLabel = "${p.name} · día $dayN"
                    }
                }
            }
            for (id in ids) {
                val v = RemoteViews(context.packageName, R.layout.widget_progress).apply {
                    setTextViewText(R.id.pw_streak, "🔥 $streak")
                    setTextViewText(R.id.pw_plan, plan)
                    setTextViewText(R.id.pw_plan_label, planLabel)
                    setTextViewText(R.id.pw_memory, "🧠 $due")
                    setOnClickPendingIntent(R.id.pw_root, open(context, "codicevivo://today", 10))
                    setOnClickPendingIntent(R.id.pw_plan_box, open(context, "codicevivo://progress", 11))
                    setOnClickPendingIntent(R.id.pw_memory_box, open(context, "codicevivo://memory", 12))
                }
                manager.updateAppWidget(id, v)
            }
        }

        suspend fun refreshAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, ProgressWidget::class.java))
            if (ids.isNotEmpty()) render(context, m, ids)
        }
    }
}
