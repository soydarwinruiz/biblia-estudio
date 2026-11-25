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
import com.biblia.estudio.data.prefs.ReaderFont
import com.biblia.estudio.data.prefs.ThemeMode
import com.biblia.estudio.ui.components.BackTopBar
import com.biblia.estudio.ui.components.SectionHeader
import com.biblia.estudio.ui.theme.verseTextStyle
import com.biblia.estudio.work.AutoBackup
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
        val before = c.settings.current()
        c.settings.update(f); val n = c.settings.current()
        if (n.notifications != before.notifications || n.notifyHour != before.notifyHour || n.notifyMinute != before.notifyMinute)
            runCatching { Notifications.schedule(ctx.applicationContext, n.notifications, n.notifyHour, n.notifyMinute) }
        if (n.autoBackupDays != before.autoBackupDays) runCatching { AutoBackup.schedule(ctx.applicationContext, n.autoBackupDays) }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            set { it.copy(autoBackupUri = uri.toString(), autoBackupDays = if (it.autoBackupDays == 0) 1 else it.autoBackupDays) }
        }
    }
    var lastBackup by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { lastBackup = c.settings.lastAutoBackup() }
    // Los deslizadores guardan al soltar (no en cada paso) para no reescribir los ajustes decenas de veces.
    var fontSize by remember(s.fontSize) { mutableFloatStateOf(s.fontSize) }
    var lineHeight by remember(s.lineHeight) { mutableFloatStateOf(s.lineHeight) }
    var ttsRate by remember(s.ttsRate) { mutableFloatStateOf(s.ttsRate) }
    var voices by remember { mutableStateOf(emptyList<java.util.Locale>()) }
    LaunchedEffect(Unit) { repeat(10) { voices = c.speaker.availableSpanish(); if (voices.isNotEmpty()) return@LaunchedEffect; kotlinx.coroutines.delay(300) } }
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
            if (Build.VERSION.SDK_INT >= 31 && s.theme != ThemeMode.SEPIA) SwitchRow("Colores del sistema (Material You)", s.dynamicColor) { v -> set { it.copy(dynamicColor = v) } }
            Text("Tipo de letra del texto bíblico", Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.padding(horizontal = 16.dp)) {
                ReaderFont.entries.forEach { f -> FilterChip(s.readerFont == f, { set { it.copy(readerFont = f) } }, { Text(f.label) }, Modifier.padding(end = 8.dp)) }
            }
            Text("Tamaño de letra: ${fontSize.toInt()}", Modifier.padding(start = 16.dp, top = 12.dp))
            Slider(fontSize, { fontSize = it }, onValueChangeFinished = { set { it.copy(fontSize = fontSize) } }, valueRange = 13f..30f, steps = 16, modifier = Modifier.padding(horizontal = 16.dp))
            Text("Interlineado: ${"%.1f".format(lineHeight)}", Modifier.padding(start = 16.dp))
            Slider(lineHeight, { lineHeight = it }, onValueChangeFinished = { set { it.copy(lineHeight = lineHeight) } }, valueRange = 1.2f..2.0f, steps = 7, modifier = Modifier.padding(horizontal = 16.dp))
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("En el principio era el Verbo, y el Verbo era con Dios, y el Verbo era Dios.", Modifier.padding(12.dp), style = verseTextStyle(fontSize, lineHeight, s.readerFont))
            }
            SectionHeader("Lectura")
            Text("Versión predeterminada", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.padding(horizontal = 16.dp)) {
                c.content.versions.forEach { v -> FilterChip(s.version == v.id, { set { it.copy(version = v.id) } }, { Text(v.id) }, Modifier.padding(end = 6.dp)) }
            }
            SwitchRow("Mostrar títulos de sección", s.showHeadings) { v -> set { it.copy(showHeadings = v) } }
            SwitchRow("Mostrar números de versículo", s.showVerseNumbers) { v -> set { it.copy(showVerseNumbers = v) } }
            SwitchRow("Indicar notas de estudio MacArthur", s.showNoteMarkers) { v -> set { it.copy(showNoteMarkers = v) } }

            SectionHeader("Lectura en voz alta")
            Text("Voz", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(s.ttsLocale.isBlank(), { set { it.copy(ttsLocale = "") } }, { Text("Predeterminada") }, Modifier.padding(end = 6.dp))
                voices.forEach { l ->
                    val tag = l.toLanguageTag()
                    FilterChip(s.ttsLocale == tag, { set { it.copy(ttsLocale = tag) } }, { Text(l.displayCountry.ifBlank { tag }) }, Modifier.padding(end = 6.dp))
                }
            }
            if (voices.isEmpty()) Text("Instala voces en español en Ajustes del teléfono › Texto a voz para elegir acento (México, EE. UU., España…).",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            Text("Velocidad: ${"%.1f".format(ttsRate)}×", Modifier.padding(start = 16.dp, top = 8.dp))
            Slider(ttsRate, { ttsRate = it }, onValueChangeFinished = { set { it.copy(ttsRate = ttsRate) } }, valueRange = 0.6f..1.8f, steps = 11, modifier = Modifier.padding(horizontal = 16.dp))
            TextButton(onClick = { c.speaker.configure(s.ttsLocale, ttsRate); c.speaker.speak("test", listOf("Porque de tal manera amó Dios al mundo, que ha dado a su Hijo unigénito.")) },
                modifier = Modifier.padding(horizontal = 8.dp)) { Text("Probar voz") }
            SwitchRow("Al terminar un capítulo, seguir con el siguiente", s.audioContinue) { v -> set { it.copy(audioContinue = v) } }

            SectionHeader("Recordatorio diario")
            SwitchRow("Notificación con el versículo del día", s.notifications) { v ->
                if (v && Build.VERSION.SDK_INT >= 33 && !Notifications.canNotify(ctx)) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                set { it.copy(notifications = v) }
            }
            ListItem(headlineContent = { Text("Hora del recordatorio") }, supportingContent = { Text("%02d:%02d".format(s.notifyHour, s.notifyMinute)) },
                modifier = Modifier.clickable(enabled = s.notifications) { timePicker = true })
            if (s.notifications && !Notifications.canScheduleExact(ctx)) ListItem(
                headlineContent = { Text("Permitir hora exacta") },
                supportingContent = { Text("Sin este permiso el aviso puede llegar con algunos minutos de retraso.") },
                modifier = Modifier.clickable {
                    runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + ctx.packageName))) }
                })

            SectionHeader("Respaldo de datos personales")
            Text("Tus resaltados, notas, temas, etiquetas, estudios y progreso se guardan solo en este teléfono. Haz copias de seguridad en un archivo.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.padding(16.dp)) {
                Button(onClick = { exportLauncher.launch("codice-vivo-respaldo-${LocalDate.now()}.json") }) { Text("Exportar") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Importar") }
            }

            SectionHeader("Respaldo automático")
            Text("Guarda una copia de tus datos cada día o cada semana en una carpeta que elijas (por ejemplo, una carpeta que sincronice otra app o una tarjeta SD). Se conservan las últimas ${AutoBackup.KEEP} copias.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            ListItem(
                headlineContent = { Text(if (s.autoBackupUri.isBlank()) "Elegir carpeta" else "Carpeta") },
                supportingContent = { Text(if (s.autoBackupUri.isBlank()) "Ninguna elegida" else android.net.Uri.decode(s.autoBackupUri.substringAfterLast("/")).substringAfter(":").ifBlank { "Raíz" }) },
                modifier = Modifier.clickable { folderLauncher.launch(null) })
            if (s.autoBackupUri.isNotBlank()) {
                FlowRow(Modifier.padding(horizontal = 16.dp)) {
                    listOf(0 to "Desactivado", 1 to "Diario", 7 to "Semanal").forEach { (d, label) ->
                        FilterChip(s.autoBackupDays == d, { set { it.copy(autoBackupDays = d) } }, { Text(label) }, Modifier.padding(end = 8.dp))
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            try { val n = AutoBackup.runNow(ctx.applicationContext); lastBackup = c.settings.lastAutoBackup(); Toast.makeText(ctx, "Guardado: $n", Toast.LENGTH_SHORT).show() }
                            catch (e: Exception) { Toast.makeText(ctx, "No se pudo respaldar: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
                    }) { Text("Respaldar ahora") }
                    Spacer(Modifier.width(12.dp))
                    Text(if (lastBackup > 0) "Último: " + java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("es")).format(java.util.Date(lastBackup)) else "Aún sin copias",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Además, Android incluye tus datos (no la Biblia) en la copia de seguridad de Google del teléfono si la tienes activada.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

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
