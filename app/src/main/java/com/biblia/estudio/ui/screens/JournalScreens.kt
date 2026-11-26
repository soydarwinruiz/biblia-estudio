package com.biblia.estudio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import com.biblia.estudio.ui.components.SectionTopBar
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.data.user.JournalEntry
import com.biblia.estudio.domain.Recurrence
import com.biblia.estudio.ui.components.BackTopBar
import com.biblia.estudio.ui.components.EmptyState
import com.biblia.estudio.ui.openRef
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale("es"))
private fun fmt(t: Long) = dateFmt.format(Date(t))

/** Tipos de actividad de la agenda (emoji, nombre). */
val EVENT_TYPES = listOf(
    "⛪" to "Culto", "🙏" to "Oración", "🍞" to "Ayuno", "📖" to "Devocional", "🌙" to "Vigilia",
    "📣" to "Campaña", "🎓" to "Estudio bíblico", "👥" to "Reunión", "🎵" to "Alabanza", "🤝" to "Visita", "📌" to "Otro",
)
fun eventEmoji(type: String) = EVENT_TYPES.firstOrNull { it.second == type }?.first ?: "📌"

private val dayFmt = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))
private val timeFmt = SimpleDateFormat("HH:mm", Locale("es"))
private val shortDayFmt = SimpleDateFormat("EEE d MMM yyyy", Locale("es"))

/**
 * Diario: sección propia con tres pestañas.
 * Agenda (actividades de la iglesia con día, hora, lugar y aviso), Oración (peticiones con «respondida») y Diario (entradas).
 */
@Composable
fun JournalScreen(nav: NavController, initialTab: Int = 0) {
    val c = LocalContext.current.app
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val kind = when (tab) { 0 -> "event"; 1 -> "prayer"; else -> "journal" }
    val entries by remember(kind) { if (kind == "event") c.user.dao.events() else c.user.dao.journal(kind) }.collectAsState(initial = null)
    val answered by c.user.dao.answeredCount().collectAsState(initial = 0)
    var showPast by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { SectionTopBar("Diario") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate("journal/0?kind=$kind") }, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(when (tab) { 0 -> "Nueva actividad"; 1 -> "Nueva petición"; else -> "Nueva entrada" }) })
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Agenda") }, icon = { Icon(Icons.Filled.Event, null) })
                Tab(tab == 1, { tab = 1 }, text = { Text("Oración") }, icon = { Icon(Icons.Filled.VolunteerActivism, null) })
                Tab(tab == 2, { tab = 2 }, text = { Text("Diario") }, icon = { Icon(Icons.Filled.EditNote, null) })
            }
            val list = entries ?: return@Column
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (tab == 0) {
                    val startToday = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    // Cada repetición en su fecha, para las próximas 8 semanas
                    val upcoming = upcomingOccurrences(list, startToday, startToday + 56 * 86_400_000L)
                    val past = list.filter { Recurrence.next(it.eventAt, it.repeatRule, it.repeatUntil, startToday) == null }.sortedByDescending { it.eventAt }
                    if (list.isEmpty()) item {
                        EmptyState(Icons.Filled.Event, "Agenda vacía", "Programa cultos, ayunos, vigilias, devocionales, campañas y demás actividades con su día, hora y lugar. Te avisaremos antes de cada una.")
                    }
                    var lastDay = ""
                    upcoming.forEach { (e, at) ->
                        val day = dayFmt.format(Date(at)).replaceFirstChar { it.uppercase() }
                        val header = if (day != lastDay) day else null
                        lastDay = day
                        item(key = "${e.id}_$at") {
                            if (header != null) Text(if (isToday(at)) "Hoy · $header" else header, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            EventRow(e, at = at) { nav.navigate("journal/${e.id}?kind=event") }
                        }
                    }
                    if (past.isNotEmpty()) item(key = "past") {
                        TextButton(onClick = { showPast = !showPast }, modifier = Modifier.padding(start = 8.dp, top = 12.dp)) {
                            Text(if (showPast) "Ocultar actividades pasadas" else "Ver actividades pasadas (${past.size})")
                        }
                    }
                    if (showPast) items(past, key = { "p" + it.id }) { e -> EventRow(e, faded = true) { nav.navigate("journal/${e.id}?kind=event") } }
                } else {
                    if (tab == 1 && answered > 0) item {
                        Text("🙌 $answered oración${if (answered > 1) "es" else ""} respondida${if (answered > 1) "s" else ""}", Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (list.isEmpty()) item {
                        if (tab == 1) EmptyState(Icons.Filled.VolunteerActivism, "Sin peticiones", "Anota por qué oras y marca cuándo Dios responde. También puedes orar desde un versículo en el lector (botón «Orar»).")
                        else EmptyState(Icons.Filled.EditNote, "Diario vacío", "Escribe lo que Dios te habla en tu lectura, tus luchas y tus gratitudes.")
                    }
                    items(list, key = { it.id }) { e -> JournalRow(e, onClick = { nav.navigate("journal/${e.id}?kind=${e.kind}") }) }
                }
            }
        }
    }
}

