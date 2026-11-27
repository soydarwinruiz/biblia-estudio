package com.biblia.estudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.biblia.estudio.app
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.ui.theme.Serif

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
        actions = { actions() }
    )
}

/** Barra superior de una sección principal (sin flecha de volver). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTopBar(title: String, actions: @Composable () -> Unit = {}) {
    TopAppBar(title = { Text(title, maxLines = 1) }, actions = { actions() })
}

@Composable
fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ElevatedCard(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** Construye texto con referencias bíblicas tocables. */
@Composable
fun linkedText(text: String, ctxBook: Int, ctxChapter: Int, onRef: (PassageRef) -> Unit): AnnotatedString {
    val parser = LocalContext.current.app.refParser
    val color = MaterialTheme.colorScheme.primary
    return remember(text, ctxBook, ctxChapter, parser) {
        val spans = parser?.parse(text, ctxBook, ctxChapter).orEmpty()
        buildAnnotatedString {
            var pos = 0
            for (s in spans) {
                if (s.start < pos) continue
                append(text.substring(pos, s.start))
                withLink(LinkAnnotation.Clickable("ref", TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))) { onRef(s.ref) }) {
                    append(text.substring(s.start, s.end))
                }
                pos = s.end
            }
            append(text.substring(pos))
        }
    }
}

@Composable
fun LinkedText(text: String, ctxBook: Int = 0, ctxChapter: Int = 0, style: TextStyle = MaterialTheme.typography.bodyLarge, modifier: Modifier = Modifier, onRef: (PassageRef) -> Unit) {
    Text(linkedText(text, ctxBook, ctxChapter, onRef), style = style, modifier = modifier)
}

/**
 * Muestra texto largo con un formato mínimo: párrafos separados por línea en blanco,
 * "## " como subtítulo y "- " como viñeta. Las referencias son tocables.
 */
@Composable
fun RichArticle(text: String, ctxBook: Int = 0, onRef: (PassageRef) -> Unit, fontSize: Float = 17f) {
    val body = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, fontSize = androidx.compose.ui.unit.TextUnit(fontSize, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(fontSize * 1.5f, androidx.compose.ui.unit.TextUnitType.Sp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val blocks = text.split(Regex("\n\\s*\n|\n(?=## |- |\\d+\\. )"))
        for (raw in blocks) {
            val b = raw.trim(); if (b.isEmpty()) continue
            when {
                b.startsWith("## ") -> Text(b.removePrefix("## "), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                b.startsWith("- ") -> Row {
                    Text("•  ", style = body)
                    LinkedText(b.removePrefix("- "), ctxBook, 0, body, onRef = onRef)
                }
                else -> LinkedText(b, ctxBook, 0, body, onRef = onRef)
            }
        }
    }
}

/** Diálogo con vista previa de una referencia bíblica. */
@Composable
fun RefPreviewDialog(ref: PassageRef, onDismiss: () -> Unit, onOpen: (PassageRef) -> Unit) {
    val c = LocalContext.current.app
    var text by remember(ref) { mutableStateOf<String?>(null) }
    LaunchedEffect(ref) {
        val v = c.settings.current().version
        val r = if (ref.verse == 0) ref.copy(verse = 1, verseEnd = if (ref.chapterEnd == ref.chapter) 999 else 0) else ref
        val verses = c.content.passage(v, r)
        text = if (verses.isEmpty()) "No se encontró el pasaje." else verses.take(40).joinToString(" ") { "${it.label} ${it.text}" } + if (verses.size > 40) " …" else ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.content.label(ref)) },
        text = {
            Box(Modifier.height(280.dp)) {
                if (text == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else Text(text!!, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif), modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onOpen(ref) }) { Text("Abrir en la Biblia") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
fun StatPill(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    // Cada dato en una sola línea: si no cabe (letra grande del teléfono), la letra se reduce un poco
    Column(modifier.padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FitText("$icon $value", MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        FitText(label, MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Texto de una sola línea que reduce su tamaño (hasta un 65 %) si no cabe en el ancho disponible,
 * en lugar de partirse o cortarse. Útil con la letra del sistema grande y pantallas estrechas.
 */
@Composable
fun FitText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier,
            color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
            textAlign: androidx.compose.ui.text.style.TextAlign? = null) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val full = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        val max = constraints.maxWidth
        val scale = if (max == androidx.compose.ui.unit.Constraints.Infinity || full <= max || full == 0) 1f else (max.toFloat() / full).coerceAtLeast(0.65f)
        val st = if (scale < 1f) style.copy(fontSize = style.fontSize * scale, letterSpacing = style.letterSpacing * scale) else style
        Text(text, style = st, color = color, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = textAlign)
    }
}
