package com.biblia.estudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biblia.estudio.app
import com.biblia.estudio.data.content.PassageRef
import com.biblia.estudio.data.content.Verse
import com.biblia.estudio.data.prefs.AppSettings
import com.biblia.estudio.ui.theme.verseTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Modo púlpito: texto muy grande sobre fondo oscuro, pantalla siempre encendida
 * y desplazamiento automático tipo teleprónter (toca el texto para pausar).
 */
@Composable
fun PulpitScreen(nav: NavController, settings: AppSettings, ref: PassageRef) {
    val c = LocalContext.current.app
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val verses by produceState<List<Verse>>(emptyList(), ref, settings.version) {
        val r = if (ref.verse == 0) ref.copy(verse = 1, verseEnd = 0) else ref
        value = c.content.passage(settings.version, r)
    }
    var size by remember { mutableFloatStateOf(34f) }
    var speed by remember { mutableFloatStateOf(0f) }   // píxeles por segundo; 0 = detenido
    var lastSpeed by remember { mutableFloatStateOf(40f) }
    val scroll = rememberScrollState()
    LaunchedEffect(speed) {
        if (speed <= 0f) return@LaunchedEffect
        while (isActive) {
            delay(16)
            if (scroll.value >= scroll.maxValue) { speed = 0f; break }
            scroll.scrollBy(speed * 0.016f)
        }
    }
    val bg = Color(0xFF111111); val fg = Color(0xFFF5EBDD); val accent = Color(0xFFFFB77A)
    // Tres franjas que no se solapan: controles (con la referencia), texto y velocidad.
    Column(Modifier.fillMaxSize().background(bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Salir", tint = fg) }
            Text(c.content.label(ref) + " · " + settings.version, color = accent, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { size = (size - 4f).coerceAtLeast(20f) }) { Icon(Icons.Filled.TextDecrease, "Letra más pequeña", tint = fg) }
            IconButton(onClick = { size = (size + 4f).coerceAtMost(72f) }) { Icon(Icons.Filled.TextIncrease, "Letra más grande", tint = fg) }
            IconButton(onClick = { if (speed > 0) { lastSpeed = speed; speed = 0f } else speed = lastSpeed }) {
                Icon(if (speed > 0) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (speed > 0) "Pausar" else "Desplazamiento automático", tint = accent)
            }
        }
        HorizontalDivider(color = fg.copy(alpha = 0.12f))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                .clickable(remember { MutableInteractionSource() }, null) { if (speed > 0) { lastSpeed = speed; speed = 0f } else speed = lastSpeed }
                .padding(horizontal = 28.dp).padding(top = 20.dp, bottom = 160.dp)
        ) {
            Text(buildAnnotatedString {
                verses.forEach { v ->
                    withStyle(SpanStyle(color = accent, fontSize = (size * 0.55f).sp, fontWeight = FontWeight.Bold)) { append(v.label + " ") }
                    append(v.text + " ")
                }
            }, color = fg, style = verseTextStyle(size, 1.45f, settings.readerFont))
        }
        HorizontalDivider(color = fg.copy(alpha = 0.12f))
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 6.dp)) {
            Text("Velocidad de desplazamiento · toca el texto para pausar", color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            Slider(if (speed > 0) speed else lastSpeed, { v -> lastSpeed = v; if (speed > 0) speed = v }, valueRange = 10f..150f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
        }
    }
}
