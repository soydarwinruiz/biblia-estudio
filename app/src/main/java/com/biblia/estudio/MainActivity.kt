package com.biblia.estudio

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
import com.biblia.estudio.ui.theme.BibliaTheme
import com.biblia.estudio.work.Notifications

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = app
        setContent {
            val settings by c.settings.settings.collectAsState(initial = AppSettings())
            var ready by remember { mutableStateOf(false) }
            var progress by remember { mutableFloatStateOf(0f) }
            var error by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                try {
                    c.init { p -> progress = p }
                    val s = c.settings.current()
                    Notifications.schedule(applicationContext, s.notifications, s.notifyHour, s.notifyMinute)
                    ready = true
                } catch (e: Exception) { error = e.message ?: e.toString() }
            }
            BibliaTheme(settings.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (ready) AppRoot(settings)
                    else Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Biblia de Estudio", style = MaterialTheme.typography.headlineSmall)
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
