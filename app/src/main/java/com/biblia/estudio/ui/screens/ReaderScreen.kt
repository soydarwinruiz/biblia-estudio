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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.luminance
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

/** Un capítulo cargado en el lector. */
private data class ChapterBlock(val book: Int, val chapter: Int, val verses: List<Verse>, val parallel: Map<Int, String>, val headings: List<Heading>, val noteVerses: Set<Int>)

/** Elemento de la lista del lector. */
private sealed interface RItem {
    val book: Int; val chapter: Int
    data class Title(override val book: Int, override val chapter: Int) : RItem
    data class Head(override val book: Int, override val chapter: Int, val h: Heading, val idx: Int) : RItem
    data class V(override val book: Int, override val chapter: Int, val v: Verse) : RItem
    data class End(override val book: Int, override val chapter: Int) : RItem
}

private data class VKey(val book: Int, val chapter: Int, val verse: Int)

private fun key(b: Int, c: Int, v: Int) = b * 1_000_000L + c * 1000L + v

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(nav: NavController, settings: AppSettings, argBook: Int, argChapter: Int, argVerse: Int, argVerseEnd: Int) {
    val ctx = LocalContext.current; val c = ctx.app; val scope = rememberCoroutineScope()
    var startBook by rememberSaveable(argBook, argChapter) { mutableIntStateOf(if (argBook > 0) argBook else settings.lastBook) }
    var startChapter by rememberSaveable(argBook, argChapter) { mutableIntStateOf(if (argChapter > 0) argChapter else settings.lastChapter) }
    var target by remember(argBook, argChapter) { mutableStateOf(if (argVerse > 0) VKey(startBook, startChapter, argVerse) to (if (argVerseEnd >= argVerse) argVerseEnd else argVerse) else null) }
    val continuous = settings.continuous

    suspend fun load(b: Int, ch: Int): ChapterBlock {
        val verses = c.content.chapter(settings.version, b, ch)
        val par = if (settings.parallel) c.content.chapter(settings.parallelVersion, b, ch).associate { it.verse to it.text } else emptyMap()
        return ChapterBlock(b, ch, verses, par, if (settings.showHeadings) c.content.headings(settings.version, b, ch) else emptyList(), c.content.chaptersWithNotes(b, ch))
    }
    fun nextOf(b: Int, ch: Int): Pair<Int, Int>? = if (ch < c.content.book(b).chapters) b to ch + 1 else if (b < 66) b + 1 to 1 else null
    fun prevOf(b: Int, ch: Int): Pair<Int, Int>? = if (ch > 1) b to ch - 1 else if (b > 1) b - 1 to c.content.book(b - 1).chapters else null

    var blocks by remember { mutableStateOf<List<ChapterBlock>>(emptyList()) }
    var loadingMore by remember { mutableStateOf(false) }
    LaunchedEffect(startBook, startChapter, settings.version, settings.parallel, settings.parallelVersion, settings.showHeadings, continuous) {
        blocks = emptyList()
        val first = load(startBook, startChapter)
        blocks = if (continuous) listOfNotNull(first, nextOf(startBook, startChapter)?.let { load(it.first, it.second) }) else listOf(first)
    }

    // Datos personales de todos los capítulos (filtrados al pintar)
    val highlights by c.user.dao.allHighlights().collectAsState(initial = emptyList())
    val notes by c.user.dao.allNotes().collectAsState(initial = emptyList())
    val bookmarks by c.user.dao.bookmarks().collectAsState(initial = emptyList())
    val tagged by c.user.dao.allTaggedKeys().collectAsState(initial = emptyList())
    val readList by c.user.dao.chaptersRead().collectAsState(initial = emptyList())
    val wordHl by c.user.dao.wordHighlights(settings.version).collectAsState(initial = emptyList())
    val hl = remember(highlights) { highlights.associate { key(it.book, it.chapter, it.verse) to it.color } }
    val noteMap = remember(notes) { notes.groupBy { key(it.book, it.chapter, it.verse) } }
    val bmSet = remember(bookmarks) { bookmarks.map { key(it.book, it.chapter, it.verse) }.toSet() }
    val taggedSet = remember(tagged) { tagged.toSet() }
    val readSet = remember(readList) { readList.map { it.book * 1000 + it.chapter }.toSet() }
    val wordMap = remember(wordHl) { wordHl.groupBy { key(it.book, it.chapter, it.verse) } }

    var selection by remember { mutableStateOf(setOf<VKey>()) }
    var showPicker by remember { mutableStateOf(false) }
    var versionMenu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }   // notes | note | topic | tag | words
    var previewRef by remember { mutableStateOf<PassageRef?>(null) }
    val listState = rememberLazyListState()

    val items = remember(blocks) {
        buildList {
            blocks.forEach { b ->
                add(RItem.Title(b.book, b.chapter))
                val byVerse = b.headings.groupBy { it.beforeVerse }
                b.verses.forEach { v -> byVerse[v.verse]?.forEachIndexed { i, h -> add(RItem.Head(b.book, b.chapter, h, i)) }; add(RItem.V(b.book, b.chapter, v)) }
                add(RItem.End(b.book, b.chapter))
            }
        }
    }
    // Capítulo visible (para el título, «continuar leyendo» y acciones)
    val offset = if (continuous) 1 else 0   // en lectura continua hay un botón «capítulo anterior» antes de los ítems
    val visible by remember(items, offset) { derivedStateOf { items.getOrNull((listState.firstVisibleItemIndex - offset + 1).coerceAtLeast(0)) ?: items.firstOrNull() } }
    val book = visible?.book ?: startBook
    val chapter = visible?.chapter ?: startChapter
    val bk = c.content.book(book)
    LaunchedEffect(book, chapter) { c.settings.update { it.copy(lastBook = book, lastChapter = chapter) } }

    // Cargar el siguiente capítulo al acercarse al final (lectura continua)
    LaunchedEffect(listState, continuous, blocks) {
        if (!continuous) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (!loadingMore && blocks.isNotEmpty() && last >= items.size - 8) {
                val lb = blocks.last(); val n = nextOf(lb.book, lb.chapter) ?: return@collect
                loadingMore = true
                val nb = load(n.first, n.second)
                blocks = (blocks + nb).takeLast(40)
                loadingMore = false
            }
        }
    }
    LaunchedEffect(items, target) {
        val t = target ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it is RItem.V && it.book == t.first.book && it.chapter == t.first.chapter && it.v.verse == t.first.verse }
        if (idx >= 0) listState.scrollToItem((idx + offset - 1).coerceAtLeast(0))
    }

    // Lectura en voz alta del capítulo visible
    val speakKey = "cap:$book:$chapter:${settings.version}"
    val speakingKey by c.speaker.speaking.collectAsState()
    val speakingIdx by c.speaker.current.collectAsState()
    val isSpeaking = speakingKey == speakKey
    val speakBlock = blocks.firstOrNull { it.book == book && it.chapter == chapter }
    DisposableEffect(Unit) { onDispose { if (c.speaker.speaking.value?.startsWith("cap:") == true) c.speaker.stop() } }
    LaunchedEffect(speakingIdx) {
        if (isSpeaking && speakingIdx > 0) {
            val v = speakBlock?.verses?.getOrNull(speakingIdx - 1) ?: return@LaunchedEffect
            val idx = items.indexOfFirst { it is RItem.V && it.book == book && it.chapter == chapter && it.v.verse == v.verse }
            if (idx >= 0) listState.animateScrollToItem((idx + offset - 1).coerceAtLeast(0))
        }
    }

    fun go(b: Int, ch: Int, verse: Int = 0) {
        selection = emptySet(); startBook = b; startChapter = ch
        target = if (verse > 0) VKey(b, ch, verse) to verse else null
        scope.launch { listState.scrollToItem(0) }
    }
    fun next() { nextOf(book, chapter)?.let { go(it.first, it.second) } }
    fun prev() { prevOf(book, chapter)?.let { go(it.first, it.second) } }
    val selVerses = selection.map { it.verse }.sorted()
    val selBook = selection.firstOrNull()?.book ?: book
    val selChapter = selection.firstOrNull()?.chapter ?: chapter
    val selBlock = blocks.firstOrNull { it.book == selBook && it.chapter == selChapter }

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
                                    onClick = { versionMenu = false; startBook = book; startChapter = chapter; scope.launch { c.settings.update { it.copy(version = v.id) } } })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(if (continuous) "Lectura por capítulos" else "Lectura continua") }, leadingIcon = { Icon(Icons.Filled.ViewStream, null) },
                                onClick = { versionMenu = false; startBook = book; startChapter = chapter; scope.launch { c.settings.update { it.copy(continuous = !it.continuous) } } })
                            DropdownMenuItem(text = { Text(if (settings.parallel) "Desactivar vista paralela" else "Vista paralela (comparar)") }, leadingIcon = { Icon(Icons.Filled.VerticalSplit, null) },
                                onClick = { versionMenu = false; startBook = book; startChapter = chapter; scope.launch { c.settings.update { it.copy(parallel = !it.parallel) } } })
                            if (settings.parallel) {
                                Text("Versión paralela", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
                                c.content.versions.filter { it.id != settings.version }.forEach { v ->
                                    DropdownMenuItem(text = { Text(v.id) }, leadingIcon = { if (v.id == settings.parallelVersion) Icon(Icons.Filled.Check, null) },
                                        onClick = { versionMenu = false; startBook = book; startChapter = chapter; scope.launch { c.settings.update { it.copy(parallelVersion = v.id) } } })
                                }
                            }
                        }
                    }
                    IconButton(onClick = {
                        val vs = speakBlock?.verses.orEmpty()
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
                onColor = { color -> scope.launch { selection.forEach { k -> c.user.setHighlight(k.book, k.chapter, k.verse, color) }; selection = emptySet() } },
                onAction = { a ->
                    val ref = PassageRef(selBook, selChapter, selVerses.first(), selChapter, selVerses.last())
                    when (a) {
                        "notes" -> sheet = "notes"
                        "note" -> sheet = "note"
                        "topic" -> sheet = "topic"
                        "tag" -> sheet = "tag"
                        "words" -> sheet = "words"
                        "compare" -> nav.navigate("compare/$selBook/$selChapter/${selVerses.first()}")
                        "study" -> nav.newStudy(ref)
                        "memory" -> scope.launch {
                            val r = c.user.addMemory(selBook, selChapter, selVerses.first(), selVerses.last())
                            Toast.makeText(ctx, if (r == -1L) "Ya está en tu lista de memorización" else "Añadido a Memorizar", Toast.LENGTH_SHORT).show()
                            selection = emptySet()
                        }
                        "copy", "share" -> {
                            val txt = selBlock?.verses?.filter { it.verse in selVerses }?.joinToString(" ") { "${it.verse} ${it.text}" } ?: ""
                            val full = "«$txt» — ${c.content.label(ref)} (${settings.version})"
                            if (a == "copy") {
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("versículo", full))
                                Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
                            } else shareText(ctx, full)
                            selection = emptySet()
                        }
                        "bookmark" -> scope.launch {
                            val v = selVerses.first()
                            if (key(selBook, selChapter, v) in bmSet) c.user.dao.deleteBookmark(selBook, selChapter, v)
                            else c.user.dao.addBookmark(com.biblia.estudio.data.user.Bookmark(book = selBook, chapter = selChapter, verse = v))
                            selection = emptySet()
                        }
                        "clear" -> selection = emptySet()
                    }
                }
            )
        }
    ) { pad ->
        if (blocks.isEmpty()) { Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        var dragX by remember { mutableFloatStateOf(0f) }
        LazyColumn(state = listState, modifier = Modifier.padding(pad).fillMaxSize().pointerInput(continuous, book, chapter) {
                if (!continuous) detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onDragEnd = { if (dragX < -180f) next() else if (dragX > 180f) prev(); dragX = 0f },
                    onDragCancel = { dragX = 0f },
                    onHorizontalDrag = { _, dx -> dragX += dx }
                )
            }, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
            if (continuous) item {
                val p = prevOf(blocks.first().book, blocks.first().chapter)
                if (p != null) TextButton(onClick = { go(p.first, p.second) }, modifier = Modifier.fillMaxWidth()) { Text("↑ ${c.content.book(p.first).name} ${p.second}") }
            }
            items(items, key = { when (it) {
                is RItem.Title -> "t${it.book}.${it.chapter}"
                is RItem.Head -> "h${it.book}.${it.chapter}.${it.h.beforeVerse}.${it.idx}"
                is RItem.V -> "v${it.book}.${it.chapter}.${it.v.verse}"
                is RItem.End -> "e${it.book}.${it.chapter}"
            } }) { it ->
                when (it) {
                    is RItem.Title -> {
                        Column {
                            Text("${c.content.book(it.book).name} ${it.chapter}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), textAlign = TextAlign.Center)
                            if (it.chapter == 1) TextButton(onClick = { nav.navigate("intro/${it.book}") }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Info, null); Spacer(Modifier.width(6.dp)); Text("Introducción a ${c.content.book(it.book).name} (MacArthur)")
                            }
                        }
                    }
                    is RItem.Head -> Text(it.h.text,
                        style = if (it.h.kind == "label") MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif) else MaterialTheme.typography.titleSmall,
                        color = if (it.h.kind == "label") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    is RItem.V -> {
                        val k = key(it.book, it.chapter, it.v.verse)
                        val blk = blocks.firstOrNull { b -> b.book == it.book && b.chapter == it.chapter }
                        val vk = VKey(it.book, it.chapter, it.v.verse)
                        VerseRow(
                            v = it.v, settings = settings, parallel = blk?.parallel?.get(it.v.verse),
                            highlight = hl[k], words = wordMap[k].orEmpty(), selected = vk in selection,
                            speaking = isSpeaking && it.book == book && it.chapter == chapter && speakingIdx - 1 == (speakBlock?.verses?.indexOf(it.v) ?: -2),
                            targeted = target?.let { t -> t.first.book == it.book && t.first.chapter == it.chapter && it.v.verse in t.first.verse..t.second } == true,
                            hasStudyNote = settings.showNoteMarkers && blk != null && it.v.verse in blk.noteVerses,
                            hasNote = noteMap.containsKey(k), bookmarked = k in bmSet, tagged = k in taggedSet,
                            onClick = {
                                selection = when {
                                    vk in selection -> selection - vk
                                    selection.any { s -> s.book != it.book || s.chapter != it.chapter } -> setOf(vk)
                                    else -> selection + vk
                                }
                            },
                            onStudyNote = { selection = setOf(vk); sheet = "notes" }
                        )
                    }
                    is RItem.End -> {
                        val read = (it.book * 1000 + it.chapter) in readSet
                        Column(Modifier.padding(top = 16.dp, bottom = if (continuous) 8.dp else 0.dp)) {
                            if (read) OutlinedButton(onClick = { scope.launch { c.user.markChapterRead(it.book, it.chapter, false) } }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(if (continuous) "${c.content.book(it.book).name} ${it.chapter} leído" else "Capítulo leído")
                            } else Button(onClick = { scope.launch { c.user.markChapterRead(it.book, it.chapter, true); Toast.makeText(ctx, "+10 puntos", Toast.LENGTH_SHORT).show() } }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Done, null); Spacer(Modifier.width(8.dp)); Text(if (continuous) "Marcar ${c.content.book(it.book).name} ${it.chapter} como leído" else "Marcar como leído")
                            }
                            if (!continuous) {
                                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                    OutlinedButton(onClick = { prev() }, enabled = prevOf(it.book, it.chapter) != null) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Anterior") }
                                    Spacer(Modifier.weight(1f))
                                    OutlinedButton(onClick = { next() }, enabled = nextOf(it.book, it.chapter) != null) { Text("Siguiente "); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                                }
                                val copyright = c.content.versions.firstOrNull { v -> v.id == settings.version }?.copyright.orEmpty()
                                Text(copyright, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 16.dp))
                            } else HorizontalDivider(Modifier.padding(top = 16.dp))
                        }
                    }
                }
            }
            if (continuous && loadingMore) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
        }
    }

    if (showPicker) BookChapterPicker(c.content.books, book, onDismiss = { showPicker = false }, pickVerse = true) { b, ch, v ->
        showPicker = false; go(b, ch, v)
    }
    val first = selVerses.firstOrNull()
    when (sheet) {
        "notes" -> if (first != null) StudyNotesSheet(selBook, selChapter, first, onDismiss = { sheet = null }, onRef = { previewRef = it },
            onOpenCommentary = { sheet = null; nav.navigate("commentary/$selBook/$selChapter") })
        "note" -> if (first != null) NoteDialog(selBook, selChapter, first, noteMap[key(selBook, selChapter, first)]?.firstOrNull(), onDismiss = { sheet = null; selection = emptySet() })
        "topic" -> if (first != null) TopicPickerDialog(selBook, selChapter, selVerses, onDismiss = { sheet = null; selection = emptySet() })
        "tag" -> if (first != null) TagPickerDialog(selBook, selChapter, selVerses, onDismiss = { sheet = null; selection = emptySet() })
        "words" -> if (first != null) selBlock?.verses?.firstOrNull { it.verse == first }?.let { v ->
            WordHighlightDialog(settings.version, v, wordMap[key(selBook, selChapter, first)].orEmpty(),
                onDismiss = { sheet = null; selection = emptySet() },
                onDictionary = { w -> sheet = null; selection = emptySet(); nav.navigate("dictionary?q=${android.net.Uri.encode(w)}") })
        }
    }
    previewRef?.let { r -> RefPreviewDialog(r, onDismiss = { previewRef = null }, onOpen = { sheet = null; nav.openRef(it) }) }
}

