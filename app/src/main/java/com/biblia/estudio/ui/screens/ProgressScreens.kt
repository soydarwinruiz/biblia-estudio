package com.biblia.estudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.data.content.Plan
import com.biblia.estudio.data.content.Reading
import com.biblia.estudio.data.user.today
import com.biblia.estudio.domain.Achievements
import com.biblia.estudio.domain.Gamification
import com.biblia.estudio.domain.Stats
import com.biblia.estudio.ui.components.BackTopBar
import com.biblia.estudio.ui.components.SectionHeader
import com.biblia.estudio.ui.components.StatPill
import com.biblia.estudio.ui.openRef
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(nav: NavController) {
    val c = LocalContext.current.app
    val read by c.user.dao.chaptersRead().collectAsState(initial = emptyList())
    val activity by c.user.dao.activity().collectAsState(initial = emptyList())
    val quizDays by c.user.dao.quizDays().collectAsState(initial = emptyList())
    val enrollments by c.user.dao.enrollments().collectAsState(initial = emptyList())
    val highlights by c.user.dao.allHighlights().collectAsState(initial = emptyList())
    val notes by c.user.dao.allNotes().collectAsState(initial = emptyList())
    val topics by c.user.dao.topics().collectAsState(initial = emptyList())
    val studies by c.user.dao.studies().collectAsState(initial = emptyList())
    val devotionals by c.user.dao.devotionalCount().collectAsState(initial = 0)
    val plans by produceState(emptyList<Plan>()) { value = c.content.plans() }
    val planDone by produceState(emptyMap<Int, Int>(), enrollments, activity) { value = c.user.dao.dumpPlanDays().groupingBy { it.planId }.eachCount() }
    var showAll by remember { mutableStateOf(false) }

    val books = c.content.books
    val perBook = read.groupingBy { it.book }.eachCount()
    val ot = read.count { it.book <= 39 }; val nt = read.size - ot
    val completed = books.count { (perBook[it.id] ?: 0) >= it.chapters }
    val points = activity.sumOf { it.points }
    val level = Gamification.level(points)
    val stats = Stats(read.size, ot, nt, completed, Gamification.streak(activity, today()), Gamification.bestStreak(activity), points,
        quizDays.size, quizDays.count { it.correct == it.total && it.total > 0 }, highlights.size, topics.size, notes.size, studies.size, devotionals,
        plans.count { p -> (planDone[p.id] ?: 0) >= p.days })
    val achievements = Achievements.compute(stats)

    Scaffold(topBar = { TopAppBar(title = { Text("Progreso") }, actions = { IconButton(onClick = { nav.navigate("quiz") }) { Icon(Icons.Filled.Quiz, "Preguntas") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Nivel ${level.number} · ${level.name}", style = MaterialTheme.typography.titleLarge)
                        val next = level.next
                        if (next != null) {
                            LinearProgressIndicator(progress = { (points - level.min).toFloat() / (next - level.min) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            Text("$points / $next puntos para el siguiente nivel", style = MaterialTheme.typography.labelMedium)
                        } else Text("$points puntos · ¡nivel máximo!")
                        // 2 × 2 para que las etiquetas largas quepan con cualquier tamaño de letra
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            StatPill("🔥", "${stats.streak}", "racha", Modifier.weight(1f)); StatPill("🏅", "${stats.bestStreak}", "mejor racha", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            StatPill("❓", "${stats.quizDays}", "cuestionarios", Modifier.weight(1f)); StatPill("🎯", "${stats.perfectQuizzes}", "perfectos", Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Lectura de la Biblia", style = MaterialTheme.typography.titleMedium)
                        ProgressLine("Toda la Biblia", read.size, 1189)
                        ProgressLine("Antiguo Testamento", ot, 929)
                        ProgressLine("Nuevo Testamento", nt, 260)
                        Text("$completed libros completos", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            item { SectionHeader("Planes de lectura") }
            items(plans) { p ->
                val enrolled = enrollments.firstOrNull { it.planId == p.id }
                val done = planDone[p.id] ?: 0
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = {
                        Column {
                            Text(p.description, style = MaterialTheme.typography.bodySmall)
                            if (enrolled != null) {
                                LinearProgressIndicator(progress = { done.toFloat() / p.days }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                                Text("$done de ${p.days} días · día ${(today() - enrolled.startDay + 1).coerceAtMost(p.days.toLong())} del calendario", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    trailingContent = { Text(if (enrolled != null) "Activo" else "${p.days} días", color = if (enrolled != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline) },
                    modifier = Modifier.clickable { nav.navigate("plan/${p.id}") }
                )
            }
            item { SectionHeader("Logros (${achievements.count { it.unlocked }}/${achievements.size})") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    achievements.chunked(3).forEach { row ->
                        Row {
                            row.forEach { a ->
                                Card(Modifier.weight(1f).padding(3.dp).alpha(if (a.unlocked) 1f else 0.5f)) {
                                    Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(a.icon, fontSize = 26.sp)
                                        Text(a.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                        Text(a.description, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                        if (!a.unlocked) LinearProgressIndicator(progress = { a.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Progreso por libro", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Solo iniciados" else "Ver todos") }
                }
            }
            items(books.filter { showAll || (perBook[it.id] ?: 0) > 0 }) { b ->
                val n = perBook[b.id] ?: 0
                Column(Modifier.fillMaxWidth().clickable { nav.openRef(PassageRef(b.id, 1)) }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row { Text(b.name, Modifier.weight(1f)); Text("$n/${b.chapters}", style = MaterialTheme.typography.labelMedium) }
                    LinearProgressIndicator(progress = { n.toFloat() / b.chapters }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(label: String, value: Int, total: Int) {
    Column(Modifier.padding(top = 8.dp)) {
        Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text("$value/$total · ${"%.1f".format(value * 100f / total)}%", style = MaterialTheme.typography.labelMedium) }
        LinearProgressIndicator(progress = { value.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp).clip(RoundedCornerShape(4.dp)))
    }
}

@Composable
fun PlanScreen(nav: NavController, id: Int) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    val plan by produceState<Plan?>(null, id) { value = c.content.plans().firstOrNull { it.id == id } }
    val days by produceState(emptyList<List<Reading>>(), id) { value = c.content.planDays(id) }
    val enrollments by c.user.dao.enrollments().collectAsState(initial = emptyList())
    val done by c.user.dao.planDaysDone(id).collectAsState(initial = emptyList())
    val enrolled = enrollments.firstOrNull { it.planId == id }
    val doneSet = done.map { it.day }.toSet()
    val currentDay = enrolled?.let { (today() - it.startDay + 1).toInt() } ?: 0
    val listState = rememberLazyListState()
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(currentDay, days.size) { if (currentDay > 1 && days.isNotEmpty()) listState.scrollToItem((currentDay).coerceAtMost(days.size)) }
    Scaffold(topBar = { BackTopBar(plan?.name ?: "Plan", { nav.popBackStack() }) }) { pad ->
        val p = plan ?: return@Scaffold
        LazyColumn(Modifier.padding(pad), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(p.description)
                    Spacer(Modifier.height(8.dp))
                    if (enrolled == null) Button(onClick = { scope.launch { c.user.enroll(id) } }) { Text("Comenzar hoy") }
                    else {
                        LinearProgressIndicator(progress = { doneSet.size.toFloat() / p.days }, modifier = Modifier.fillMaxWidth())
                        Text("${doneSet.size} de ${p.days} días completados", style = MaterialTheme.typography.labelMedium)
                        // Si vas atrasado, el plan se puede reprogramar para que «hoy» sea el primer día pendiente.
                        val firstPending = (1..p.days).firstOrNull { it !in doneSet }
                        if (firstPending != null && firstPending < currentDay) {
                            Text("Vas ${currentDay - firstPending} día${if (currentDay - firstPending > 1) "s" else ""} atrasado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                            OutlinedButton(onClick = { scope.launch { c.user.dao.upsertEnrollment(enrolled.copy(startDay = today() - (firstPending - 1))) } }) {
                                Text("Reprogramar: hoy = día $firstPending")
                            }
                        }
                        TextButton(onClick = { confirmLeave = true }) { Text("Abandonar / reiniciar plan") }
                    }
                }
            }
            itemsIndexed(days) { i, readings ->
                val day = i + 1
                val isToday = day == currentDay
                ListItem(
                    leadingContent = {
                        Checkbox(day in doneSet, { chk -> scope.launch {
                            if (enrolled == null) c.user.enroll(id)
                            c.user.markPlanDay(id, day, chk)
                            if (chk) readings.forEach { r -> (r.chapterStart..r.chapterEnd).forEach { ch -> c.user.dao.markRead(com.biblia.estudio.data.user.ChapterRead(r.book, ch)) } }
                        } })
                    },
                    overlineContent = { Text("Día $day" + if (isToday) " · HOY" else "", color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    headlineContent = {
                        Column { readings.forEach { r -> Text(c.content.readingLabel(r), Modifier.clickable { nav.openRef(PassageRef(r.book, r.chapterStart)) }.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.primary) } }
                    },
                    modifier = if (isToday) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)) else Modifier
                )
            }
        }
    }
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("¿Abandonar el plan?") }, text = { Text("Se borrará el progreso de este plan (los capítulos leídos se conservan).") },
        confirmButton = { TextButton(onClick = { scope.launch { c.user.leavePlan(id); confirmLeave = false } }) { Text("Abandonar") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancelar") } })
}
