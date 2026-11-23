package com.biblia.estudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.data.user.UserTopic
import com.biblia.estudio.ui.components.BackTopBar
import com.biblia.estudio.ui.components.EmptyState
import com.biblia.estudio.ui.openRef
import com.biblia.estudio.ui.theme.HighlightColors
import com.biblia.estudio.ui.theme.HighlightNames
import com.biblia.estudio.ui.theme.Serif
import com.biblia.estudio.ui.theme.TopicColors
import kotlinx.coroutines.launch

/** Elemento de lista que muestra una referencia y el texto del versículo en la versión actual. */
@Composable
fun VerseItem(ref: PassageRef, version: String, extra: String? = null, color: Int? = null, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val c = LocalContext.current.app
    val text by produceState("", ref, version) { value = c.content.passageText(version, ref) }
    ListItem(
        overlineContent = { Text(c.content.label(ref)) },
        headlineContent = { Text(text, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif)) },
        supportingContent = extra?.takeIf { it.isNotBlank() }?.let { { Text(it, color = MaterialTheme.colorScheme.primary) } },
        leadingContent = color?.let { { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(it))) } },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private val MINE_TABS = listOf("Temas", "Etiquetas", "Resaltados", "Notas", "Marcadores")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MyTopicsScreen(nav: NavController, settings: AppSettings) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val topics by c.user.dao.topics().collectAsState(initial = emptyList())
    val tags by c.user.dao.tags().collectAsState(initial = emptyList())
    val highlights by c.user.dao.allHighlights().collectAsState(initial = emptyList())
    val notes by c.user.dao.allNotes().collectAsState(initial = emptyList())
    val bookmarks by c.user.dao.bookmarks().collectAsState(initial = emptyList())
    var editTopic by remember { mutableStateOf<UserTopic?>(null) }
    var newTag by remember { mutableStateOf(false) }
    var colorFilter by remember { mutableStateOf<Int?>(null) }
    val memoryCount by c.user.dao.memoryVerses().collectAsState(initial = emptyList())
    Scaffold(
        topBar = { TopAppBar(title = { Text("Mis temas") }, actions = {
            TextButton(onClick = { nav.navigate("memory") }) { Icon(Icons.Filled.Psychology, null); Text(" Memorizar (${memoryCount.size})") }
        }) },
        floatingActionButton = {
            when (tab) {
                0 -> ExtendedFloatingActionButton(onClick = { editTopic = UserTopic(name = "", color = TopicColors[topics.size % TopicColors.size]) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Tema") })
                1 -> ExtendedFloatingActionButton(onClick = { newTag = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Etiqueta") })
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) { MINE_TABS.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                when (tab) {
                    0 -> {
                        if (topics.isEmpty()) item { EmptyState(Icons.Filled.FolderSpecial, "Agrupa versículos por tema", "Crea temas como «Fe», «Oración» o «Promesas de Dios» y agrega versículos desde el lector.") }
                        items(topics) { t ->
                            ListItem(leadingContent = { Box(Modifier.size(18.dp).clip(CircleShape).background(Color(t.color))) },
                                headlineContent = { Text(t.name) }, supportingContent = { Text(t.description.ifBlank { "${t.count} versículo(s)" }, maxLines = 2) },
                                trailingContent = { Text("${t.count}") }, modifier = Modifier.clickable { nav.navigate("topic/${t.id}") })
                        }
                    }
                    1 -> {
                        if (tags.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.Label, "Sin etiquetas", "Etiqueta versículos (#promesa, #mandato, #consuelo) para encontrarlos rápido.") }
                        item {
                            FlowRow(Modifier.padding(16.dp)) {
                                tags.forEach { t -> AssistChip(onClick = { nav.navigate("tag/${t.id}") }, label = { Text("#${t.name} (${t.count})") }, modifier = Modifier.padding(end = 6.dp)) }
                            }
                        }
                    }
                    2 -> {
                        item {
                            Row(Modifier.padding(horizontal = 12.dp)) {
                                FilterChip(colorFilter == null, { colorFilter = null }, { Text("Todos") }, Modifier.padding(end = 4.dp))
                                HighlightColors.forEachIndexed { i, col ->
                                    FilterChip(colorFilter == col, { colorFilter = col }, { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(col))) }, Modifier.padding(end = 4.dp))
                                }
                            }
                        }
                        val list = highlights.filter { colorFilter == null || it.color == colorFilter }
                        if (list.isEmpty()) item { EmptyState(Icons.Filled.Brush, "Sin resaltados", "Toca un versículo en el lector y elige un color.") }
                        items(list) { h ->
                            val ref = PassageRef(h.book, h.chapter, h.verse)
                            VerseItem(ref, settings.version, HighlightNames.getOrNull(HighlightColors.indexOf(h.color)), h.color, { nav.openRef(ref) })
                        }
                    }
                    3 -> {
                        if (notes.isEmpty()) item { EmptyState(Icons.Filled.EditNote, "Sin notas", "Selecciona un versículo y toca «Nota».") }
                        items(notes) { n -> val ref = PassageRef(n.book, n.chapter, n.verse); VerseItem(ref, settings.version, n.text, null, { nav.openRef(ref) }) }
                    }
                    4 -> {
                        if (bookmarks.isEmpty()) item { EmptyState(Icons.Filled.Bookmark, "Sin marcadores", "Selecciona un versículo y toca «Marcador».") }
                        items(bookmarks) { b ->
                            val ref = PassageRef(b.book, b.chapter, b.verse)
                            VerseItem(ref, settings.version, null, null, { nav.openRef(ref) }) {
                                IconButton(onClick = { scope.launch { c.user.dao.deleteBookmark(b.book, b.chapter, b.verse) } }) { Icon(Icons.Filled.Delete, "Quitar") }
                            }
                        }
                    }
                }
            }
        }
    }
    editTopic?.let { t -> TopicEditDialog(t, onDismiss = { editTopic = null }) }
    if (newTag) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { newTag = false }, title = { Text("Nueva etiqueta") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("p. ej. promesa") }) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) scope.launch { c.user.createTag(name) }; newTag = false }) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { newTag = false }) { Text("Cancelar") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicEditDialog(t: UserTopic, onDismiss: () -> Unit) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(t.name) }
    var desc by remember { mutableStateOf(t.description) }
    var color by remember { mutableIntStateOf(t.color) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (t.id == 0L) "Nuevo tema" else "Editar tema") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nombre") })
                OutlinedTextField(desc, { desc = it }, label = { Text("Descripción (opcional)") }, modifier = Modifier.padding(top = 8.dp))
                FlowRow(Modifier.padding(top = 12.dp)) {
                    TopicColors.forEach { col ->
                        Box(Modifier.padding(4.dp).size(if (col == color) 34.dp else 28.dp).clip(CircleShape).background(Color(col)).clickable { color = col })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (name.isNotBlank()) scope.launch { if (t.id == 0L) c.user.createTopic(name, desc, color) else c.user.dao.upsertTopic(t.copy(name = name.trim(), description = desc.trim(), color = color)) }
            onDismiss()
        }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun TopicDetailScreen(nav: NavController, settings: AppSettings, id: Long) {
    val ctx = LocalContext.current; val c = ctx.app; val scope = rememberCoroutineScope()
    val topic by c.user.dao.topic(id).collectAsState(initial = null)
    val verses by c.user.dao.topicVerses(id).collectAsState(initial = emptyList())
    var edit by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        BackTopBar(topic?.name ?: "Tema", { nav.popBackStack() }) {
            IconButton(onClick = {
                scope.launch {
                    val txt = buildString {
                        appendLine(topic?.name ?: ""); topic?.description?.takeIf { it.isNotBlank() }?.let { appendLine(it) }; appendLine()
                        verses.forEach { v -> val r = PassageRef(v.book, v.chapter, v.verse, v.chapter, v.verseEnd); appendLine("${c.content.label(r)}: ${c.content.passageText(settings.version, r)}") }
                    }
                    shareText(ctx, txt)
                }
            }) { Icon(Icons.Filled.Share, "Compartir") }
            IconButton(onClick = { edit = true }) { Icon(Icons.Filled.Edit, "Editar") }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            topic?.description?.takeIf { it.isNotBlank() }?.let { d -> item { Text(d, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) } }
            if (verses.isEmpty()) item { EmptyState(Icons.Filled.FolderSpecial, "Tema vacío", "En el lector, selecciona versículos y toca «Tema».") }
            items(verses) { v ->
                val ref = PassageRef(v.book, v.chapter, v.verse, v.chapter, v.verseEnd)
                VerseItem(ref, settings.version, v.note, null, { nav.openRef(ref) }) {
                    IconButton(onClick = { scope.launch { c.user.dao.removeTopicVerse(id, v.book, v.chapter, v.verse) } }) { Icon(Icons.Filled.RemoveCircleOutline, "Quitar") }
                }
            }
        }
    }
    if (edit) topic?.let { TopicEditDialog(it) { edit = false } }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("¿Eliminar el tema?") }, text = { Text("Los versículos no se borran de la Biblia, solo se quita la agrupación.") },
        confirmButton = { TextButton(onClick = { scope.launch { c.user.deleteTopic(id); confirm = false; nav.popBackStack() } }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}

@Composable
fun TagDetailScreen(nav: NavController, settings: AppSettings, id: Long) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    val tag by c.user.dao.tag(id).collectAsState(initial = null)
    val verses by c.user.dao.tagVerses(id).collectAsState(initial = emptyList())
    Scaffold(topBar = {
        BackTopBar("#" + (tag?.name ?: ""), { nav.popBackStack() }) {
            IconButton(onClick = { scope.launch { c.user.deleteTag(id); nav.popBackStack() } }) { Icon(Icons.Filled.Delete, "Eliminar etiqueta") }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            items(verses) { v ->
                val ref = PassageRef(v.book, v.chapter, v.verse)
                VerseItem(ref, settings.version, null, null, { nav.openRef(ref) }) {
                    IconButton(onClick = { scope.launch { c.user.dao.removeTagVerse(id, v.book, v.chapter, v.verse) } }) { Icon(Icons.Filled.RemoveCircleOutline, "Quitar") }
                }
            }
        }
    }
}