/** Separa el texto en palabras conservando la puntuación pegada a cada una. */
fun splitWords(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

@Composable
private fun VerseRow(
    v: Verse, settings: AppSettings, parallel: String?, highlight: Int?, words: List<com.biblia.estudio.data.user.WordHighlight>,
    selected: Boolean, targeted: Boolean, speaking: Boolean,
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
    val onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        Modifier.fillMaxWidth().padding(top = if (v.para) 6.dp else 0.dp).clip(RoundedCornerShape(6.dp)).background(bg)
            .then(if (selected) Modifier.border(2.dp, primary, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(buildAnnotatedString {
            if (settings.showVerseNumbers) withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold, fontSize = (settings.fontSize * 0.65f).sp, baselineShift = BaselineShift(0.3f))) { append(v.label + " ") }
            if (words.isEmpty()) append(v.text)
            else splitWords(v.text).forEachIndexed { i, w ->
                if (i > 0) append(" ")
                val wh = words.firstOrNull { i in it.wordStart..it.wordEnd }
                if (wh != null) withStyle(SpanStyle(background = Color(wh.color).copy(alpha = 0.75f), color = if (onDark) Color.Black else Color.Unspecified)) { append(w) } else append(w)
            }
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

/** Diálogo para resaltar palabras sueltas: se tocan las palabras y se elige un color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordHighlightDialog(version: String, v: Verse, existing: List<com.biblia.estudio.data.user.WordHighlight>, onDismiss: () -> Unit, onDictionary: (String) -> Unit) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    val words = remember(v) { splitWords(v.text) }
    // color por índice de palabra
    var colors by remember(existing) { mutableStateOf(buildMap { existing.forEach { e -> (e.wordStart..e.wordEnd).forEach { put(it, e.color) } } }) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resaltar palabras · ${c.content.book(v.book).name} ${v.chapter}:${v.verse}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Toca las palabras que quieres marcar y elige un color.", style = MaterialTheme.typography.bodySmall)
                FlowRow(Modifier.padding(vertical = 8.dp)) {
                    words.forEachIndexed { i, w ->
                        val col = colors[i]
                        FilterChip(
                            selected = i in picked, onClick = { picked = if (i in picked) picked - i else picked + i },
                            label = { Text(w, fontFamily = Serif) }, modifier = Modifier.padding(end = 4.dp),
                            colors = if (col != null && i !in picked) FilterChipDefaults.filterChipColors(containerColor = Color(col).copy(alpha = 0.8f), labelColor = Color.Black) else FilterChipDefaults.filterChipColors()
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HighlightColors.forEach { col ->
                        Box(Modifier.padding(3.dp).size(30.dp).clip(CircleShape).background(Color(col)).clickable(enabled = picked.isNotEmpty()) {
                            colors = colors + picked.associateWith { col }; picked = emptySet()
                        })
                    }
                    IconButton(onClick = { colors = colors - picked; picked = emptySet() }, enabled = picked.isNotEmpty()) { Icon(Icons.Filled.FormatColorReset, "Quitar") }
                }
                if (picked.size == 1 || (picked.isNotEmpty() && picked.max() - picked.min() == picked.size - 1)) {
                    val term = picked.sorted().joinToString(" ") { words[it] }.trim { !it.isLetter() }
                    TextButton(onClick = { onDictionary(term) }) { Icon(Icons.Filled.Translate, null); Text("  Buscar «$term» en el diccionario") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    // agrupar índices consecutivos del mismo color en rangos
                    val ranges = mutableListOf<Triple<Int, Int, Int>>()
                    colors.toSortedMap().forEach { (i, col) ->
                        val last = ranges.lastOrNull()
                        if (last != null && last.second == i - 1 && last.third == col) ranges[ranges.lastIndex] = Triple(last.first, i, col)
                        else ranges.add(Triple(i, i, col))
                    }
                    c.user.setWordHighlights(version, v.book, v.chapter, v.verse, ranges)
                    onDismiss()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
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
                A(Icons.Filled.FormatColorText, "Palabras", "words")
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
