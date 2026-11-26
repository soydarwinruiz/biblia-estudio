package com.biblia.estudio.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.biblia.estudio.app
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.data.prefs.ThemeMode
import com.biblia.estudio.work.Notifications
import kotlinx.coroutines.launch

/** Bienvenida (solo la primera vez): versión, tema y recordatorio, con el permiso de notificaciones. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(s: AppSettings) {
    val ctx = LocalContext.current; val c = ctx.app; val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(s.version) }
    var theme by remember { mutableStateOf(s.theme) }
    var remind by remember { mutableStateOf(s.notifications) }
    val time = rememberTimePickerState(s.notifyHour, s.notifyMinute, true)
    var showTime by remember { mutableStateOf(false) }
    fun finish(granted: Boolean) = scope.launch {
        val on = remind && granted
        c.settings.update { it.copy(version = version, theme = theme, notifications = on, notifyHour = time.hour, notifyMinute = time.minute, onboarded = true) }
        runCatching { Notifications.schedule(ctx.applicationContext, on, time.hour, time.minute) }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> finish(ok) }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Bienvenido a", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Códice vivo", style = MaterialTheme.typography.displaySmall)
        Text("por Danruz", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        Text("Tu Biblia de estudio, 100 % en tu teléfono: sin cuentas, tus notas solo para ti.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))

        Text("¿Qué versión lees normalmente?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            c.content.versions.forEach { v -> FilterChip(version == v.id, { version = v.id }, { Text(v.id) }) }
        }
        Text(c.content.versions.firstOrNull { it.id == version }?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

        Text("Apariencia", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeMode.entries.forEach { m -> FilterChip(theme == m, { theme = m }, { Text(m.label) }) }
        }

        Text("Recordatorio diario", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))
        ListItem(
            headlineContent = { Text("Versículo del día a las %02d:%02d".format(time.hour, time.minute)) },
            supportingContent = { Text("Toca para cambiar la hora") },
            trailingContent = { Switch(remind, { remind = it }) },
        )
        TextButton(onClick = { showTime = true }, enabled = remind) { Text("Cambiar hora") }

        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            if (remind && Build.VERSION.SDK_INT >= 33 && !Notifications.canNotify(ctx)) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
            else finish(true)
        }, modifier = Modifier.fillMaxWidth()) { Text("Empezar") }
        Text("Puedes cambiar todo esto después en Ajustes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
    }
    if (showTime) AlertDialog(onDismissRequest = { showTime = false }, text = { TimePicker(time) },
        confirmButton = { TextButton(onClick = { showTime = false }) { Text("Aceptar") } })
}
