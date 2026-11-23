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

@Composable
fun SearchScreen(nav: NavController, settings: AppSettings, initialQuery: String = "") {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var version by rememberSaveable { mutableStateOf<String?>(settings.version) }
    var testament by rememberSaveable { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SearchHit>?>(null) }
    var refHit by remember { mutableStateOf<PassageRef?>(null) }
    fun run() = scope.launch {
        refHit = c.refParser?.parseQuery(query)
        results = c.content.search(query, version, testament, null)
    }
    LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) run() }
    Scaffold(topBar = { BackTopBar("Buscar en la Biblia", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                placeholder = { Text("Palabras o referencia (p. ej. «gracia», «Juan 3:16»)") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { run() }))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
                FilterChip(testament == null, { testament = null; run() }, { Text("Toda la Biblia") }, Modifier.padding(end = 6.dp))
                FilterChip(testament == "AT", { testament = "AT"; run() }, { Text("AT") }, Modifier.padding(end = 6.dp))
                FilterChip(testament == "NT", { testament = "NT"; run() }, { Text("NT") }, Modifier.padding(end = 12.dp))
                FilterChip(version == null, { version = null; run() }, { Text("Todas las versiones") }, Modifier.padding(end = 6.dp))
                c.content.versions.forEach { v -> FilterChip(version == v.id, { version = v.id; run() }, { Text(v.id) }, Modifier.padding(end = 6.dp)) }
            }
            val r = results
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                refHit?.let { ref -> item {
                    ListItem(headlineContent = { Text("Ir a ${c.content.label(ref)}") }, leadingContent = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.clickable { nav.openRef(ref) })
                    HorizontalDivider()
                } }
                if (r != null) {
                    item { Text("${r.size}${if (r.size >= 300) "+" else ""} resultados", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp, 4.dp)) }
                    if (r.isEmpty() && refHit == null) item { EmptyState(Icons.Filled.Search, "Sin resultados", "Prueba con otras palabras. La búsqueda no distingue acentos.") }
                    val words = query.trim().split(Regex("\\s+")).filter { it.length > 1 }.map { fold(it) }
                    items(r) { h ->
                        val ann = buildAnnotatedString {
                            Regex("\\S+|\\s+").findAll(h.text).forEach { m ->
                                val w = fold(m.value.trim(',', '.', ';', ':', '¿', '?', '¡', '!', '«', '»', '“', '”'))
                                if (words.any { w.startsWith(it) }) withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.primaryContainer)) { append(m.value) }
                                else append(m.value)
                            }
                        }
                        ListItem(
                            overlineContent = { Text("${c.content.book(h.book).name} ${h.chapter}:${h.verse} · ${h.version}") },
                            headlineContent = { Text(ann, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif)) },
                            modifier = Modifier.clickable { nav.openRef(PassageRef(h.book, h.chapter, h.verse)) }
                        )
                    }
                }
            }
        }
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
