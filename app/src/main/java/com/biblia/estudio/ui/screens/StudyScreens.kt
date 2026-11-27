package com.biblia.estudio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.*
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.data.user.today
import com.biblia.estudio.ui.components.*
import com.biblia.estudio.ui.openRef
import com.biblia.estudio.ui.theme.Serif
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyHubScreen(nav: NavController) {
    val c = LocalContext.current.app
    val articles by produceState(emptyList<Article>()) { value = c.content.articles() }
    val studies by c.user.dao.studies().collectAsState(initial = emptyList())
    var picker by remember { mutableStateOf<String?>(null) }  // intro | commentary | study
    Scaffold(topBar = { TopAppBar(title = { Text("Estudio bíblico") }, actions = { IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Filled.Search, "Buscar") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                InfoCard {
                    Text("Estudio de un pasaje", style = MaterialTheme.typography.titleMedium)
                    Text("Recorre paso a paso el contexto, la exégesis, la hermenéutica y la homilética de un pasaje, con las notas de MacArthur a mano.", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(onClick = { picker = "study" }) { Icon(Icons.Filled.Add, null); Text(" Nuevo estudio") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { nav.navigate("studies") }) { Text("Mis estudios (${studies.size})") }
                    }
                }
            }
            item { SectionHeader("Biblia de Estudio MacArthur") }
            item { HubItem(Icons.Filled.Info, "Introducciones a los libros", "Título, autor y fecha, contexto, temas, retos de interpretación y bosquejo") { picker = "intro" } }
            item { HubItem(Icons.AutoMirrored.Filled.Notes, "Notas versículo a versículo", "Más de 17.000 notas de estudio por capítulo") { picker = "commentary" } }
            item { HubItem(Icons.Filled.Translate, "Diccionario bíblico", "Personas, lugares, términos, objetos y fiestas") { nav.navigate("dictionary") } }
            item { HubItem(Icons.Filled.ListAlt, "Índice temático", "Más de 600 temas con sus referencias") { nav.navigate("index") } }
            item { HubItem(Icons.Filled.CastForEducation, "Lecciones", "Curso diario: panorama de la Biblia y doctrina") { nav.navigate("lessons") } }
            val method = articles.filter { it.category == "Método de estudio" }
            if (method.isNotEmpty()) {
                item { SectionHeader("Método: contexto, exégesis, hermenéutica y homilética") }
                items(method) { a -> HubItem(Icons.Filled.Lightbulb, a.title, null) { nav.navigate("article/${a.id}") } }
            }
            articles.filter { it.category != "Método de estudio" }.groupBy { it.category }.forEach { (cat, list) ->
                item { SectionHeader(cat) }
                items(list) { a -> HubItem(Icons.AutoMirrored.Filled.MenuBook, a.title, null) { nav.navigate("article/${a.id}") } }
            }
        }
    }
    when (picker) {
        "intro" -> BookChapterPickerBooksOnly(c.content.books, { picker = null }) { b -> picker = null; nav.navigate("intro/$b") }
        "commentary" -> BookChapterPicker(c.content.books, 1, { picker = null }) { b, ch -> picker = null; nav.navigate("commentary/$b/$ch") }
        "study" -> BookChapterPicker(c.content.books, 1, { picker = null }) { b, ch -> picker = null; nav.navigate("studyedit/0?b=$b&c=$ch&v=0&ce=$ch&ve=0") }
    }
}

