package com.biblia.estudio.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CoPresent
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.IntroSection
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.data.content.StudyNote
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.data.user.Study
import com.biblia.estudio.ui.components.BackTopBar
import com.biblia.estudio.ui.components.LinkedText
import com.biblia.estudio.ui.components.Loading
import com.biblia.estudio.ui.components.RefPreviewDialog
import com.biblia.estudio.ui.openRef
import com.biblia.estudio.ui.openPulpit
import com.biblia.estudio.ui.theme.Serif
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

private val TABS = listOf("Pasaje", "Contexto", "Exégesis", "Hermenéutica", "Homilética")

@Composable
fun StudyEditorScreen(nav: NavController, settings: AppSettings, id: Long, initialRef: PassageRef) {
    val ctx = LocalContext.current; val c = ctx.app; val scope = rememberCoroutineScope()
    var study by remember { mutableStateOf<Study?>(null) }
    var savedId by rememberSaveable { mutableLongStateOf(id) }
    var dirty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        study = if (id > 0) c.user.dao.study(id) else {
            val r = initialRef
            Study(title = "Estudio de " + c.content.label(r), book = r.book, chapter = r.chapter, verse = r.verse, chapterEnd = r.chapterEnd, verseEnd = r.verseEnd)
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<PassageRef?>(null) }
    val s = study
    fun upd(f: (Study) -> Study) { study = study?.let(f); dirty = true }
    val saveLock = remember { kotlinx.coroutines.sync.Mutex() }
    var savedNote by remember { mutableStateOf("") }
    /** Guarda (una sola escritura a la vez, para no duplicar un estudio nuevo). */
    suspend fun persist() = saveLock.withLock {
        val cur = study ?: return@withLock
        if (!dirty && savedId > 0) return@withLock
        val newId = c.user.saveStudy(cur.copy(id = savedId))
        savedId = newId; study = study?.copy(id = newId); dirty = false
        savedNote = "Guardado " + java.text.SimpleDateFormat("HH:mm", java.util.Locale("es")).format(java.util.Date())
    }
    fun save(then: () -> Unit = {}) = scope.launch {
        persist()
        Toast.makeText(ctx, "Estudio guardado", Toast.LENGTH_SHORT).show(); then()
    }
    // Guardado automático: 1,5 s después de dejar de escribir
    LaunchedEffect(study) { if (dirty) { kotlinx.coroutines.delay(1500); persist() } }
    // Salir con el gesto o el botón «atrás» del teléfono también guarda
    /** Guarda y sale (la navegación siempre en el hilo principal). */
    fun leave() = scope.launch { persist(); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { nav.popBackStack() } }
    androidx.activity.compose.BackHandler(enabled = dirty) { leave() }
    Scaffold(topBar = {
        BackTopBar(s?.title ?: "Estudio", { leave() }) {
            if (savedNote.isNotBlank() && !dirty) Text(savedNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Box {
                IconButton(onClick = { shareMenu = true }) { Icon(Icons.Filled.Share, "Compartir y exportar") }
                DropdownMenu(shareMenu, { shareMenu = false }) {
                    DropdownMenuItem(text = { Text("Compartir como texto") }, onClick = {
                        shareMenu = false
                        s?.let { shareText(ctx, exportStudy(it, c.content.label(PassageRef(it.book, it.chapter, it.verse, it.chapterEnd, it.verseEnd)))) }
                    })
                    DropdownMenuItem(text = { Text("Exportar a PDF") }, onClick = {
                        shareMenu = false
                        val st = s ?: return@DropdownMenuItem
                        scope.launch {
                            val r = PassageRef(st.book, st.chapter, st.verse, st.chapterEnd, st.verseEnd)
                            val label = c.content.label(r)
                            val passage = c.content.passage(settings.version, r).joinToString(" ") { "${it.label} ${it.text}" }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                shareFile(ctx, "estudio-${st.id}.pdf", "application/pdf") { f ->
                                    writePdf(f, st.title, "$label · ${settings.version}", listOf("Texto" to passage) + studySections(st))
                                }
                            }
                        }
                    })
                    DropdownMenuItem(text = { Text("Abrir en modo púlpito") }, onClick = {
                        shareMenu = false
                        s?.let { nav.openPulpit(PassageRef(it.book, it.chapter, it.verse, it.chapterEnd, it.verseEnd)) }
                    })
                }
            }
            if (savedId > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
            IconButton(onClick = { save() }) { Icon(Icons.Filled.Save, "Guardar") }
        }
    }) { pad ->
        if (s == null) return@Scaffold Loading()
        val ref = PassageRef(s.book, s.chapter, s.verse, s.chapterEnd, s.verseEnd)
        Column(Modifier.padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) { TABS.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                when (tab) {
                    0 -> PassageTab(s, ref, settings, ::upd, nav) { preview = it }
                    1 -> {
                        Guide("Ubica el pasaje en su contexto: ¿quién escribe, a quién, cuándo y por qué? ¿Qué ocurre antes y después? ¿Dónde encaja en el bosquejo del libro?")
                        IntroHelper(s.book) { preview = it }
                        Field("Contexto histórico y cultural", s.contextHistorical, "Autor, destinatarios, fecha, situación, costumbres…") { v -> upd { it.copy(contextHistorical = v) } }
                        Field("Contexto literario", s.contextLiterary, "Género, lugar en el libro, párrafos anteriores y posteriores…") { v -> upd { it.copy(contextLiterary = v) } }
                    }
                    2 -> {
                        Guide("Exégesis: extrae del texto lo que el autor quiso decir. Observa repeticiones, conectores (porque, por tanto, para que), verbos principales, mandatos y promesas. Compara versiones.")
                        OutlinedButton(onClick = { nav.navigate("compare/${s.book}/${s.chapter}/${maxOf(1, s.verse)}") }) { Text("Comparar versiones") }
                        Field("Observaciones", s.observation, "¿Quién? ¿Qué? ¿Cuándo? ¿Dónde? ¿Por qué? ¿Cómo?") { v -> upd { it.copy(observation = v) } }
                        Field("Palabras clave", s.keyWords, "Términos importantes y su significado (usa las notas de estudio)") { v -> upd { it.copy(keyWords = v) } }
                        Field("Estructura del pasaje", s.structure, "Bosquejo o diagrama del argumento, versículo por versículo") { v -> upd { it.copy(structure = v) } }
                        Field("Idea exegética", s.exegeticalIdea, "En una frase: lo que el texto dijo a sus primeros lectores") { v -> upd { it.copy(exegeticalIdea = v) } }
                    }
                    3 -> {
                        Guide("Hermenéutica: interpreta según el sentido literal, gramatical e histórico. La Escritura interpreta a la Escritura. Un significado, muchas aplicaciones. Cruza el puente: de lo que significó para ellos a lo que significa para nosotros.")
                        Field("Significado original", s.originalMeaning, "¿Qué significó para los destinatarios originales?") { v -> upd { it.copy(originalMeaning = v) } }
                        Field("Principio teológico universal", s.principle, "Verdad atemporal que enseña el pasaje") { v -> upd { it.copy(principle = v) } }
                        Field("Referencias cruzadas", s.crossReferences, "Otros textos que confirman o aclaran (p. ej. Ro. 5:1; Ef. 2:8)") { v -> upd { it.copy(crossReferences = v) } }
                        if (s.crossReferences.isNotBlank()) LinkedText(s.crossReferences, s.book, s.chapter, MaterialTheme.typography.bodyMedium, Modifier.padding(bottom = 8.dp)) { preview = it }
                        Field("Aplicación personal", s.application, "¿Qué creeré, cambiaré u obedeceré? Sé concreto.") { v -> upd { it.copy(application = v) } }
                    }
                    4 -> {
                        Guide("Homilética: convierte el estudio en un mensaje fiel al texto. Proposición clara, bosquejo que salga del pasaje, explicación, ilustración y aplicación, y una conclusión que apunte a Cristo.")
                        Field("Proposición (idea homilética)", s.proposition, "Una oración en presente, aplicable a los oyentes") { v -> upd { it.copy(proposition = v) } }
                        Field("Propósito", s.purpose, "Qué quieres que los oyentes crean, sientan o hagan") { v -> upd { it.copy(purpose = v) } }
                        Field("Bosquejo", s.outline, "I. …\n   A. …\nII. …\nIII. …", minLines = 6) { v -> upd { it.copy(outline = v) } }
                        Field("Ilustraciones", s.illustrations, "Historias, ejemplos o imágenes que aclaran cada punto") { v -> upd { it.copy(illustrations = v) } }
                        Field("Aplicaciones", s.sermonApplication, "Aplicaciones concretas para la congregación") { v -> upd { it.copy(sermonApplication = v) } }
                        Field("Conclusión e invitación", s.conclusion, "Resumen, llamado y dirección a Cristo") { v -> upd { it.copy(conclusion = v) } }
                        Text("Lista de revisión: ¿Fiel al texto? ¿Idea central clara? ¿Exalta a Cristo? ¿Aplica concretamente?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    if (tab > 0) OutlinedButton(onClick = { tab-- }) { Text("‹ ${TABS[tab - 1]}") }
                    Spacer(Modifier.weight(1f))
                    if (tab < TABS.lastIndex) Button(onClick = { tab++ }) { Text("${TABS[tab + 1]} ›") } else Button(onClick = { save() }) { Text("Guardar estudio") }
                }
            }
        }
    }
    preview?.let { RefPreviewDialog(it, { preview = null }) { r -> nav.openRef(r) } }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("¿Eliminar este estudio?") },
        confirmButton = { TextButton(onClick = { scope.launch { c.user.dao.deleteStudy(savedId); confirmDelete = false; nav.popBackStack() } }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

@Composable
private fun PassageTab(s: Study, ref: PassageRef, settings: AppSettings, upd: ((Study) -> Study) -> Unit, nav: NavController, onRef: (PassageRef) -> Unit) {
    val c = LocalContext.current.app
    val bk = c.content.book(s.book)
    val verses by produceState(emptyList<com.biblia.estudio.data.content.Verse>(), ref, settings.version) { value = c.content.passage(settings.version, ref) }
    val notes by produceState(emptyList<StudyNote>(), ref) {
        value = (ref.chapter..ref.chapterEnd).flatMap { ch -> c.content.notesForChapter(s.book, ch) }.distinctBy { it.id }
            .filter { n -> ref.verse == 0 || (n.chapter * 1000 + n.verse) in (ref.chapter * 1000 + ref.verse)..(ref.chapterEnd * 1000 + (if (ref.verseEnd == 0) 999 else ref.verseEnd)) || ref.contains(n.chapter, n.verse) }
    }
    val maxV by produceState(176, s.book, s.chapter) { value = c.content.chapter("RVR1960", s.book, s.chapter).maxOfOrNull { it.verseEnd } ?: 176 }
    var expanded by remember(ref) { mutableStateOf(false) }
    var allNotes by remember(ref) { mutableStateOf(false) }
    OutlinedTextField(s.title, { v -> upd { it.copy(title = v) } }, Modifier.fillMaxWidth(), label = { Text("Título del estudio") }, singleLine = true)

    Text("Pasaje · ${bk.name} ${s.chapter}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(s.verse == 0, { upd { it.copy(verse = 0, verseEnd = 0, chapterEnd = it.chapter) } }, { Text("Capítulo completo") })
        FilterChip(s.verse > 0, { if (s.verse == 0) upd { it.copy(verse = 1, verseEnd = minOf(maxV, 1), chapterEnd = it.chapter) } }, { Text("Versículos") })
    }
    if (s.verse > 0) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField("Desde", s.verse) { v -> if (v in 1..maxV) upd { it.copy(verse = v, verseEnd = maxOf(it.verseEnd, v), chapterEnd = it.chapter) } }
        Text("  –  ", style = MaterialTheme.typography.titleMedium)
        NumberField("Hasta", s.verseEnd) { v -> if (v in s.verse..maxV) upd { it.copy(verseEnd = v, chapterEnd = it.chapter) } }
        Text("  de $maxV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }

    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 10.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.content.label(ref), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(settings.version, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(8.dp))
            val shown = if (expanded || verses.size <= 10) verses else verses.take(8)
            val numColor = MaterialTheme.colorScheme.primary
            Text(androidx.compose.ui.text.buildAnnotatedString {
                shown.forEach { v ->
                    withStyle(androidx.compose.ui.text.SpanStyle(color = numColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize, baselineShift = androidx.compose.ui.text.style.BaselineShift(0.3f))) { append(v.label + " ") }
                    append(v.text + " ")
                }
            }, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f))
            if (verses.size > 10) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Mostrar menos" else "Mostrar todo (${verses.size} versículos)")
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        AssistChip(onClick = { nav.openRef(ref) }, label = { Text("Abrir en la Biblia") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp)) })
        AssistChip(onClick = { nav.navigate("intro/${s.book}") }, label = { Text("Introducción del libro") }, leadingIcon = { Icon(Icons.Filled.Info, null, Modifier.size(18.dp)) })
        AssistChip(onClick = { nav.openPulpit(ref) }, label = { Text("Púlpito") }, leadingIcon = { Icon(Icons.Filled.CoPresent, null, Modifier.size(18.dp)) })
    }

    Text("Notas de estudio MacArthur (${notes.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    if (notes.isEmpty()) Text("No hay notas para este pasaje.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    (if (allNotes) notes else notes.take(4)).forEach { n ->
        OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(c.content.label(n.ref), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LinkedText(n.text, s.book, n.chapter, MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif), onRef = onRef)
            }
        }
    }
    if (notes.size > 4) TextButton(onClick = { allNotes = !allNotes }) { Text(if (allNotes) "Mostrar menos notas" else "Ver las ${notes.size} notas") }
}

