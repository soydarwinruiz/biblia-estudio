package com.biblia.estudio.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.data.prefs.ThemeMode
import com.biblia.estudio.ui.components.BackTopBar
import com.biblia.estudio.ui.components.SectionHeader
import com.biblia.estudio.ui.theme.verseTextStyle
import com.biblia.estudio.work.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: NavController, s: AppSettings) {
    val ctx = LocalContext.current; val c = ctx.app; val scope = rememberCoroutineScope()
    fun set(f: (AppSettings) -> AppSettings) = scope.launch {
        c.settings.update(f); val n = c.settings.current()
        runCatching { Notifications.schedule(ctx.applicationContext, n.notifications, n.notifyHour, n.notifyMinute) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(ctx, "Sin permiso no se pueden mostrar recordatorios", Toast.LENGTH_LONG).show()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = c.user.exportJson()
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            Toast.makeText(ctx, "Respaldo guardado", Toast.LENGTH_SHORT).show()
        }
    }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    var timePicker by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar("Ajustes", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionHeader("Apariencia")
            FlowRow(Modifier.padding(horizontal = 16.dp)) {
                ThemeMode.entries.forEach { m -> FilterChip(s.theme == m, { set { it.copy(theme = m) } }, { Text(m.label) }, Modifier.padding(end = 8.dp)) }
            }
            Text("Tamaño de letra: ${s.fontSize.toInt()}", Modifier.padding(start = 16.dp, top = 12.dp))
            Slider(s.fontSize, { v -> set { it.copy(fontSize = v) } }, valueRange = 13f..30f, steps = 16, modifier = Modifier.padding(horizontal = 16.dp))
            Text("Interlineado: ${"%.1f".format(s.lineHeight)}", Modifier.padding(start = 16.dp))
            Slider(s.lineHeight, { v -> set { it.copy(lineHeight = v) } }, valueRange = 1.2f..2.0f, steps = 7, modifier = Modifier.padding(horizontal = 16.dp))
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("En el principio era el Verbo, y el Verbo era con Dios, y el Verbo era Dios.", Modifier.padding(12.dp), style = verseTextStyle(s.fontSize, s.lineHeight))
            }
            SectionHeader("Lectura")
            Text("Versión predeterminada", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.padding(horizontal = 16.dp)) {
                c.content.versions.forEach { v -> FilterChip(s.version == v.id, { set { it.copy(version = v.id) } }, { Text(v.id) }, Modifier.padding(end = 6.dp)) }
            }
            SwitchRow("Mostrar títulos de sección", s.showHeadings) { v -> set { it.copy(showHeadings = v) } }
            SwitchRow("Mostrar números de versículo", s.showVerseNumbers) { v -> set { it.copy(showVerseNumbers = v) } }
            SwitchRow("Indicar notas de estudio MacArthur", s.showNoteMarkers) { v -> set { it.copy(showNoteMarkers = v) } }

            SectionHeader("Recordatorio diario")
            SwitchRow("Notificación con el versículo del día", s.notifications) { v ->
                if (v && Build.VERSION.SDK_INT >= 33 && !Notifications.canNotify(ctx)) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                set { it.copy(notifications = v) }
            }
            ListItem(headlineContent = { Text("Hora del recordatorio") }, supportingContent = { Text("%02d:%02d".format(s.notifyHour, s.notifyMinute)) },
                modifier = Modifier.clickable(enabled = s.notifications) { timePicker = true })

            SectionHeader("Respaldo de datos personales")
            Text("Tus resaltados, notas, temas, etiquetas, estudios y progreso se guardan solo en este teléfono. Haz copias de seguridad en un archivo.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.padding(16.dp)) {
                Button(onClick = { exportLauncher.launch("codice-vivo-respaldo-${LocalDate.now()}.json") }) { Text("Exportar") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Importar") }
            }

            SectionHeader("Acerca de")
            Text(
                "Códice vivo — Biblia de estudio 100% local: no solicita permiso de Internet; todo el contenido y tus datos permanecen en el dispositivo.\n\n" +
                "Contenido de estudio: Biblia de Estudio MacArthur (© Editorial Portavoz / Thomas Nelson), incluida para uso personal.\n\n" +
                "Textos bíblicos: " + c.content.versions.joinToString("\n") { "• ${it.id}: ${it.copyright.ifBlank { it.name }}" },
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall
            )
        }
    }
    if (timePicker) {
        val state = rememberTimePickerState(s.notifyHour, s.notifyMinute, true)
        AlertDialog(onDismissRequest = { timePicker = false }, text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { set { it.copy(notifyHour = state.hour, notifyMinute = state.minute) }; timePicker = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { timePicker = false }) { Text("Cancelar") } })
    }
    pendingImport?.let { uri ->
        AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("¿Restaurar respaldo?") },
            text = { Text("Se reemplazarán todos tus datos personales actuales por los del archivo.") },
            confirmButton = { TextButton(onClick = {
                pendingImport = null
                scope.launch {
                    try {
                        val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: error("No se pudo leer")
                        c.user.importJson(text)
                        Toast.makeText(ctx, "Respaldo restaurado", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { Toast.makeText(ctx, "Archivo no válido: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(label) }, trailingContent = { Switch(value, onChange) }, modifier = Modifier.clickable { onChange(!value) })
}