@Composable
private fun HubItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
fun BookChapterPickerBooksOnly(books: List<Book>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Elegir libro") }, text = {
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            items(books) { b -> Text(b.name, Modifier.fillMaxWidth().clickable { onPick(b.id) }.padding(vertical = 10.dp)) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
fun IntroScreen(nav: NavController, settings: AppSettings, book: Int) {
    val c = LocalContext.current.app
    val sections by produceState<List<IntroSection>?>(null, book) { value = c.content.intro(book) }
    var preview by remember { mutableStateOf<PassageRef?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val b = c.content.book(book)
    Scaffold(topBar = { BackTopBar("Introducción a ${b.name}", { nav.popBackStack() }) }) { pad ->
        val s = sections ?: return@Scaffold Loading()
        Column(Modifier.padding(pad)) {
            if (s.size > 1) ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                s.forEachIndexed { i, sec -> Tab(tab == i, { tab = i }, text = { Text(sec.section) }) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                val sec = s.getOrNull(tab)
                if (sec == null) Text("No hay introducción disponible.")
                else {
                    Text(sec.section, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    if (sec.section.startsWith("Bosquejo")) {
                        sec.content.split("\n").forEach { line ->
                            val indent = when { Regex("^[IVXLC]+\\.").containsMatchIn(line) -> 0; Regex("^[A-Z]\\.").containsMatchIn(line) -> 1; else -> 2 }
                            LinkedText(line, book, 0, MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif), Modifier.padding(start = (indent * 16).dp, top = 3.dp)) { preview = it }
                        }
                    } else RichArticle(sec.content, book, { preview = it }, settings.fontSize - 1)
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    OutlinedButton(onClick = { nav.openRef(PassageRef(book, 1)) }) { Text("Leer ${b.name} 1") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { nav.navigate("commentary/$book/1") }) { Text("Notas del cap. 1") }
                }
                Text("Fuente: Biblia de Estudio MacArthur (uso personal).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
    preview?.let { RefPreviewDialog(it, { preview = null }) { r -> nav.openRef(r) } }
}

@Composable
fun CommentaryScreen(nav: NavController, settings: AppSettings, book: Int, chapter0: Int) {
    val c = LocalContext.current.app
    var chapter by rememberSaveable { mutableIntStateOf(chapter0) }
    val b = c.content.book(book)
    val notes by produceState<List<StudyNote>?>(null, book, chapter) { value = null; value = c.content.notesForChapter(book, chapter) }
    var preview by remember { mutableStateOf<PassageRef?>(null) }
    Scaffold(topBar = { BackTopBar("Notas · ${b.name} $chapter", { nav.popBackStack() }) {
        IconButton(onClick = { nav.openRef(PassageRef(book, chapter)) }) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Leer capítulo") }
    } }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp)) {
            item {
                Row {
                    OutlinedButton(onClick = { chapter-- }, enabled = chapter > 1) { Text("‹ Cap. ${chapter - 1}") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { chapter++ }, enabled = chapter < b.chapters) { Text("Cap. ${chapter + 1} ›") }
                }
            }
            val n = notes
            if (n == null) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp)) }
            else if (n.isEmpty()) item { Text("No hay notas para este capítulo.", Modifier.padding(top = 16.dp)) }
            else items(n) { note ->
                Text(c.content.label(note.ref), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp).clickable { nav.openRef(note.ref) })
                LinkedText(note.text, book, chapter, MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif)) { preview = it }
            }
        }
    }
    preview?.let { RefPreviewDialog(it, { preview = null }) { r -> nav.openRef(r) } }
}

