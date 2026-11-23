package com.biblia.estudio.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.*
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.data.user.today
import com.biblia.estudio.domain.Gamification
import com.biblia.estudio.ui.components.StatPill
import com.biblia.estudio.ui.openRef
import com.biblia.estudio.ui.theme.Serif
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TodayData(
    val verseRef: PassageRef, val verseText: String, val passage: DailyPassage, val passagePreview: String,
    val lesson: Lesson, val prayer: Prayer, val plans: List<Triple<Plan, Int, List<Reading>>>
)

fun shareText(context: android.content.Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(i, "Compartir"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(nav: NavController, settings: AppSettings) {
    val ctx = LocalContext.current; val c = ctx.app; val scope = rememberCoroutineScope()
    val day = remember { today() }
    val enrollments by c.user.dao.enrollments().collectAsState(initial = emptyList())
    val done by c.user.dao.devotionalDone(day).collectAsState(initial = emptyList())
    val activity by c.user.dao.activity().collectAsState(initial = emptyList())
    val quizToday by c.user.dao.quizDay(day).collectAsState(initial = null)
    val data by produceState<TodayData?>(null, settings.version, enrollments) {
        val vr = c.content.verseOfDay(day)
        val p = c.content.passageOfDay(day)
        val allPlans = c.content.plans()
        val plans = enrollments.mapNotNull { e ->
            val plan = allPlans.firstOrNull { it.id == e.planId } ?: return@mapNotNull null
            val dayN = (day - e.startDay).toInt() + 1
            if (dayN < 1 || dayN > plan.days) null else Triple(plan, dayN, c.content.planDays(plan.id)[dayN - 1])
        }
        value = TodayData(vr, c.content.passageText(settings.version, vr), p,
            c.content.passageText(settings.version, p.ref.copy(chapterEnd = p.ref.chapter, verseEnd = p.ref.verse + 2)),
            c.content.lessonOfDay(day), c.content.prayerOfDay(day), plans)
    }
    val points = activity.sumOf { it.points }
    val streak = Gamification.streak(activity, day)
    val level = Gamification.level(points)
    val doneTypes = done.map { it.type }.toSet()
    val dateLabel = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es"))).replaceFirstChar { it.uppercase() } }

    Scaffold(topBar = {
        TopAppBar(title = { Column { Text("Hoy"); Text(dateLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            actions = {
                IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Filled.Search, "Buscar") }
                IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Ajustes") }
            })
    }) { pad ->
        val d = data
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { nav.navigate("progress") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatPill("🔥", "$streak", if (streak == 1) "día de racha" else "días de racha")
                    StatPill("⭐", "$points", "puntos")
                    StatPill("🏆", "${level.number}", level.name)
                    StatPill("✅", "${doneTypes.size + (if (quizToday != null) 1 else 0)}/5", "hoy")
                }
            }
            if (d == null) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)); return@Column }

            DailyCard("Versículo del día", "verse" in doneTypes, onDone = { scope.launch { c.user.markDevotional("verse") } }) {
                Text("«${d.verseText}»", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif, fontStyle = FontStyle.Italic))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.content.label(d.verseRef) + " · " + settings.version, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { shareText(ctx, "«${d.verseText}» — ${c.content.label(d.verseRef)} (${settings.version})") }) { Icon(Icons.Filled.Share, "Compartir") }
                    TextButton(onClick = { nav.openRef(d.verseRef) }) { Text("Contexto") }
                }
            }
            DailyCard("Pasaje del día", "passage" in doneTypes, onDone = { scope.launch { c.user.markDevotional("passage") } }) {
                Text(d.passage.title, style = MaterialTheme.typography.titleMedium)
                Text(c.content.label(d.passage.ref), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(d.passagePreview, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif))
                Row { Spacer(Modifier.weight(1f)); FilledTonalButton(onClick = { scope.launch { c.user.markDevotional("passage") }; nav.openRef(d.passage.ref) }) { Text("Leer pasaje") } }
            }
            DailyCard("Lección del día", "lesson" in doneTypes, onDone = { scope.launch { c.user.markDevotional("lesson") } }) {
                Text(d.lesson.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(d.lesson.content, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Row { Spacer(Modifier.weight(1f)); FilledTonalButton(onClick = { nav.navigate("lesson/${d.lesson.id}") }) { Text("Estudiar lección") } }
            }
            DailyCard("Oración del día", "prayer" in doneTypes, onDone = { scope.launch { c.user.markDevotional("prayer") } }) {
                Text(d.prayer.title, style = MaterialTheme.typography.titleMedium)
                Text("Basada en " + c.content.label(d.prayer.ref), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(d.prayer.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif))
                Row { Spacer(Modifier.weight(1f)); FilledTonalButton(onClick = { nav.navigate("prayer") }) { Text("Orar") } }
            }
            DailyCard("Preguntas del día", quizToday != null, onDone = null) {
                if (quizToday == null) {
                    Text("5 preguntas para poner a prueba tu conocimiento bíblico. ¡Gana hasta 70 puntos!", style = MaterialTheme.typography.bodyMedium)
                    Row { Spacer(Modifier.weight(1f)); Button(onClick = { nav.navigate("quiz") }) { Text("Comenzar") } }
                } else {
                    Text("Respondiste bien ${quizToday!!.correct} de ${quizToday!!.total}. ¡Vuelve mañana!", style = MaterialTheme.typography.bodyMedium)
                    Row { Spacer(Modifier.weight(1f)); OutlinedButton(onClick = { nav.navigate("quiz?practice=true") }) { Text("Práctica libre") } }
                }
            }
            if (d.plans.isEmpty()) {
                DailyCard("Plan de lectura", false, onDone = null) {
                    Text("Aún no sigues ningún plan. Elige uno para leer la Biblia con constancia.", style = MaterialTheme.typography.bodyMedium)
                    Row { Spacer(Modifier.weight(1f)); TextButton(onClick = { nav.navigate("progress") }) { Text("Ver planes") } }
                }
            } else d.plans.forEach { (plan, dayN, readings) ->
                DailyCard("${plan.name} · día $dayN", false, onDone = null) {
                    readings.forEach { r ->
                        TextButton(onClick = { nav.openRef(PassageRef(r.book, r.chapterStart)) }) { Text(c.content.readingLabel(r)) }
                    }
                    Row { Spacer(Modifier.weight(1f)); TextButton(onClick = { nav.navigate("plan/${plan.id}") }) { Text("Abrir plan") } }
                }
            }
        }
    }
}

@Composable
private fun DailyCard(title: String, done: Boolean, onDone: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (onDone != null || done) {
                    IconButton(onClick = { onDone?.invoke() }, enabled = !done && onDone != null) {
                        Icon(if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, if (done) "Completado" else "Marcar como completado",
                            tint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
                    }
                }
            }
            content()
        }
    }
}
