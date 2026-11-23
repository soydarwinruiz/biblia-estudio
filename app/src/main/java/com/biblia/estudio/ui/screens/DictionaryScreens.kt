package com.biblia.estudio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.DictEntry
import com.biblia.estudio.data.content.IndexTopic
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.data.content.StudyNote
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.ui.components.*
import com.biblia.estudio.ui.openRef
import com.biblia.estudio.ui.theme.Serif

@Composable
fun DictionaryScreen(nav: NavController, initialQuery: String) {
    val c = LocalContext.current.app
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categories by produceState(emptyList<String>()) { value = c.content.dictionaryCategories() }
    val list by produceState<List<DictEntry>?>(null, query, category) { value = c.content.dictionarySearch(query, category) }
    // Si se llega desde una palabra del lector y hay coincidencia exacta, abrir directamente
    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) c.content.dictionaryFind(initialQuery)?.let { nav.navigate("dict/${it.id}") { popUpTo("dictionary?q={q}") { inclusive = true } } }
    }
    Scaffold(topBar = { BackTopBar("Diccionario bíblico", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                placeholder = { Text("Buscar: Abraham, gracia, Jerusalén…") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
                FilterChip(category == null, { category = null }, { Text("Todo") }, Modifier.padding(end = 6.dp))
                categories.forEach { cat -> FilterChip(category == cat, { category = cat }, { Text(cat) }, Modifier.padding(end = 6.dp)) }
            }
            val l = list
            if (l == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Text("${l.size} entradas", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp)) }
                if (l.isEmpty()) item {
                    EmptyState(Icons.Filled.MenuBook, "Sin resultados", "Prueba otra palabra o búscala en el texto bíblico.")
                    TextButton(onClick = { nav.navigate("search?q=${android.net.Uri.encode(query)}") }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Buscar «$query» en la Biblia") }
                }
                items(l, key = { it.id }) { e ->
                    ListItem(headlineContent = { Text(e.term) }, supportingContent = { Text(e.category) }, modifier = Modifier.clickable { nav.navigate("dict/${e.id}") })
                }
            }
        }
    }
}

@Composable
fun DictionaryEntryScreen(nav: NavController, settings: AppSettings, id: Int) {
    val c = LocalContext.current.app
    val entry by produceState<DictEntry?>(null, id) { value = c.content.dictionaryEntry(id) }
    var preview by remember { mutableStateOf<PassageRef?>(null) }
    Scaffold(topBar = { BackTopBar("Diccionario", { nav.popBackStack() }) }) { pad ->
        val e = entry ?: return@Scaffold Loading()
        val searchTerm = e.term.substringBefore(" (").substringBefore(",")
        val count by produceState(-1, e) { value = c.content.countInBible(searchTerm, settings.version) }
        val topic by produceState<IndexTopic?>(null, e) { value = c.content.indexTopicFor(searchTerm) }
        val notes by produceState(emptyList<StudyNote>(), e) { value = c.content.notesMentioning(searchTerm, 12) }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(e.term, style = MaterialTheme.typography.headlineSmall)
            AssistChip(onClick = {}, label = { Text(e.category) })
            Spacer(Modifier.height(8.dp))
            RichArticle(e.definition, 0, { preview = it }, settings.fontSize - 1)
            Spacer(Modifier.height(16.dp))
            InfoCardColumn {
                Text("En la Biblia", style = MaterialTheme.typography.titleSmall)
                Text(if (count < 0) "Contando…" else "«$searchTerm» aparece en $count versículo${if (count == 1) "" else "s"} (${settings.version}).", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { nav.navigate("search?q=${android.net.Uri.encode(searchTerm)}") }) { Text("Ver todos los versículos") }
                topic?.let { t -> TextButton(onClick = { nav.navigate("index/${t.id}") }) { Text("Índice temático MacArthur: ${t.name.lowercase().replaceFirstChar { it.uppercase() }}") } }
            }
            if (notes.isNotEmpty()) {
                Text("Notas de MacArthur que lo mencionan", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                notes.forEach { n ->
                    ListItem(
                        overlineContent = { Text(c.content.label(n.ref)) },
                        headlineContent = { Text(n.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Serif)) },
                        modifier = Modifier.clickable { nav.navigate("commentary/${n.book}/${n.chapter}") }
                    )
                }
            }
            Text("Diccionario redactado para esta app. Contrasta siempre con el texto bíblico.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 16.dp))
        }
    }
    preview?.let { RefPreviewDialog(it, { preview = null }) { r -> nav.openRef(r) } }
}

@Composable
private fun InfoCardColumn(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Column(Modifier.padding(16.dp), content = content) }
}