private fun isToday(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()

/** Repeticiones de las actividades entre [from] y [to], ordenadas: (actividad, fecha). */
fun upcomingOccurrences(events: List<JournalEntry>, from: Long, to: Long): List<Pair<JournalEntry, Long>> =
    events.flatMap { e -> Recurrence.occurrences(e.eventAt, e.repeatRule, e.repeatUntil, from, to, 30).map { e to it } }.sortedBy { it.second }

@Composable
fun EventRow(e: JournalEntry, faded: Boolean = false, at: Long = e.eventAt, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                Text(eventEmoji(e.eventType), style = MaterialTheme.typography.titleLarge)
                Text(timeFmt.format(Date(at)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        overlineContent = { Text(buildString {
            append(e.eventType.ifBlank { "Actividad" }); if (faded) append(" · " + fmt(e.eventAt))
            if (e.repeatRule.isNotBlank()) append(" · 🔁 " + Recurrence.label(e.repeatRule, e.eventAt).replaceFirstChar { it.lowercase() })
        }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        headlineContent = { Text(e.title.ifBlank { e.eventType }, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (faded) MaterialTheme.colorScheme.outline else Color.Unspecified) },
        supportingContent = {
            val sub = listOfNotNull(e.place.takeIf { it.isNotBlank() }?.let { "📍 $it" }, if (e.reminderMinutes >= 0 && !faded) "🔔 " + reminderLabel(e.reminderMinutes) else null).joinToString("   ")
            if (sub.isNotBlank()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    )
}

val REMINDER_OPTIONS = listOf(-1 to "Sin aviso", 0 to "A la hora", 15 to "15 min antes", 60 to "1 hora antes", 180 to "3 horas antes", 1440 to "1 día antes")
fun reminderLabel(m: Int) = REMINDER_OPTIONS.firstOrNull { it.first == m }?.second ?: "$m min antes"

@Composable
private fun JournalRow(e: JournalEntry, onClick: () -> Unit) {
    val c = LocalContext.current.app
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = if (e.kind == "prayer") ({
            Icon(if (e.answered) Icons.Filled.CheckCircle else Icons.Filled.VolunteerActivism, null,
                tint = if (e.answered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }) else null,
        overlineContent = {
            Text(buildString {
                append(fmt(e.created))
                if (e.book > 0) append(" · " + c.content.label(PassageRef(e.book, e.chapter, e.verse)))
                if (e.answered) append(" · respondida el " + fmt(e.answeredAt))
            })
        },
        headlineContent = { Text(e.title.ifBlank { e.text.lineSequence().firstOrNull().orEmpty() }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { if (e.text.isNotBlank() && e.title.isNotBlank()) Text(e.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalEditScreen(nav: NavController, id: Long, kind: String, ref: PassageRef) {
    val ctx = LocalContext.current; val c = ctx.app; val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf<JournalEntry?>(null) }
    LaunchedEffect(id) {
        entry = if (id > 0) c.user.dao.journalEntry(id) else {
            // Actividad nueva: mañana a las 19:00 con aviso una hora antes
            val at = java.time.LocalDate.now().plusDays(1).atTime(19, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            JournalEntry(kind = kind, book = ref.book, chapter = ref.chapter, verse = ref.verse,
                eventType = if (kind == "event") "Culto" else "", eventAt = if (kind == "event") at else 0, reminderMinutes = if (kind == "event") 60 else -1)
        }
    }
    val e = entry
    var title by remember(e?.id) { mutableStateOf(e?.title ?: "") }
    var text by remember(e?.id) { mutableStateOf(e?.text ?: "") }
    var answered by remember(e?.id) { mutableStateOf(e?.answered ?: false) }
    var answerNote by remember(e?.id) { mutableStateOf(e?.answerNote ?: "") }
    var eventType by remember(e?.id) { mutableStateOf(e?.eventType ?: "") }
    var eventAt by remember(e?.id) { mutableLongStateOf(e?.eventAt ?: 0L) }
    var place by remember(e?.id) { mutableStateOf(e?.place ?: "") }
    var reminder by remember(e?.id) { mutableIntStateOf(e?.reminderMinutes ?: -1) }
    var repeatRule by remember(e?.id) { mutableStateOf(e?.repeatRule ?: "") }
    var repeatUntil by remember(e?.id) { mutableLongStateOf(e?.repeatUntil ?: 0L) }
    var pickUntil by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val k = e?.kind ?: kind
    val isPrayer = k == "prayer"; val isEvent = k == "event"
    val verseText by produceState("", e?.book, e?.chapter, e?.verse) {
        val x = e; value = if (x != null && x.book > 0 && x.verse > 0) c.content.verseText(c.settings.current().version, x.book, x.chapter, x.verse) else ""
    }
    val zone = java.time.ZoneId.systemDefault()
    val at = java.time.Instant.ofEpochMilli(eventAt).atZone(zone)
    fun save() = scope.launch {
        val base = e ?: return@launch
        if (title.isBlank() && text.isBlank() && !isEvent) { nav.popBackStack(); return@launch }
        val now = System.currentTimeMillis()
        val saved = base.copy(title = title.trim().ifBlank { if (isEvent) eventType else "" }, text = text.trim(), answered = answered,
            answeredAt = if (answered) (base.answeredAt.takeIf { it > 0 } ?: now) else 0, answerNote = answerNote.trim(), updated = now,
            eventType = eventType, eventAt = eventAt, place = place.trim(), reminderMinutes = reminder,
            repeatRule = repeatRule, repeatUntil = if (repeatRule.isBlank()) 0 else repeatUntil)
        val newId = c.user.dao.upsertJournal(saved)
        if (isEvent) runCatching { com.biblia.estudio.work.EventReminders.schedule(ctx.applicationContext, saved.copy(id = if (base.id > 0) base.id else newId)) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { nav.popBackStack() }
    }
    androidx.activity.compose.BackHandler { save() }
    Scaffold(topBar = {
        BackTopBar(when { isEvent -> if (id > 0) "Actividad" else "Nueva actividad"; isPrayer -> "Petición de oración"; else -> "Entrada de diario" }, { save() }) {
            if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
            TextButton(onClick = { save() }) { Text("Guardar") }
        }
    }) { pad ->
        if (e == null) return@Scaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (e.book > 0) {
                val r = PassageRef(e.book, e.chapter, e.verse)
                AssistChip(onClick = { nav.openRef(r) }, label = { Text(c.content.label(r)) })
                if (verseText.isNotBlank()) Text("«$verseText»", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = com.biblia.estudio.ui.theme.Serif),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (isEvent) {
                Text("Tipo de actividad", style = MaterialTheme.typography.labelLarge)
                FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EVENT_TYPES.forEach { (emoji, name) -> FilterChip(eventType == name, { eventType = name }, { Text("$emoji $name") }) }
                }
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Nombre de la actividad") }, placeholder = { Text("p. ej. Ayuno congregacional, Vigilia de jóvenes") })
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1.6f)) {
                        Icon(Icons.Filled.CalendarMonth, null); Text("  " + shortDayFmt.format(Date(eventAt)).replaceFirstChar { it.uppercase() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Schedule, null); Text("  " + timeFmt.format(Date(eventAt))) }
                }
                OutlinedTextField(place, { place = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
                    label = { Text("Lugar") }, leadingIcon = { Icon(Icons.Filled.Place, null) }, placeholder = { Text("Templo, casa, dirección…") })
                Text("Repetir", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Recurrence.RULES.forEach { r -> FilterChip(repeatRule == r, { repeatRule = r }, { Text(Recurrence.label(r, eventAt)) }) }
                }
                if (repeatRule.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hasta: ", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { pickUntil = true }) { Text(if (repeatUntil > 0) shortDayFmt.format(Date(repeatUntil)) else "sin fecha de fin") }
                    if (repeatUntil > 0) TextButton(onClick = { repeatUntil = 0 }) { Text("Quitar") }
                }
                Text("Aviso", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    REMINDER_OPTIONS.forEach { (m, label) -> FilterChip(reminder == m, { reminder = m }, { Text(label) }) }
                }
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 8.dp),
                    label = { Text("Notas (opcional)") }, placeholder = { Text("Qué llevar, horario, responsables, motivo de oración…") })
                OutlinedButton(onClick = {
                    addToPhoneCalendar(ctx, title.ifBlank { eventType }, eventType, eventAt, place, text, repeatRule, repeatUntil)
                }, modifier = Modifier.padding(top = 12.dp)) { Icon(Icons.Filled.EventAvailable, null); Text("  Añadir al calendario del teléfono") }
            } else {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(if (isPrayer) "Por qué o por quién oras" else "Título (opcional)") })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    label = { Text(if (isPrayer) "Tu oración" else "Lo que Dios te habló hoy") })
            }
            if (isPrayer) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { answered = !answered }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(answered, { answered = it }); Text("Oración respondida")
                }
                if (answered) OutlinedTextField(answerNote, { answerNote = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp), label = { Text("¿Cómo respondió Dios?") })
            }
            if (id > 0) Text("Creada el ${fmt(e.created)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 16.dp))
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = at.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { utc ->
                    val d = java.time.Instant.ofEpochMilli(utc).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    eventAt = d.atTime(at.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                }
                pickDate = false
            }) { Text("Aceptar") }
        }, dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } }) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(at.hour, at.minute, true)
        AlertDialog(onDismissRequest = { pickTime = false }, text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = {
                eventAt = at.toLocalDate().atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli(); pickTime = false
            }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancelar") } })
    }
    if (pickUntil) {
        val base = if (repeatUntil > 0) repeatUntil else eventAt + 90L * 86_400_000L
        val state = rememberDatePickerState(initialSelectedDateMillis = java.time.Instant.ofEpochMilli(base).atZone(zone).toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickUntil = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { utc ->
                    repeatUntil = java.time.Instant.ofEpochMilli(utc).atZone(java.time.ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                }
                pickUntil = false
            }) { Text("Aceptar") }
        }, dismissButton = { TextButton(onClick = { pickUntil = false }) { Text("Cancelar") } }) { DatePicker(state) }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("¿Eliminar?") },
        text = { Text("Se borrará esta ${if (isEvent) "actividad" else "entrada"} del diario.") },
        confirmButton = { TextButton(onClick = {
            scope.launch {
                if (isEvent) runCatching { com.biblia.estudio.work.EventReminders.cancel(ctx.applicationContext, id) }
                c.user.dao.deleteJournal(id); confirmDelete = false; nav.popBackStack()
            }
        }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

/** Abre la app de calendario del teléfono con la actividad lista para guardar (sin pedir permisos). */
fun addToPhoneCalendar(ctx: android.content.Context, title: String, type: String, at: Long, place: String, notes: String, rule: String, until: Long) {
    val i = android.content.Intent(android.content.Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
        .putExtra(android.provider.CalendarContract.Events.TITLE, title)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, at)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, at + 3_600_000L)
        .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, place)
        .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, listOf(type, notes).filter { it.isNotBlank() }.joinToString("\n"))
    Recurrence.rrule(rule, at, until)?.let { i.putExtra(android.provider.CalendarContract.Events.RRULE, it) }
    try { ctx.startActivity(i) } catch (e: Exception) {
        android.widget.Toast.makeText(ctx, "No hay una app de calendario instalada", android.widget.Toast.LENGTH_LONG).show()
    }
}
