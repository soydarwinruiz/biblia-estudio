package com.biblia.estudio.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.biblia.estudio.app
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.data.prefs.ThemeMode
import com.biblia.estudio.work.Notifications
import kotlinx.coroutines.launch

/** Una página del recorrido: icono, título y puntos que explican cómo se usa esa parte de la app. */
private data class TourPage(val icon: ImageVector, val title: String, val intro: String, val points: List<String>)

private val TOUR = listOf(
    TourPage(Icons.Filled.WbSunny, "Hoy", "Tu devocional de cada día, en un solo lugar.", listOf(
        "Versículo, pasaje, lección, oración y 5 preguntas del día; cambian solos cada día.",
        "Marca cada parte con ○ al terminarla: suma puntos y mantiene tu racha 🔥.",
        "Las tarjetas que completas se pliegan; toca su título para abrirlas de nuevo.",
        "Con ← → arriba puedes ver los días anteriores.")),
    TourPage(Icons.AutoMirrored.Filled.MenuBook, "La Biblia", "12 versiones, sin Internet.", listOf(
        "Toca el nombre del libro arriba para ir a cualquier libro, capítulo y versículo.",
        "Desliza el dedo a los lados para pasar de capítulo.",
        "Toca la versión (RVR1960) para cambiarla, comparar dos en paralelo o leer de corrido.",
        "🔎 busca palabras, frases exactas o una cita como «Juan 3:16».")),
    TourPage(Icons.Filled.TouchApp, "Toca un versículo", "Al seleccionarlo aparece una barra de acciones.", listOf(
        "Elige un color para resaltarlo. Puedes seleccionar varios versículos a la vez.",
        "Nota: escribe tus reflexiones. MacArthur: notas de estudio del versículo.",
        "Tema: agrúpalo con otros versículos. Copiar y Compartir, con su referencia.",
        "En «Más»: marcador, etiquetas, memorizar, orar por él, imagen para compartir y modo púlpito.")),
    TourPage(Icons.Filled.TextFields, "Lee a tu gusto", "Ajusta la lectura sin salir del texto.", listOf(
        "Aa: tamaño de letra, interlineado, tipo de letra y tema claro, oscuro o sepia.",
        "Pellizca el texto con dos dedos para agrandarlo.",
        "Al bajar leyendo las barras se ocultan; sube un poco para verlas.",
        "⋮ › Escuchar capítulo: lo lee en voz alta, incluso con la pantalla apagada.")),
    TourPage(Icons.Filled.School, "Estudio", "Herramientas para profundizar.", listOf(
        "Notas e introducciones de la Biblia de Estudio MacArthur, diccionario e índice temático.",
        "Lecciones diarias y guías de método: inductivo, exégesis, hermenéutica y homilética.",
        "Nuevo estudio: recorre un pasaje paso a paso; se guarda solo mientras escribes.",
        "Expórtalo en PDF o ábrelo en modo púlpito para predicar.")),
    TourPage(Icons.Filled.EventNote, "Diario", "Tu vida de oración y la agenda de tu iglesia.", listOf(
        "Agenda: cultos, ayunos, vigilias, campañas… con día, hora, lugar y aviso.",
        "Pueden repetirse (cada semana, el primer lunes de cada mes…) y pasar a tu calendario.",
        "Oración: anota tus peticiones y marca cuándo Dios responde.",
        "Diario: escribe lo que Dios te habla cada día.")),
    TourPage(Icons.Filled.Bookmarks, "Temas, memoria y progreso", "Organiza y afianza lo que aprendes.", listOf(
        "Temas: tus colecciones de versículos; arrastra ⠿ para ordenarlos.",
        "Etiquetas, resaltados, notas y marcadores, todos juntos en «Temas».",
        "Memorizar: repaso espaciado; puedes recitar o escribir el versículo.",
        "Progreso: planes de lectura, capítulos leídos, niveles y logros.")),
    TourPage(Icons.Filled.Lock, "Tus datos son tuyos", "Todo se guarda solo en tu teléfono.", listOf(
        "Sin cuentas ni anuncios. Lo que escribes no sale de tu teléfono.",
        "Si borras algo por error, toca «Deshacer» en el aviso de abajo.",
        "Haz copias en Ajustes › Respaldo (también automáticas, cada día o semana).",
        "Puedes volver a ver este recorrido en Ajustes.")),
)

