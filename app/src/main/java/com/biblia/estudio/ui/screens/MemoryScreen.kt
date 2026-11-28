package com.biblia.estudio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.data.user.MemoryVerse
import com.biblia.estudio.data.user.Sm2
import com.biblia.estudio.data.user.today
import com.biblia.estudio.ui.components.BackTopBar
import com.biblia.estudio.ui.components.EmptyState
import com.biblia.estudio.ui.openRef
import com.biblia.estudio.ui.theme.Serif
import kotlinx.coroutines.launch

private val BOX_NAMES = listOf("Nuevo", "Aprendiendo", "Aprendiendo", "Repasando", "Repasando", "Afianzado", "Memorizado")

private fun MemoryVerse.ref() = PassageRef(book, chapter, verse, chapter, verseEnd)

/** Primera letra de cada palabra: ayuda para recordar sin ver el texto completo. */
private fun hint(text: String) = text.split(" ").joinToString(" ") { w ->
    val i = w.indexOfFirst { it.isLetter() }
    if (i < 0) w else w.substring(0, i + 1) + w.substring(i + 1).map { if (it.isLetter()) '_' else it }.joinToString("")
}

@Composable
fun MemoryScreen(nav: NavController, settings: AppSettings) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    val all by c.user.dao.memoryVerses().collectAsState(initial = emptyList())
    var session by remember { mutableStateOf<List<MemoryVerse>?>(null) }
    var idx by remember { mutableIntStateOf(0) }
    var known by remember { mutableIntStateOf(0) }
    val due = all.filter { it.nextReview <= today() }

    Scaffold(topBar = { BackTopBar("Memorizar versículos", { if (session != null) session = null else nav.popBackStack() }) }) { pad ->
        val s = session
        if (s != null) {
            if (idx >= s.size) {
                Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("🧠", fontSize = 64.sp)
                    Text("Repaso terminado", style = MaterialTheme.typography.headlineSmall)
                    Text("Recordaste $known de ${s.size}. +${known * 5} puntos", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { session = null }) { Text("Volver") }
                }
            } else ReviewCard(s[idx], settings, idx, s.size) { grade ->
                scope.launch { c.user.reviewMemory(s[idx], grade); if (grade >= Sm2.HARD) known++; idx++ }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${all.size} versículo${if (all.size == 1) "" else "s"} · ${all.count { it.box >= 6 }} memorizado${if (all.count { it.box >= 6 } == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
                        Text("Repaso espaciado (SM-2): según lo bien que recuerdes cada versículo, el siguiente repaso se aleja más o menos. Puedes recitarlo o escribirlo para comprobarlo palabra por palabra.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { session = due; idx = 0; known = 0 }, enabled = due.isNotEmpty(), modifier = Modifier.padding(top = 8.dp)) {
                            Text(if (due.isEmpty()) "Nada pendiente hoy" else "Repasar ahora (${due.size})")
                        }
                    }
                }
            }
            if (all.isEmpty()) item { EmptyState(Icons.Filled.Psychology, "Aún no hay versículos", "En el lector, selecciona un versículo y toca «Memorizar».") }
            items(all, key = { it.id }) { m ->
                VerseItem(m.ref(), settings.version, BOX_NAMES[m.box.coerceIn(0, 6)] + if (m.nextReview <= today()) " · para hoy" else " · en ${days((m.nextReview - today()).toInt())}", null, { nav.openRef(m.ref()) }) {
                    IconButton(onClick = { scope.launch {
                        c.user.dao.deleteMemory(m.id)
                        com.biblia.estudio.ui.components.Undo.offer("Quitado de Memorizar: ${c.content.label(m.ref())}") { c.user.dao.upsertMemory(m) }
                    } }) { Icon(Icons.Filled.Delete, "Quitar de Memorizar") }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(m: MemoryVerse, settings: AppSettings, index: Int, total: Int, onAnswer: (Int) -> Unit) {
    val c = LocalContext.current.app
    val text by produceState("", m) { value = c.content.passageText(settings.version, m.ref()) }
    var reveal by remember(m) { mutableStateOf(false) }
    var showHint by remember(m) { mutableStateOf(false) }
    var typing by remember(m) { mutableStateOf(false) }
    var typed by remember(m) { mutableStateOf("") }
    var result by remember(m) { mutableStateOf<Pair<Float, List<Pair<String, Boolean>>>?>(null) }
    val preview = remember(m) { Sm2.preview(m, today()) }
    Column(Modifier.fillMaxSize().padding(top = 64.dp).verticalScroll(rememberScrollState()).padding(24.dp)) {
        LinearProgressIndicator(progress = { index.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        Text("${index + 1} de $total", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(24.dp))
        Text(c.content.label(m.ref()), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(if (typing) "Escribe el versículo de memoria" else "Recita el versículo de memoria", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        val r = result
        if (typing && !reveal) {
            OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp), placeholder = { Text("Escribe aquí…") })
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = { showHint = !showHint }) { Text("Pista") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { result = Sm2.compareTyped(text, typed); reveal = true }, enabled = typed.isNotBlank()) { Text("Comprobar") }
            }
            if (showHint) Text(hint(text), Modifier.padding(top = 12.dp).fillMaxWidth(), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif), textAlign = TextAlign.Center)
        } else ElevatedCard(Modifier.fillMaxWidth()) {
            if (r != null) {
                // Palabras acertadas en verde, falladas en rojo
                val ok = MaterialTheme.colorScheme.primary; val bad = MaterialTheme.colorScheme.error
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    r.second.forEachIndexed { i, (w, good) ->
                        if (i > 0) append(" ")
                        withStyle(androidx.compose.ui.text.SpanStyle(color = if (good) ok else bad,
                            textDecoration = if (good) null else androidx.compose.ui.text.style.TextDecoration.Underline)) { append(w) }
                    }
                }, Modifier.padding(20.dp).fillMaxWidth(), style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif), textAlign = TextAlign.Center)
            } else Text(
                when { reveal -> text; showHint -> hint(text); else -> "• • •" },
                Modifier.padding(20.dp).fillMaxWidth(), style = MaterialTheme.typography.titleMedium.copy(fontFamily = Serif), textAlign = TextAlign.Center
            )
        }
        if (r != null) Text("Acertaste ${(r.first * 100).toInt()} % de las palabras", Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(20.dp))
        if (!reveal && !typing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = { showHint = true }, enabled = !showHint) { Text("Pista") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { typing = true }) { Text("Escribir") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { reveal = true }) { Text("Mostrar") }
            }
        } else if (reveal) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { c.speaker.speak("mem", listOf(text)) }) { Icon(Icons.Filled.VolumeUp, "Escuchar") }
            }
            Text("¿Qué tal lo recordaste?", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
            val suggested = r?.let { Sm2.gradeFor(it.first) }
            val labels = listOf("Otra vez", "Difícil", "Bien", "Fácil")
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { g, label ->
                    val sub = days(preview[g])
                    val mod = Modifier.weight(1f)
                    if (g == (suggested ?: Sm2.GOOD)) Button(onClick = { onAnswer(g) }, mod, contentPadding = PaddingValues(4.dp)) { GradeLabel(label, sub) }
                    else OutlinedButton(onClick = { onAnswer(g) }, mod, contentPadding = PaddingValues(4.dp)) { GradeLabel(label, sub) }
                }
            }
        }
    }
}

private fun days(n: Int) = if (n == 1) "1 día" else if (n < 30) "$n días" else if (n < 365) "${n / 30} mes${if (n / 30 > 1) "es" else ""}" else "1 año"

@Composable
private fun GradeLabel(label: String, sub: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    com.biblia.estudio.ui.components.FitText(label, MaterialTheme.typography.labelLarge)
    com.biblia.estudio.ui.components.FitText(sub, MaterialTheme.typography.labelSmall)
}
