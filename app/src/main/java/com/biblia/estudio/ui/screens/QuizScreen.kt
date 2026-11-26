package com.biblia.estudio.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.QuizQuestion
import com.biblia.estudio.data.user.Points
import com.biblia.estudio.data.user.today
import com.biblia.estudio.ui.components.BackTopBar
import com.biblia.estudio.ui.components.Loading
import com.biblia.estudio.ui.components.RefPreviewDialog
import com.biblia.estudio.ui.openRef
import com.biblia.estudio.ui.theme.Serif
import kotlinx.coroutines.launch

@Composable
fun QuizScreen(nav: NavController, practice0: Boolean) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    val day = remember { today() }
    val quizToday by c.user.dao.quizDay(day).collectAsState(initial = null)
    var practice by remember { mutableStateOf(practice0) }
    var category by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val categories by produceState(emptyList<String>()) { value = c.content.quizCategories() }
    val questions by produceState<List<QuizQuestion>?>(null, practice, category, round) {
        value = null
        // Preguntas contestadas antes (las de hoy no, para que el cuestionario del día no cambie al reabrirlo)
        val seen = c.user.dao.quizSeenBefore(if (practice) day + 1 else day).toSet()
        value = if (practice) c.content.randomQuiz(10, category, seen) else c.content.dailyQuiz(day, exclude = seen)
    }
    var index by remember(questions) { mutableIntStateOf(0) }
    var chosen by remember(questions, index) { mutableStateOf<Int?>(null) }
    var correct by remember(questions) { mutableIntStateOf(0) }
    var finished by remember(questions) { mutableStateOf(false) }
    var preview by remember { mutableStateOf<com.biblia.estudio.data.content.PassageRef?>(null) }
    val alreadyDone = !practice && quizToday != null && !finished && index == 0 && chosen == null

    Scaffold(topBar = { BackTopBar(if (practice) "Práctica libre" else "Preguntas del día", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            if (practice) Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                FilterChip(category == null, { category = null }, { Text("Todas") }, Modifier.padding(end = 6.dp))
                categories.forEach { cat -> FilterChip(category == cat, { category = cat }, { Text(cat) }, Modifier.padding(end = 6.dp)) }
            }
            if (alreadyDone) {
                val q = quizToday!!
                Text("🎉", fontSize = 56.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Ya completaste las preguntas de hoy: ${q.correct}/${q.total}", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("Vuelve mañana para un nuevo desafío, o sigue practicando.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
                Button(onClick = { practice = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Práctica libre") }
                return@Column
            }
            val qs = questions ?: return@Column Loading()
            if (qs.isEmpty()) { Text("No hay preguntas en esta categoría."); return@Column }
            if (finished) {
                val perfect = correct == qs.size
                Text(if (perfect) "🏆" else if (correct * 2 >= qs.size) "👏" else "📖", fontSize = 64.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("$correct de ${qs.size} correctas", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                val pts = if (practice) correct * 2 else correct * Points.QUIZ_CORRECT + if (perfect) Points.QUIZ_PERFECT_BONUS else 0
                Text("+$pts puntos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = { nav.popBackStack() }) { Text("Salir") }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { practice = true; round++ }) { Text("Otra ronda") }
                }
                return@Column
            }
            val q = qs[index]
            LinearProgressIndicator(progress = { (index + (if (chosen != null) 1 else 0)).toFloat() / qs.size }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.padding(vertical = 8.dp)) {
                Text("Pregunta ${index + 1} de ${qs.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(q.category + " · " + "★".repeat(q.difficulty), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            Text(q.question, style = MaterialTheme.typography.titleLarge.copy(fontFamily = Serif), modifier = Modifier.padding(vertical = 12.dp))
            q.options.forEachIndexed { i, opt ->
                val ch = chosen
                val colors = when {
                    ch == null -> ButtonDefaults.outlinedButtonColors()
                    i == q.answer -> ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.25f))
                    i == ch -> ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFC62828).copy(alpha = 0.25f))
                    else -> ButtonDefaults.outlinedButtonColors()
                }
                OutlinedButton(onClick = { if (chosen == null) { chosen = i; if (i == q.answer) correct++ } }, colors = colors,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(opt, Modifier.fillMaxWidth().padding(vertical = 6.dp), textAlign = TextAlign.Start)
                }
            }
            if (chosen != null) {
                Text(if (chosen == q.answer) "¡Correcto!" else "Respuesta correcta: ${q.options[q.answer]}",
                    color = if (chosen == q.answer) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                val r = c.refParser?.parseQuery(q.ref)
                if (r != null) TextButton(onClick = { preview = r }) { Text("Ver ${q.ref}") } else Text(q.ref, style = MaterialTheme.typography.labelMedium)
                Button(onClick = {
                    if (index + 1 < qs.size) index++ else {
                        finished = true
                        scope.launch {
                            c.user.dao.markQuizSeen(qs.map { com.biblia.estudio.data.user.QuizSeen(it.id, day) })
                            if (practice) c.user.practicePoints(correct) else c.user.saveQuiz(correct, qs.size)
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (index + 1 < qs.size) "Siguiente" else "Ver resultado") }
            }
        }
    }
    preview?.let { RefPreviewDialog(it, { preview = null }) { r -> nav.openRef(r) } }
}
