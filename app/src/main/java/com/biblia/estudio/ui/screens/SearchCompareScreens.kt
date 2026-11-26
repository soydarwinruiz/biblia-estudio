package com.biblia.estudio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.CompareRow
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.data.content.SearchHit
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.ui.components.BackTopBar
import com.biblia.estudio.ui.components.EmptyState
import com.biblia.estudio.ui.openRef
import com.biblia.estudio.ui.theme.Serif
import kotlinx.coroutines.launch
import java.text.Normalizer

private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

/** Resultado de búsqueda en datos personales o notas de estudio. */
private data class OtherHit(val overline: String, val text: String, val ref: PassageRef?, val route: String?)

private val SCOPES = listOf("Biblia", "Notas MacArthur", "Mis notas y diario")

@Composable
fun SearchScreen(nav: NavController, settings: AppSettings, initialQuery: String = "") {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var version by rememberSaveable { mutableStateOf<String?>(settings.version) }
    var testament by rememberSaveable { mutableStateOf<String?>(null) }
    var exact by rememberSaveable { mutableStateOf(false) }
    var where by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<SearchHit>?>(null) }
    var others by remember { mutableStateOf<List<OtherHit>?>(null) }
    var refHit by remember { mutableStateOf<PassageRef?>(null) }
    val recent by c.settings.recentSearches.collectAsState(initial = emptyList())
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    suspend fun search() {
        if (query.isBlank()) { results = null; others = null; refHit = null; return }
        refHit = c.refParser?.parseQuery(query)
        when (where) {
            0 -> { results = c.content.search(query, version, testament, null, exact = exact); others = null }
            1 -> {
                results = null
                others = c.content.notesMentioning(query.trim(), 100).map { n -> OtherHit(c.content.label(n.ref), n.text, n.ref, null) }
            }
            else -> {
                results = null
                val q = query.trim()
                val notes = c.user.dao.searchNotes(q).map { n -> OtherHit("Nota · " + c.content.label(PassageRef(n.book, n.chapter, n.verse)), n.text, PassageRef(n.book, n.chapter, n.verse), null) }
                val studies = c.user.dao.searchStudies(q).map { st -> OtherHit("Estudio · " + c.content.label(PassageRef(st.book, st.chapter, st.verse, st.chapterEnd, st.verseEnd)), st.title, null, "studyedit/${st.id}") }
                val journal = c.user.dao.searchJournal(q).map { j -> OtherHit((if (j.kind == "prayer") "Oración" else "Diario"), j.title.ifBlank { j.text }, null, "journal/${j.id}?kind=${j.kind}") }
                others = notes + studies + journal
            }
        }
        c.settings.addRecentSearch(query)
    }
    // Cada búsqueda nueva cancela la anterior, para que no la sobrescriba un resultado viejo.
    var searching by remember { mutableStateOf(false) }
    fun run() {
        job?.cancel()
        job = scope.launch { searching = true; try { search() } finally { searching = false } }
    }
    LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) run() }
    Scaffold(topBar = { BackTopBar("Buscar", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                placeholder = { Text("Palabras o referencia (p. ej. «gracia», «Juan 3:16»)") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { run() }))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
                SCOPES.forEachIndexed { i, label -> FilterChip(where == i, { where = i; run() }, { Text(label) }, Modifier.padding(end = 6.dp)) }
            }
            if (where == 0) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp)) {
                FilterChip(exact, { exact = !exact; run() }, { Text("Frase exacta") }, Modifier.padding(end = 12.dp))
                FilterChip(testament == null, { testament = null; run() }, { Text("Toda la Biblia") }, Modifier.padding(end = 6.dp))
                FilterChip(testament == "AT", { testament = "AT"; run() }, { Text("AT") }, Modifier.padding(end = 6.dp))
                FilterChip(testament == "NT", { testament = "NT"; run() }, { Text("NT") }, Modifier.padding(end = 12.dp))
                FilterChip(version == null, { version = null; run() }, { Text("Todas las versiones") }, Modifier.padding(end = 6.dp))
                c.content.versions.forEach { v -> FilterChip(version == v.id, { version = v.id; run() }, { Text(v.id) }, Modifier.padding(end = 6.dp)) }
            }
            val r = results
            val o = others
            val words = query.trim().split(Regex("\\s+")).filter { it.length > 1 }.map { fold(it) }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
                if (r == null && o == null && recent.isNotEmpty() && !searching) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Búsquedas recientes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { c.settings.clearRecentSearches() } }) { Text("Borrar") }
                        }
                    }
                    items(recent) { q -> ListItem(headlineContent = { Text(q) }, leadingContent = { Icon(Icons.Filled.History, null) }, modifier = Modifier.clickable { query = q; run() }) }
                }
                refHit?.let { ref -> item {
                    ListItem(headlineContent = { Text("Ir a ${c.content.label(ref)}") }, leadingContent = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.clickable { nav.openRef(ref) })
                    HorizontalDivider()
                } }
                if (r != null) {
                    item { Text("${r.size}${if (r.size >= 300) "+" else ""} resultado${if (r.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp, 4.dp)) }
                    if (r.isEmpty() && refHit == null) item { EmptyState(Icons.Filled.Search, "Sin resultados", "Prueba con otras palabras. La búsqueda no distingue acentos.") }
                    items(r) { h ->
                        ListItem(
                            overlineContent = { Text("${c.content.book(h.book).name} ${h.chapter}:${h.verse} · ${h.version}") },
                            headlineContent = { Text(highlightWords(h.text, words), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif)) },
                            modifier = Modifier.clickable { nav.openRef(PassageRef(h.book, h.chapter, h.verse)) }
                        )
                    }
                }
                if (o != null) {
                    item { Text("${o.size} resultado${if (o.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp, 4.dp)) }
                    if (o.isEmpty()) item { EmptyState(Icons.Filled.Search, "Sin resultados", if (where == 1) "Ninguna nota de estudio menciona ese texto." else "No hay notas, estudios ni entradas de diario con ese texto.") }
                    items(o) { h ->
                        ListItem(
                            overlineContent = { Text(h.overline) },
                            headlineContent = { Text(highlightWords(snippet(h.text, words), words), style = MaterialTheme.typography.bodyMedium, maxLines = 4) },
                            modifier = Modifier.clickable { h.route?.let { nav.navigate(it) } ?: h.ref?.let { nav.openRef(it) } }
                        )
                    }
                }
            }
        }
    }
}