/**
 * Bienvenida y recorrido: nombre (opcional), una página por sección de la app y, al final,
 * versión, tema y recordatorio. Se muestra la primera vez y se puede repetir desde Ajustes.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(s: AppSettings) {
    val ctx = LocalContext.current; val c = ctx.app; val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(s.userName) }
    var version by remember { mutableStateOf(s.version) }
    var theme by remember { mutableStateOf(s.theme) }
    var remind by remember { mutableStateOf(s.notifications) }
    val time = rememberTimePickerState(s.notifyHour, s.notifyMinute, true)
    var showTime by remember { mutableStateOf(false) }
    val total = TOUR.size + 2   // nombre + secciones + ajustes finales
    val pager = rememberPagerState { total }
    val last = total - 1
    fun finish(granted: Boolean) = scope.launch {
        val on = remind && granted
        c.settings.update { it.copy(userName = name.trim(), version = version, theme = theme, notifications = on,
            notifyHour = time.hour, notifyMinute = time.minute, onboarded = true, tourSeen = true) }
        runCatching { Notifications.schedule(ctx.applicationContext, on, time.hour, time.minute) }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> finish(ok) }
    fun start() {
        if (remind && Build.VERSION.SDK_INT >= 33 && !Notifications.canNotify(ctx)) perm.launch(Manifest.permission.POST_NOTIFICATIONS) else finish(true)
    }
    val firstName = name.trim().substringBefore(" ")

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        // Arriba: «Saltar» lleva a los ajustes finales
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Códice vivo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp).weight(1f))
            if (pager.currentPage < last) TextButton(onClick = { scope.launch { pager.animateScrollToPage(last) } }) { Text("Saltar") }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                when (page) {
                    0 -> {
                        Spacer(Modifier.height(24.dp))
                        Text("Bienvenido a", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Códice vivo", style = MaterialTheme.typography.displaySmall)
                        Text("por Danruz", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                        Text("Tu Biblia de estudio, en tu teléfono. Te mostramos en un minuto cómo sacarle provecho.",
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
                        Text("¿Cómo te llamas?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
                        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Tu nombre (opcional)") }, leadingIcon = { Icon(Icons.Filled.Person, null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { scope.launch { pager.animateScrollToPage(1) } }))
                        Text(if (firstName.isNotEmpty()) "¡Mucho gusto, $firstName! Así te saludaremos cada día." else "Lo usaremos solo para saludarte, por ejemplo: «Buenos días, Ana».",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 6.dp))
                    }
                    last -> {
                        Text(if (firstName.isNotEmpty()) "Último paso, $firstName" else "Último paso", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
                        Text("Puedes cambiar todo esto después en Ajustes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text("¿Qué versión lees normalmente?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            c.content.versions.forEach { v -> FilterChip(version == v.id, { version = v.id }, { Text(v.id) }) }
                        }
                        Text(c.content.versions.firstOrNull { it.id == version }?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text("Apariencia", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemeMode.entries.forEach { m -> FilterChip(theme == m, { theme = m }, { Text(m.label) }) }
                        }
                        Text("Recordatorio diario", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                        ListItem(
                            headlineContent = { Text("Versículo del día a las %02d:%02d".format(time.hour, time.minute)) },
                            supportingContent = { Text("Te avisamos cada día con un versículo") },
                            trailingContent = { Switch(remind, { remind = it }) },
                        )
                        TextButton(onClick = { showTime = true }, enabled = remind) { Text("Cambiar hora") }
                    }
                    else -> TourPageView(TOUR[page - 1])
                }
            }
        }
        // Abajo: puntos de avance y botones
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage > 0) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text("Atrás") }
            else Spacer(Modifier.width(72.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                repeat(total) { i ->
                    Box(Modifier.padding(3.dp).size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            if (pager.currentPage < last) Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                Text(if (pager.currentPage == 0) "Comenzar recorrido" else "Siguiente")
            } else Button(onClick = { start() }) { Text("Empezar") }
        }
    }
    if (showTime) AlertDialog(onDismissRequest = { showTime = false }, text = { TimePicker(time) },
        confirmButton = { TextButton(onClick = { showTime = false }) { Text("Aceptar") } })
}

@Composable
private fun TourPageView(p: TourPage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 16.dp).size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(p.icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(p.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text(p.intro, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
    }
    p.points.forEach { pt ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.padding(top = 2.dp, end = 10.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(pt, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