@Composable
private fun IntroHelper(book: Int, onRef: (PassageRef) -> Unit) {
    val c = LocalContext.current.app
    val intro by produceState(emptyList<IntroSection>(), book) { value = c.content.intro(book) }
    var open by remember { mutableStateOf<String?>(null) }
    Text("Ayudas de la introducción (MacArthur)", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.padding(vertical = 4.dp)) {
        listOf("Autor y fecha", "Contexto histórico", "Bosquejo").forEach { sec ->
            if (intro.any { it.section == sec }) FilterChip(open == sec, { open = if (open == sec) null else sec }, { Text(sec) }, Modifier.padding(end = 6.dp))
        }
    }
    intro.firstOrNull { it.section == open }?.let { sec ->
        Card(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) { LinkedText(sec.content, book, 0, MaterialTheme.typography.bodyMedium, onRef = onRef) }
        }
    }
}

@Composable
private fun Guide(text: String) {
    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Field(label: String, value: String, hint: String, minLines: Int = 3, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(vertical = 6.dp), label = { Text(label) }, placeholder = { Text(hint) }, minLines = minLines)
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value > 0) value.toString() else "") }
    OutlinedTextField(text, { t -> text = t.filter { it.isDigit() }.take(3); text.toIntOrNull()?.let(onChange) }, Modifier.width(96.dp), label = { Text(label) }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
}

