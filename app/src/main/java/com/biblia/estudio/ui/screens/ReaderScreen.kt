package com.biblia.estudio.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.*
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.data.user.TagVerse
import com.biblia.estudio.data.user.TopicVerse
import com.biblia.estudio.data.user.VerseNote
import com.biblia.estudio.ui.components.LinkedText
import com.biblia.estudio.ui.components.RefPreviewDialog
import com.biblia.estudio.ui.newStudy
import com.biblia.estudio.ui.openRef
import com.biblia.estudio.ui.theme.HighlightColors
import com.biblia.estudio.ui.theme.Serif
import com.biblia.estudio.ui.theme.TopicColors
import com.biblia.estudio.ui.theme.verseTextStyle
import kotlinx.coroutines.launch

private data class ChapterData(val verses: List<Verse>, val parallel: Map<Int, String>, val headings: List<Heading>, val noteVerses: Set<Int>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(nav: NavController, settings: AppSettings, argBook: Int, argChapter: Int, argVerse: Int, argVerseEnd: Int) {
    val ctx = LocalContext.current; val c = ctx.app; val scope = rememberCoroutineScope()
    var book by rememberSaveable(argBook, argChapter) { mutableIntStateOf(if (argBook > 0) argBook else settings.lastBook) }
    var chapter by rememberSaveable(argBook, argChapter) { mutableIntStateOf(if (argChapter > 0) argChapter else settings.lastChapter) }
    var target by rememberSaveable(argBook, argChapter) { mutableStateOf(if (argVerse > 0) argVerse to (if (argVerseEnd >= argVerse) argVerseEnd else argVerse) else null) }
    val bk = c.content.book(book)
    val data by produceState<ChapterData?>(null, book, chapter, settings.version, settings.parallel, settings.parallelVersion) {
        val verses = c.content.chapter(settings.version, book, chapter)
        val par = if (settings.parallel) c.content.chapter(settings.parallelVersion, book, chapter).associate { it.verse to it.text } else emptyMap()
        value = ChapterData(verses, par, if (settings.showHeadings) c.content.headings(settings.version, book, chapter) else emptyList(), c.content.chaptersWithNotes(book, chapter))
    }
    LaunchedEffect(book, chapter) { c.settings.update { it.copy(lastBook = book, lastChapter = chapter) } }
    val highlights by c.user.dao.highlightsForChapter(book, chapter).collectAsState(initial = emptyList())
    val notes by c.user.dao.notesForChapter(book, chapter).collectAsState(initial = emptyList())
    val bookmarks by c.user.dao.bookmarksForChapter(book, chapter).collectAsState(initial = emptyList())
    val tagged by c.user.dao.taggedVersesInChapter(book, chapter).collectAsState(initial = emptyList())
    val isRead by c.user.dao.isRead(book, chapter).collectAsState(initial = 0)
    val hl = highlights.associate { it.verse to it.color }
    val noteMap = notes.groupBy { it.verse }
    val bmSet = bookmarks.map { it.verse }.toSet()
    val taggedSet = tagged.toSet()

    var selection by remember(book, chapter) { mutableStateOf(setOf<Int>()) }
    var showPicker by remember { mutableStateOf(false) }
    var versionMenu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }   // notes | note | topic | tag
    var previewRef by remember { mutableStateOf<PassageRef?>(null) }
    val listState = rememberLazyListState()
    val speakKey = "cap:$book:$chapter:${settings.version}"
    val speakingKey by c.speaker.speaking.collectAsState()
    val speakingIdx by c.speaker.current.collectAsState()
    val isSpeaking = speakingKey == speakKey
    DisposableEffect(book, chapter) { onDispose { if (c.speaker.speaking.value?.startsWith("cap:") == true) c.speaker.stop() } }

    fun go(b: Int, ch: Int) { book = b; chapter = ch; target = null; selection = emptySet(); scope.launch { listState.scrollToItem(0) } }
    fun next() { if (chapter < bk.chapters) go(book, chapter + 1) else if (book < 66) go(book + 1, 1) }
    fun prev() { if (chapter > 1) go(book, chapter - 1) else if (book > 1) go(book - 1, c.content.book(book - 1).chapters) }

    // Ítems de la lista: encabezados y versículos
    val items = remember(data) {
        val d = data ?: return@remember emptyList<Any>()
        val byVerse = d.headings.groupBy { it.beforeVerse }
        buildList { d.verses.forEach { v -> byVerse[v.verse]?.forEach { add(it) }; add(v) } }
    }
    LaunchedEffect(speakingIdx) {
        if (isSpeaking && speakingIdx > 0) {
            val v = data?.verses?.getOrNull(speakingIdx - 1)
            val idx = items.indexOfFirst { it === v }
            if (idx >= 0) listState.animateScrollToItem((idx).coerceAtLeast(0))
        }
    }
    LaunchedEffect(items, target) {
        val t = target ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it is Verse && it.verse == t.first }
        if (idx >= 0) listState.scrollToItem((idx - 1).coerceAtLeast(0))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { showPicker = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${bk.name} $chapter", maxLines = 1); Icon(Icons.Filled.ArrowDropDown, null)
                    }
                },
                actions = {
                    Box {
                        AssistChip(onClick = { versionMenu = true }, label = { Text(settings.version + if (settings.parallel) " | ${settings.parallelVersion}" else "") })
                        DropdownMenu(versionMenu, { versionMenu = false }) {
                            Text("Versión principal", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
                            c.content.versions.forEach { v ->
                                DropdownMenuItem(text = { Text("${v.id} — ${v.name}") }, leadingIcon = { if (v.id == settings.version) Icon(Icons.Filled.Check, null) },
                                    onClick = { versionMenu = false; scope.launch { c.settings.update { it.copy(version = v.id) } } })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(if (settings.parallel) "Desactivar vista paralela" else "Vista paralela (comparar)") }, leadingIcon = { Icon(Icons.Filled.VerticalSplit, null) },
                                onClick = { versionMenu = false; scope.launch { c.settings.update { it.copy(parallel = !it.parallel) } } })
                            if (settings.parallel) {
                                Text("Versión paralela", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
                                c.content.versions.filter { it.id != settings.version }.forEach { v ->
                                    DropdownMenuItem(text = { Text(v.id) }, leadingIcon = { if (v.id == settings.parallelVersion) Icon(Icons.Filled.Check, null) },
                                        onClick = { versionMenu = false; scope.launch { c.settings.update { it.copy(parallelVersion = v.id) } } })
                                }
                            }
                        }
                    }
                    IconButton(onClick = {
                        val vs = data?.verses.orEmpty()
                        c.speaker.toggle(speakKey, listOf("${bk.name}, capítulo $chapter.") + vs.map { it.text })
                    }) { Icon(if (isSpeaking) Icons.Filled.StopCircle else Icons.Filled.VolumeUp, if (isSpeaking) "Detener lectura" else "Escuchar capítulo") }
                    IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Filled.Search, "Buscar") }
                    IconButton(onClick = { nav.navigate("commentary/$book/$chapter") }) { Icon(Icons.AutoMirrored.Filled.Notes, "Notas de estudio del capítulo") }
                }
            )
        },
        bottomBar = {
            if (selection.isNotEmpty()) SelectionBar(
                count = selection.size,
                onColor = { color -> scope.launch { selection.forEach { v -> c.user.setHighlight(book, chapter, v, color) }; selection = emptySet() } },
                onAction = { a ->
                    val sorted = selection.sorted()
                    val ref = PassageRef(book, chapter, sorted.first(), chapter, sorted.last())
                    when (a) {
                        "notes" -> sheet = "notes"
                        "note" -> sheet = "note"
                        "topic" -> sheet = "topic"
                        "tag" -> sheet = "tag"
                        "compare" -> nav.navigate("compare/$book/$chapter/${sorted.first()}")
                        "study" -> nav.newStudy(ref)
                        "memory" -> scope.launch {
                            val r = c.user.addMemory(book, chapter, sorted.first(), sorted.last())
                            Toast.makeText(ctx, if (r == -1L) "Ya está en tu lista de memorización" else "Añadido a Memorizar", Toast.LENGTH_SHORT).show()
                            selection = emptySet()
                        }
                        "copy", "share" -> {
                            val txt = data?.verses?.filter { it.verse in selection }?.joinToString(" ") { "${it.verse} ${it.text}" } ?: ""
                            val full = "«$txt» — ${c.content.label(ref)} (${settings.version})"
                            if (a == "copy") {
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("versículo", full))
                                Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
                            } else shareText(ctx, full)
                            selection = emptySet()
                        }
                        "bookmark" -> scope.launch {
                            val v = sorted.first()
                            if (v in bmSet) c.user.dao.deleteBookmark(book, chapter, v) else c.user.dao.addBookmark(com.biblia.estudio.data.user.Bookmark(book = book, chapter = chapter, verse = v))
                            selection = emptySet()
                        }
                        "clear" -> selection = emptySet()
                    }
                }
            )
        }
    ) { pad ->
        val d = data
        if (d == null) { Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        var dragX by remember { mutableFloatStateOf(0f) }
        LazyColumn(state = listState, modifier = Modifier.padding(pad).fillMaxSize().pointerInput(book, chapter) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onDragEnd = { if (dragX < -180f) next() else if (dragX > 180f) prev(); dragX = 0f },
                    onDragCancel = { dragX = 0f },
                    onHorizontalDrag = { _, dx -> dragX += dx }
                )
            }, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
            item {
                Text("${bk.name} $chapter", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
                if (chapter == 1) TextButton(onClick = { nav.navigate("intro/$book") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Info, null); Spacer(Modifier.width(6.dp)); Text("Introducción a ${bk.name} (MacArthur)")
                }
            }
            items(items) { it ->
                when (it) {
                    is Heading -> Text(it.text,
                        style = if (it.kind == "label") MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif) else MaterialTheme.typography.titleSmall,
                        color = if (it.kind == "label") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    is Verse -> VerseRow(
                        v = it, settings = settings, parallel = d.parallel[it.verse],
                        highlight = hl[it.verse], selected = it.verse in selection,
                        speaking = isSpeaking && speakingIdx - 1 == d.verses.indexOf(it),
                        targeted = target?.let { t -> it.verse in t.first..t.second } == true,
                        hasStudyNote = settings.showNoteMarkers && it.verse in d.noteVerses,
                        hasNote = noteMap.containsKey(it.verse), bookmarked = it.verse in bmSet, tagged = it.verse in taggedSet,
                        onClick = { selection = if (it.verse in selection) selection - it.verse else selection + it.verse },
                        onStudyNote = { selection = setOf(it.verse); sheet = "notes" }
                    )
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                val read = isRead > 0
                if (read) OutlinedButton(onClick = { scope.launch { c.user.markChapterRead(book, chapter, false) } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Capítulo leído")
                } else Button(onClick = { scope.launch { c.user.markChapterRead(book, chapter, true); Toast.makeText(ctx, "+10 puntos", Toast.LENGTH_SHORT).show() } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Done, null); Spacer(Modifier.width(8.dp)); Text("Marcar como leído")
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    OutlinedButton(onClick = { prev() }, enabled = book > 1 || chapter > 1) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Anterior") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { next() }, enabled = book < 66 || chapter < bk.chapters) { Text("Siguiente "); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                }
                val copyright = c.content.versions.firstOrNull { it.id == settings.version }?.copyright.orEmpty()
                Text(copyright, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }

    if (showPicker) BookChapterPicker(c.content.books, book, onDismiss = { showPicker = false }, pickVerse = true) { b, ch, v ->
        showPicker = false; go(b, ch)
        if (v > 0) target = v to v
    }
    val first = selection.minOrNull()
    when (sheet) {
        "notes" -> if (first != null) StudyNotesSheet(book, chapter, first, onDismiss = { sheet = null }, onRef = { previewRef = it },
            onOpenCommentary = { sheet = null; nav.navigate("commentary/$book/$chapter") })
        "note" -> if (first != null) NoteDialog(book, chapter, first, noteMap[first]?.firstOrNull(), onDismiss = { sheet = null; selection = emptySet() })
        "topic" -> if (first != null) TopicPickerDialog(book, chapter, selection.sorted(), onDismiss = { sheet = null; selection = emptySet() })
        "tag" -> if (first != null) TagPickerDialog(book, chapter, selection.sorted(), onDismiss = { sheet = null; selection = emptySet() })
    }
    previewRef?.let { r -> RefPreviewDialog(r, onDismiss = { previewRef = null }, onOpen = { sheet = null; nav.openRef(it) }) }
}

@Composable
private fun VerseRow(
    v: Verse, settings: AppSettings, parallel: String?, highlight: Int?, selected: Boolean, targeted: Boolean, speaking: Boolean,
    hasStudyNote: Boolean, hasNote: Boolean, bookmarked: Boolean, tagged: Boolean, onClick: () -> Unit, onStudyNote: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val bg = when {
        speaking -> MaterialTheme.colorScheme.tertiaryContainer
        highlight != null -> Color(highlight).copy(alpha = 0.45f)
        targeted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val style = verseTextStyle(settings.fontSize, settings.lineHeight)
    Column(
        Modifier.fillMaxWidth().padding(top = if (v.para) 6.dp else 0.dp).clip(RoundedCornerShape(6.dp)).background(bg)
            .then(if (selected) Modifier.border(2.dp, primary, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(buildAnnotatedString {
            if (settings.showVerseNumbers) withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold, fontSize = (settings.fontSize * 0.65f).sp, baselineShift = BaselineShift(0.3f))) { append(v.label + " ") }
            append(v.text)
            if (bookmarked) withStyle(SpanStyle(color = primary)) { append("  🔖") }
            if (hasNote) withStyle(SpanStyle(color = primary)) { append("  ✎") }
            if (tagged) withStyle(SpanStyle(color = primary)) { append("  #") }
        }, style = style)
        if (parallel != null) Text(parallel, style = style.copy(fontSize = (settings.fontSize * 0.88f).sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 4.dp))
        if (hasStudyNote) Text("Nota de estudio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.clickable(onClick = onStudyNote).padding(vertical = 2.dp))
    }
}

@Composable
private fun SelectionBar(count: Int, onColor: (Int?) -> Unit, onAction: (String) -> Unit) {
    Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$count seleccionado${if (count > 1) "s" else ""}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                HighlightColors.forEach { col ->
                    Box(Modifier.padding(3.dp).size(28.dp).clip(CircleShape).background(Color(col)).clickable { onColor(col) })
                }
                IconButton(onClick = { onColor(null) }) { Icon(Icons.Filled.FormatColorReset, "Quitar resaltado") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                @Composable fun A(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, key: String) =
                    TextButton(onClick = { onAction(key) }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Text(label, style = MaterialTheme.typography.labelSmall) } }
                A(Icons.AutoMirrored.Filled.MenuBook, "MacArthur", "notes")
                A(Icons.Filled.EditNote, "Nota", "note")
                A(Icons.Filled.FolderSpecial, "Tema", "topic")
                A(Icons.AutoMirrored.Filled.Label, "Etiqueta", "tag")
                A(Icons.Filled.CompareArrows, "Comparar", "compare")
                A(Icons.Filled.School, "Estudiar", "study")
                A(Icons.Filled.Psychology, "Memorizar", "memory")
                A(Icons.Filled.Bookmark, "Marcador", "bookmark")
                A(Icons.Filled.ContentCopy, "Copiar", "copy")
                A(Icons.Filled.Share, "Compartir", "share")
                A(Icons.Filled.Close, "Cerrar", "clear")
            }
        }
    }
}

@Composable
fun BookChapterPicker(books: List<Book>, current: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) =
    BookChapterPicker(books, current, onDismiss, pickVerse = false) { b, ch, _ -> onPick(b, ch) }

/** Selector en pasos: libro → capítulo → versículo (si pickVerse). verse = 0 significa capítulo completo. */
@Composable
fun BookChapterPicker(books: List<Book>, current: Int, onDismiss: () -> Unit, pickVerse: Boolean, onPick: (Int, Int, Int) -> Unit) {
    val c = LocalContext.current.app
    var selBook by remember { mutableStateOf<Book?>(null) }
    var selChapter by remember { mutableIntStateOf(0) }
    val verseCount by produceState(0, selBook, selChapter) { value = if (selBook != null && selChapter > 0) c.content.verseCount(selBook!!.id, selChapter) else 0 }
    var testament by remember { mutableStateOf(if (current >= 40) "NT" else "AT") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { selBook == null -> "Elegir libro"; selChapter > 0 -> "${selBook!!.name} $selChapter · versículo"; else -> selBook!!.name }) },
        text = {
            Column(Modifier.heightIn(max = 480.dp)) {
                val sb = selBook
                if (sb == null) {
                    Row { FilterChip(testament == "AT", { testament = "AT" }, { Text("Antiguo T.") }); Spacer(Modifier.width(8.dp)); FilterChip(testament == "NT", { testament = "NT" }, { Text("Nuevo T.") }) }
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.padding(top = 8.dp)) {
                        items(books.filter { it.testament == testament }) { b ->
                            OutlinedButton(onClick = { selBook = b; if (b.chapters == 1) { if (pickVerse) selChapter = 1 else onPick(b.id, 1, 0) } }, modifier = Modifier.padding(2.dp), contentPadding = PaddingValues(4.dp),
                                colors = if (b.id == current) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()) {
                                Text(b.name, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                } else if (selChapter == 0) {
                    LazyVerticalGrid(GridCells.Adaptive(52.dp)) {
                        items((1..sb.chapters).toList()) { ch ->
                            OutlinedButton(onClick = { if (pickVerse) selChapter = ch else onPick(sb.id, ch, 0) }, modifier = Modifier.padding(2.dp), contentPadding = PaddingValues(0.dp)) { Text("$ch") }
                        }
                    }
                } else {
                    TextButton(onClick = { onPick(sb.id, selChapter, 0) }) { Text("Ir al capítulo completo") }
                    LazyVerticalGrid(GridCells.Adaptive(52.dp)) {
                        items((1..verseCount).toList()) { v ->
                            OutlinedButton(onClick = { onPick(sb.id, selChapter, v) }, modifier = Modifier.padding(2.dp), contentPadding = PaddingValues(0.dp)) { Text("$v") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when { selChapter > 0 && (selBook?.chapters ?: 0) > 1 -> selChapter = 0; selBook != null -> { selBook = null; selChapter = 0 }; else -> onDismiss() }
            }) { Text(when { selChapter > 0 && (selBook?.chapters ?: 0) > 1 -> "Capítulos"; selBook != null -> "Libros"; else -> "Cerrar" }) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyNotesSheet(book: Int, chapter: Int, verse: Int, onDismiss: () -> Unit, onRef: (PassageRef) -> Unit, onOpenCommentary: () -> Unit) {
    val c = LocalContext.current.app
    val notes by produceState<List<StudyNote>?>(null, book, chapter, verse) { value = c.content.notesForVerse(book, chapter, verse) }
    val xrefs by produceState(emptyList<String>(), book, chapter, verse) { value = c.content.crossRefs(book, chapter, verse) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text("${c.content.book(book).name} $chapter:$verse", style = MaterialTheme.typography.titleLarge)
                Text("Notas de la Biblia de Estudio MacArthur", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            val n = notes
            if (n == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (n.isEmpty()) item { Text("No hay nota de estudio para este versículo. Consulta las notas del capítulo.", style = MaterialTheme.typography.bodyMedium) }
            else items(n) { note ->
                Text(c.content.label(note.ref), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                LinkedText(note.text, book, chapter, MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif), onRef = onRef)
            }
            if (xrefs.isNotEmpty()) item {
                Text("Referencias cruzadas (RVR1960)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                LinkedText(xrefs.joinToString("  "), book, chapter, onRef = onRef)
            }
            item { TextButton(onClick = onOpenCommentary, modifier = Modifier.padding(top = 12.dp)) { Text("Ver todas las notas del capítulo") } }
        }
    }
}

@Composable
private fun NoteDialog(book: Int, chapter: Int, verse: Int, existing: VerseNote?, onDismiss: () -> Unit) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(existing?.text ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nota · ${c.content.book(book).name} $chapter:$verse") },
        text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp), placeholder = { Text("Escribe tu reflexión, aplicación u observación…") }) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (text.isBlank()) existing?.let { c.user.dao.deleteNote(it) }
                    else c.user.dao.upsertNote((existing ?: VerseNote(book = book, chapter = chapter, verse = verse, text = "")).copy(text = text.trim(), updated = System.currentTimeMillis()))
                    onDismiss()
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { scope.launch { c.user.dao.deleteNote(existing); onDismiss() } }) { Text("Eliminar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun TopicPickerDialog(book: Int, chapter: Int, verses: List<Int>, onDismiss: () -> Unit) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    val topics by c.user.dao.topics().collectAsState(initial = emptyList())
    val current by produceState(emptySet<Long>(), verses) { value = c.user.dao.topicsOfVerse(book, chapter, verses.first()).toSet() }
    var chosen by remember(current) { mutableStateOf(current) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar a temas") },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Nuevo tema (p. ej. Fe)") })
                    IconButton(onClick = {
                        if (newName.isNotBlank()) scope.launch { val id = c.user.createTopic(newName, "", TopicColors[(topics.size) % TopicColors.size]); chosen = chosen + id; newName = "" }
                    }) { Icon(Icons.Filled.Add, "Crear") }
                }
                LazyColumn {
                    items(topics) { t ->
                        Row(Modifier.fillMaxWidth().clickable { chosen = if (t.id in chosen) chosen - t.id else chosen + t.id }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(t.id in chosen, { chosen = if (t.id in chosen) chosen - t.id else chosen + t.id })
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(t.color)))
                            Text("  ${t.name}", modifier = Modifier.weight(1f)); Text("${t.count}", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                if (topics.isEmpty()) Text("Crea tu primer tema para agrupar versículos.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val first = verses.first(); val last = verses.last()
                    (chosen - current).forEach { id -> c.user.dao.addTopicVerse(TopicVerse(topicId = id, book = book, chapter = chapter, verse = first, verseEnd = last)) }
                    (current - chosen).forEach { id -> c.user.dao.removeTopicVerse(id, book, chapter, first) }
                    onDismiss()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerDialog(book: Int, chapter: Int, verses: List<Int>, onDismiss: () -> Unit) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    val tags by c.user.dao.tags().collectAsState(initial = emptyList())
    val current by produceState(emptySet<Long>(), verses) { value = c.user.dao.tagsOfVerse(book, chapter, verses.first()).toSet() }
    var chosen by remember(current) { mutableStateOf(current) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiquetas") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Nueva etiqueta (p. ej. promesa)") })
                    IconButton(onClick = { if (newName.isNotBlank()) scope.launch { val id = c.user.createTag(newName); chosen = chosen + id; newName = "" } }) { Icon(Icons.Filled.Add, "Crear") }
                }
                FlowRow(Modifier.padding(top = 8.dp)) {
                    tags.forEach { t ->
                        FilterChip(t.id in chosen, { chosen = if (t.id in chosen) chosen - t.id else chosen + t.id }, { Text("#${t.name}") }, Modifier.padding(end = 6.dp))
                    }
                }
                if (tags.isEmpty()) Text("Sugerencias: promesa, mandato, oración, consuelo, profecía, Cristo, familia.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    (chosen - current).forEach { id -> verses.forEach { v -> c.user.dao.addTagVerse(TagVerse(tagId = id, book = book, chapter = chapter, verse = v)) } }
                    (current - chosen).forEach { id -> verses.forEach { v -> c.user.dao.removeTagVerse(id, book, chapter, v) } }
                    onDismiss()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
