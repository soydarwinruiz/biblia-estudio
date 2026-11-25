package com.biblia.estudio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.data.user.JournalEntry
import com.biblia.estudio.ui.components.BackTopBar
import com.biblia.estudio.ui.components.EmptyState
import com.biblia.estudio.ui.openRef
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale("es"))
private fun fmt(t: Long) = dateFmt.format(Date(t))

/** Diario espiritual: peticiones de oración (con «respondida») y entradas de diario. */
@Composable
fun JournalScreen(nav: NavController) {
    val c = LocalContext.current.app
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val kind = if (tab == 0) "prayer" else "journal"
    val entries by remember(kind) { c.user.dao.journal(kind) }.collectAsState(initial = null)
    val answered by c.user.dao.answeredCount().collectAsState(initial = 0)
    Scaffold(
        topBar = { BackTopBar("Diario", { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate("journal/0?kind=$kind") }, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(if (tab == 0) "Nueva petición" else "Nueva entrada") })
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Oración") }, icon = { Icon(Icons.Filled.VolunteerActivism, null) })
                Tab(tab == 1, { tab = 1 }, text = { Text("Diario") }, icon = { Icon(Icons.Filled.EditNote, null) })
            }
            val list = entries
            if (list == null) return@Column
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (tab == 0 && answered > 0) item {
                    Text("🙌 $answered oración${if (answered > 1) "es" else ""} respondida${if (answered > 1) "s" else ""}", Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (list.isEmpty()) item {
                    if (tab == 0) EmptyState(Icons.Filled.VolunteerActivism, "Sin peticiones", "Anota por qué oras y marca cuándo Dios responde. También puedes orar desde un versículo en el lector (botón «Orar»).")
                    else EmptyState(Icons.Filled.EditNote, "Diario vacío", "Escribe lo que Dios te habla en tu lectura, tus luchas y tus gratitudes.")
                }
                items(list, key = { it.id }) { e -> JournalRow(e, onClick = { nav.navigate("journal/${e.id}?kind=${e.kind}") }) }
            }
        }
    }
}

@Composable
private fun JournalRow(e: JournalEntry, onClick: () -> Unit) {
    val c = LocalContext.current.app
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = if (e.kind == "prayer") ({
            Icon(if (e.answered) Icons.Filled.CheckCircle else Icons.Filled.VolunteerActivism, null,
                tint = if (e.answered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }) else null,
        overlineContent = {
            Text(buildString {
                append(fmt(e.created))
                if (e.book > 0) append(" · " + c.content.label(PassageRef(e.book, e.chapter, e.verse)))
                if (e.answered) append(" · respondida el " + fmt(e.answeredAt))
            })
        },
        headlineContent = { Text(e.title.ifBlank { e.text.lineSequence().firstOrNull().orEmpty() }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { if (e.text.isNotBlank() && e.title.isNotBlank()) Text(e.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    )
}

@Composable
fun JournalEditScreen(nav: NavController, id: Long, kind: String, ref: PassageRef) {
    val c = LocalContext.current.app; val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf<JournalEntry?>(null) }
    LaunchedEffect(id) {
        entry = if (id > 0) c.user.dao.journalEntry(id) else JournalEntry(kind = kind, book = ref.book, chapter = ref.chapter, verse = ref.verse)
    }
    val e = entry
    var title by remember(e?.id) { mutableStateOf(e?.title ?: "") }
    var text by remember(e?.id) { mutableStateOf(e?.text ?: "") }
    var answered by remember(e?.id) { mutableStateOf(e?.answered ?: false) }
    var answerNote by remember(e?.id) { mutableStateOf(e?.answerNote ?: "") }
    val isPrayer = (e?.kind ?: kind) == "prayer"
    val verseText by produceState("", e?.book, e?.chapter, e?.verse) {
        val x = e; value = if (x != null && x.book > 0 && x.verse > 0) c.content.verseText(c.settings.current().version, x.book, x.chapter, x.verse) else ""
    }
    fun save() = scope.launch {
        val base = e ?: return@launch
        if (title.isBlank() && text.isBlank()) { nav.popBackStack(); return@launch }
        val now = System.currentTimeMillis()
        c.user.dao.upsertJournal(base.copy(title = title.trim(), text = text.trim(), answered = answered,
            answeredAt = if (answered) (base.answeredAt.takeIf { it > 0 } ?: now) else 0, answerNote = answerNote.trim(), updated = now))
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { nav.popBackStack() }
    }
    androidx.activity.compose.BackHandler { save() }
    Scaffold(topBar = {
        BackTopBar(if (isPrayer) "Petición de oración" else "Entrada de diario", { save() }) {
            if (id > 0) IconButton(onClick = { scope.launch { c.user.dao.deleteJournal(id); nav.popBackStack() } }) { Icon(Icons.Filled.Delete, "Eliminar") }
            TextButton(onClick = { save() }) { Text("Guardar") }
        }
    }) { pad ->
        if (e == null) return@Scaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (e.book > 0) {
                val r = PassageRef(e.book, e.chapter, e.verse)
                AssistChip(onClick = { nav.openRef(r) }, label = { Text(c.content.label(r)) })
                if (verseText.isNotBlank()) Text("«$verseText»", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = com.biblia.estudio.ui.theme.Serif),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (isPrayer) "Por qué o por quién oras" else "Título (opcional)") })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 200.dp),
                label = { Text(if (isPrayer) "Tu oración" else "Lo que Dios te habló hoy") })
            if (isPrayer) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { answered = !answered }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(answered, { answered = it }); Text("Oración respondida")
                }
                if (answered) OutlinedTextField(answerNote, { answerNote = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp), label = { Text("¿Cómo respondió Dios?") })
            }
            Text("Creada el ${fmt(e.created)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