@Composable
fun ArticleScreen(nav: NavController, settings: AppSettings, id: Int) {
    val c = LocalContext.current.app
    val a by produceState<Article?>(null, id) { value = c.content.article(id) }
    var preview by remember { mutableStateOf<PassageRef?>(null) }
    Scaffold(topBar = { BackTopBar(a?.category ?: "", { nav.popBackStack() }) }) { pad ->
        val art = a ?: return@Scaffold Loading()
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(art.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            RichArticle(art.content, 0, { preview = it }, settings.fontSize - 1)
        }
    }
    preview?.let { RefPreviewDialog(it, { preview = null }) { r -> nav.openRef(r) } }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LessonScreen(nav: NavController, settings: AppSettings, id0: Int) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    var id by rememberSaveable { mutableIntStateOf(id0) }
    val lesson by produceState<Lesson?>(null, id) { value = c.content.lesson(id) }
    val total by produceState(0) { value = c.content.lessonCount() }
    val todayId by produceState(-1) { value = c.content.lessonOfDay(today()).id }
    var preview by remember { mutableStateOf<PassageRef?>(null) }
    val reflection by c.user.dao.reflection(id).collectAsState(initial = null)
    // La reflexión se edita en local y se guarda sola (no se pisa con lo guardado mientras se escribe)
    var draft by remember(id) { mutableStateOf<String?>(null) }
    val text = draft ?: reflection?.text ?: ""
    var saveNote by remember(id) { mutableStateOf("") }
    suspend fun saveReflection() {
        val d = draft ?: return
        if (d.trim() == (reflection?.text ?: "")) return
        if (d.isBlank()) c.user.dao.deleteReflection(id) else c.user.dao.upsertReflection(com.biblia.estudio.data.user.LessonReflection(id, d.trim()))
        saveNote = if (d.isBlank()) "" else "Reflexión guardada ✓"
    }
    LaunchedEffect(draft) { if (draft != null) { saveNote = "Guardando…"; kotlinx.coroutines.delay(1000); saveReflection() } }
    // Al salir se guarda lo pendiente
    DisposableEffect(id) { onDispose { val d = draft; if (d != null && d.trim() != (reflection?.text ?: "")) kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        if (d.isBlank()) c.user.dao.deleteReflection(id) else c.user.dao.upsertReflection(com.biblia.estudio.data.user.LessonReflection(id, d.trim())) } } }
    val doneToday by c.user.dao.devotionalDone(today()).collectAsState(initial = emptyList())
    val lessonDone = doneToday.any { it.type == "lesson" }
    val ctx = LocalContext.current
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val speaking by c.speaker.speaking.collectAsState()
    DisposableEffect(id) { onDispose { if (c.speaker.speaking.value?.startsWith("lesson") == true) c.speaker.stop() } }
    Scaffold(topBar = { BackTopBar("Lección ${id} de $total", { nav.popBackStack() }) {
        IconButton(onClick = { lesson?.let { l -> c.speaker.toggle("lesson$id", listOf(l.title) + l.content.split(Regex("\n\\s*\n")).map { it.removePrefix("## ").removePrefix("- ") }) } }) {
            Icon(if (speaking == "lesson$id") Icons.Filled.StopCircle else Icons.Filled.VolumeUp, "Escuchar lección")
        }
    } }) { pad ->
        val l = lesson ?: return@Scaffold Loading()
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp)) {
            if (id == todayId) AssistChip(onClick = {}, label = { Text("Lección de hoy") })
            Text(l.title, style = MaterialTheme.typography.headlineSmall)
            if (l.ref.isNotBlank()) {
                val r = c.refParser?.parseQuery(l.ref)
                if (r != null) TextButton(onClick = { nav.openRef(r) }) { Text("Pasaje clave: ${l.ref}") }
            }
            Spacer(Modifier.height(8.dp))
            RichArticle(l.content, l.book, { preview = it }, settings.fontSize - 1)
            Spacer(Modifier.height(20.dp))
            InfoCardPlain {
                Text("Para reflexionar", style = MaterialTheme.typography.titleMedium)
                l.questions.forEachIndexed { i, q -> Text("${i + 1}. $q", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                OutlinedTextField(text, { draft = it },
                    Modifier.fillMaxWidth().padding(top = 10.dp).bringIntoViewRequester(bring)
                        .onFocusEvent { f -> if (f.isFocused) scope.launch { kotlinx.coroutines.delay(350); bring.bringIntoView() } },
                    label = { Text("Mi reflexión") }, placeholder = { Text("Escribe tus respuestas y lo que Dios te enseña…") }, minLines = 4)
                Text(if (saveNote.isNotBlank()) saveNote else "Se guarda automáticamente", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
                if (l.book > 0) TextButton(onClick = { nav.navigate("intro/${l.book}") }) { Text("Ver introducción completa del libro") }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { id-- }, enabled = id > 1) { Text("‹ Anterior") }
                Spacer(Modifier.weight(1f))
                if (id == todayId) {
                    if (lessonDone) FilledTonalButton(onClick = {}, enabled = false) { Text("Completada ✓") }
                    else Button(onClick = {
                        scope.launch {
                            saveReflection()
                            val won = c.user.markDevotional("lesson")
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                android.widget.Toast.makeText(ctx, if (won) "Lección completada · +${com.biblia.estudio.data.user.Points.DEVOTIONAL} puntos" else "Lección completada", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("Completar") }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { id++ }, enabled = id < total) { Text("Siguiente ›") }
            }
        }
    }
    preview?.let { RefPreviewDialog(it, { preview = null }) { r -> nav.openRef(r) } }
}

@Composable
private fun InfoCardPlain(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun LessonsListScreen(nav: NavController) {
    val c = LocalContext.current.app
    val list by produceState(emptyList<Pair<Int, String>>()) { value = c.content.lessonTitles() }
    val todayId by produceState(-1) { value = c.content.lessonOfDay(today()).id }
    Scaffold(topBar = { BackTopBar("Lecciones", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            items(list) { (id, title) ->
                ListItem(leadingContent = { Text("$id", color = MaterialTheme.colorScheme.primary) }, headlineContent = { Text(title) },
                    trailingContent = { if (id == todayId) Text("Hoy", color = MaterialTheme.colorScheme.tertiary) },
                    modifier = Modifier.clickable { nav.navigate("lesson/$id") })
            }
        }
    }
}

@Composable
fun IndexScreen(nav: NavController) {
    val c = LocalContext.current.app
    var filter by rememberSaveable { mutableStateOf("") }
    val topics by produceState(emptyList<IndexTopic>(), filter) { value = c.content.indexTopics(filter) }
    Scaffold(topBar = { BackTopBar("Índice temático", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, placeholder = { Text("Buscar tema: fe, oración, perdón…") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
            LazyColumn {
                items(topics) { t -> ListItem(headlineContent = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }, modifier = Modifier.clickable { nav.navigate("index/${t.id}") }) }
            }
        }
    }
}

@Composable
fun IndexTopicScreen(nav: NavController, id: Int) {
    val c = LocalContext.current.app
    val data by produceState<Pair<String, List<String>>?>(null, id) { value = c.content.indexTopic(id) }
    var preview by remember { mutableStateOf<PassageRef?>(null) }
    Scaffold(topBar = { BackTopBar(data?.first?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "", { nav.popBackStack() }) }) { pad ->
        val d = data ?: return@Scaffold Loading()
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp)) {
            items(d.second) { e ->
                val isHeader = !Regex("\\d").containsMatchIn(e)
                if (isHeader) Text(e, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                else LinkedText(e, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp)) { preview = it }
            }
        }
    }
    preview?.let { RefPreviewDialog(it, { preview = null }) { r -> nav.openRef(r) } }
}

@Composable
fun StudiesListScreen(nav: NavController) {
    val c = LocalContext.current.app
    val studies by c.user.dao.studies().collectAsState(initial = emptyList())
    var picker by remember { mutableStateOf(false) }
    Scaffold(topBar = { BackTopBar("Mis estudios", { nav.popBackStack() }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { picker = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nuevo") }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (studies.isEmpty()) item { EmptyState(Icons.Filled.School, "Sin estudios todavía", "Selecciona versículos en la Biblia y toca «Estudiar», o crea uno nuevo.") }
            items(studies) { s ->
                ListItem(headlineContent = { Text(s.title) },
                    supportingContent = { Text(c.content.label(PassageRef(s.book, s.chapter, s.verse, s.chapterEnd, s.verseEnd)) + " · " + DateFormat.getDateInstance().format(Date(s.updated))) },
                    overlineContent = { if (s.proposition.isNotBlank()) Text(s.proposition, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { nav.navigate("studyedit/${s.id}") })
            }
        }
    }
    if (picker) BookChapterPicker(c.content.books, 1, { picker = false }) { b, ch -> picker = false; nav.navigate("studyedit/0?b=$b&c=$ch&v=0&ce=$ch&ve=0") }
}