fun exportStudy(s: Study, ref: String): String = buildString {
    appendLine(s.title); appendLine(ref); appendLine()
    fun sec(title: String, vararg fields: Pair<String, String>) {
        val f = fields.filter { it.second.isNotBlank() }; if (f.isEmpty()) return
        appendLine("== $title =="); f.forEach { (k, v) -> appendLine("$k:"); appendLine(v); appendLine() }
    }
    sec("CONTEXTO", "Histórico y cultural" to s.contextHistorical, "Literario" to s.contextLiterary)
    sec("EXÉGESIS", "Observaciones" to s.observation, "Palabras clave" to s.keyWords, "Estructura" to s.structure, "Idea exegética" to s.exegeticalIdea)
    sec("HERMENÉUTICA", "Significado original" to s.originalMeaning, "Principio" to s.principle, "Referencias cruzadas" to s.crossReferences, "Aplicación" to s.application)
    sec("HOMILÉTICA", "Proposición" to s.proposition, "Propósito" to s.purpose, "Bosquejo" to s.outline, "Ilustraciones" to s.illustrations, "Aplicaciones" to s.sermonApplication, "Conclusión" to s.conclusion)
}

/** Secciones del estudio (título → texto) para el PDF. */
fun studySections(s: Study): List<Pair<String, String>> {
    fun sec(title: String, vararg fields: Pair<String, String>): Pair<String, String> =
        title to fields.filter { it.second.isNotBlank() }.joinToString("\n") { (k, v) -> "# $k\n$v" }
    return listOf(
        sec("Contexto", "Histórico y cultural" to s.contextHistorical, "Literario" to s.contextLiterary),
        sec("Exégesis", "Observaciones" to s.observation, "Palabras clave" to s.keyWords, "Estructura" to s.structure, "Idea exegética" to s.exegeticalIdea),
        sec("Hermenéutica", "Significado original" to s.originalMeaning, "Principio" to s.principle, "Referencias cruzadas" to s.crossReferences, "Aplicación" to s.application),
        sec("Homilética", "Proposición" to s.proposition, "Propósito" to s.purpose, "Bosquejo" to s.outline, "Ilustraciones" to s.illustrations, "Aplicaciones" to s.sermonApplication, "Conclusión" to s.conclusion),
    )
}
