package com.biblia.estudio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.ui.AppRoot
import com.biblia.estudio.ui.refRoute
import com.biblia.estudio.ui.screens.OnboardingScreen
import com.biblia.estudio.ui.theme.BibliaTheme
import com.biblia.estudio.work.AutoBackup
import com.biblia.estudio.work.Notifications
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Destino pedido desde fuera (atajo, widget, «Compartir», selección de texto). */
    private val incoming = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incoming.value = intent
        val c = app
        setContent {
            val settingsOrNull by c.settings.settings.collectAsState(initial = null)
            val settings = settingsOrNull ?: AppSettings()
            var ready by remember { mutableStateOf(false) }
            var progress by remember { mutableFloatStateOf(0f) }
            var error by remember { mutableStateOf<String?>(null) }
            val pendingIntent by incoming.collectAsState()
            LaunchedEffect(Unit) {
                try {
                    c.init { p -> progress = p }
                    ready = true
                    runCatching { com.biblia.estudio.work.VerseWidget.refreshAll(applicationContext) }
                } catch (e: Exception) { error = e.message ?: e.toString() }
                // El recordatorio y el respaldo son secundarios: si fallan, la app sigue funcionando.
                runCatching {
                    val s = c.settings.current()
                    Notifications.schedule(applicationContext, s.notifications, s.notifyHour, s.notifyMinute)
                    AutoBackup.schedule(applicationContext, s.autoBackupDays)
                }
                runCatching { com.biblia.estudio.work.UpdateCheck.schedule(applicationContext, c.settings.current().updateCheck) }
                runCatching { com.biblia.estudio.work.UpdateCheck.notifyIfUpdated(applicationContext) }
            }
            LaunchedEffect(settings.ttsLocale, settings.ttsRate) { c.speaker.configure(settings.ttsLocale, settings.ttsRate) }
            BibliaTheme(settings.theme, settings.dynamicColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        ready && settingsOrNull != null && !settings.onboarded -> OnboardingScreen(settings)
                        ready && settingsOrNull != null -> AppRoot(settings, pendingIntent?.let { routeFor(it) }) { incoming.value = null }
                        else -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Códice vivo", style = MaterialTheme.typography.headlineSmall)
                            Text("Biblia de estudio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text("por Danruz", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(16.dp))
                            if (error != null) Text("No se pudo preparar la base de datos: $error", color = MaterialTheme.colorScheme.error)
                            else {
                                Text(if (progress < 0.01f) "Abriendo…" else "Preparando la biblioteca (solo la primera vez)…", textAlign = TextAlign.Center)
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.value = intent
    }

    /** Traduce un Intent externo a una ruta de navegación (o null si no pide nada). */
    private fun routeFor(intent: Intent): String? {
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (text != null) {
            val parser = app.refParser
            val ref = parser?.parseQuery(text) ?: parser?.parse(text)?.firstOrNull()?.ref
            return if (ref != null) refRoute(ref) else "search?q=${Uri.encode(text.take(200))}"
        }
        val data = intent.data ?: return null
        if (data.scheme != "codicevivo") return null
        return when (data.host) {
            "reader", "search", "memory", "journal", "today", "progress" -> data.host
            else -> null
        }
    }
}