/** Recorta un texto largo alrededor de la primera coincidencia. */
private fun snippet(text: String, words: List<String>): String {
    if (text.length < 280 || words.isEmpty()) return text
    val f = fold(text)
    val i = words.mapNotNull { w -> f.indexOf(w).takeIf { it >= 0 } }.minOrNull() ?: return text.take(280) + "…"
    val start = (i - 100).coerceAtLeast(0)
    return (if (start > 0) "…" else "") + text.substring(start, (start + 280).coerceAtMost(text.length)) + if (start + 280 < text.length) "…" else ""
}

@Composable
private fun highlightWords(text: String, words: List<String>) = buildAnnotatedString {
    val bg = MaterialTheme.colorScheme.primaryContainer
    Regex("\\S+|\\s+").findAll(text).forEach { m ->
        val w = fold(m.value.trim(',', '.', ';', ':', '¿', '?', '¡', '!', '«', '»', '“', '”', '(', ')'))
        if (w.isNotBlank() && words.any { w.startsWith(it) }) withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = bg)) { append(m.value) }
        else append(m.value)
    }
}

@Composable
fun CompareScreen(nav: NavController, settings: AppSettings, book: Int, chapter: Int, verse: Int) {
    val ctx = LocalContext.current; val c = ctx.app
    var v by remember { mutableIntStateOf(verse) }
    val rows by produceState<List<CompareRow>?>(null, v) { value = c.content.compare(book, chapter, v) }
    val maxV by produceState(verse, book, chapter) { value = c.content.chapter("RVR1960", book, chapter).maxOfOrNull { it.verseEnd } ?: verse }
    Scaffold(topBar = { BackTopBar("Comparar · ${c.content.book(book).name} $chapter:$v", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = { if (v > 1) v-- }, enabled = v > 1) { Text("‹ v. ${v - 1}") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { if (v < maxV) v++ }, enabled = v < maxV) { Text("v. ${v + 1} ›") }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(rows.orEmpty()) { r ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row {
                                Text(r.version, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text("  " + r.versionName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.Share, "Compartir", Modifier.size(18.dp).clickable { shareText(ctx, "«${r.text}» — ${c.content.book(book).name} $chapter:$v (${r.version})") })
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(r.text, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrayerScreen(nav: NavController, settings: AppSettings, dayOffset: Int = 0) {
    val ctx = LocalContext.current; val c = ctx.app; val scope = rememberCoroutineScope()
    var offset by remember { mutableIntStateOf(dayOffset) }
    val speaking by c.speaker.speaking.collectAsState()
    DisposableEffect(Unit) { onDispose { if (c.speaker.speaking.value == "prayer") c.speaker.stop() } }
    val day = com.biblia.estudio.data.user.today()
    val prayer by produceState<com.biblia.estudio.data.content.Prayer?>(null, offset) { value = c.content.prayerOfDay(day + offset) }
    val scripture by produceState("", prayer, settings.version) { prayer?.let { value = c.content.passage(settings.version, it.ref).joinToString(" ") { v -> "${v.label} ${v.text}" } } }
    Scaffold(topBar = { BackTopBar(if (offset == 0) "Oración del día" else "Oración", { nav.popBackStack() }) }) { pad ->
        val p = prayer ?: return@Scaffold
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(p.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { c.speaker.toggle("prayer", listOf(p.title, scripture.replace(Regex("\\b\\d+ "), ""), p.text)) }) {
                    Icon(if (speaking == "prayer") Icons.Filled.StopCircle else Icons.Filled.VolumeUp, "Escuchar")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Ora con la Escritura · ${c.content.label(p.ref)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(scripture, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(p.text, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, fontSize = androidx.compose.ui.unit.TextUnit(settings.fontSize, androidx.compose.ui.unit.TextUnitType.Sp)))
            Spacer(Modifier.height(20.dp))
            Text("Guía A.C.T.S. para continuar orando", style = MaterialTheme.typography.titleSmall)
            Text("• Adoración: alaba a Dios por quién es.\n• Confesión: reconoce tus pecados.\n• Gratitud: da gracias por lo que ha hecho.\n• Súplica: presenta tus peticiones y las de otros.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Row {
                if (offset == 0) Button(onClick = { scope.launch { c.user.markDevotional("prayer"); nav.popBackStack() } }) { Text("Amén · Completar") }
                else Button(onClick = { nav.popBackStack() }) { Text("Amén") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { offset++ }) { Text("Otra oración") }
            }
        }
    }
}
