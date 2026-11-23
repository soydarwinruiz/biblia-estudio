package com.biblia.estudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.biblia.estudio.data.prefs.ThemeMode

private val Brown = Color(0xFF7A4A1E)
private val Gold = Color(0xFFB8860B)

private val Light = lightColorScheme(
    primary = Brown, onPrimary = Color.White, primaryContainer = Color(0xFFFFDCC2), onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF715A48), secondaryContainer = Color(0xFFFCDDC6), onSecondaryContainer = Color(0xFF29180A),
    tertiary = Color(0xFF5B6237), tertiaryContainer = Color(0xFFE0E7B1),
    background = Color(0xFFFFFBF5), surface = Color(0xFFFFFBF5), surfaceVariant = Color(0xFFF3E4D8),
    surfaceContainer = Color(0xFFF8EEE5), surfaceContainerHigh = Color(0xFFF3E8DF), surfaceContainerLow = Color(0xFFFCF3EC)
)
private val Sepia = lightColorScheme(
    primary = Color(0xFF6B4423), onPrimary = Color.White, primaryContainer = Color(0xFFEBD3B0), onPrimaryContainer = Color(0xFF2B1A0A),
    secondary = Color(0xFF7D6347), secondaryContainer = Color(0xFFE8D5B7),
    background = Color(0xFFF4ECD8), surface = Color(0xFFF4ECD8), onBackground = Color(0xFF3B2F25), onSurface = Color(0xFF3B2F25),
    surfaceVariant = Color(0xFFE9DCC2), surfaceContainer = Color(0xFFEFE4CC), surfaceContainerHigh = Color(0xFFEADFC6), surfaceContainerLow = Color(0xFFF1E8D2)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB77A), onPrimary = Color(0xFF4A2800), primaryContainer = Color(0xFF693C10), onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFDFC1AA), secondaryContainer = Color(0xFF574333), tertiary = Gold,
    background = Color(0xFF1A1411), surface = Color(0xFF1A1411), surfaceVariant = Color(0xFF52443A),
    surfaceContainer = Color(0xFF271F1A), surfaceContainerHigh = Color(0xFF322924), surfaceContainerLow = Color(0xFF221A16)
)

val Serif = FontFamily.Serif

@Composable
fun BibliaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; else -> false }
    val scheme = when { mode == ThemeMode.SEPIA -> Sepia; dark -> Dark; else -> Light }
    val base = Typography()
    val typo = base.copy(
        headlineSmall = base.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}

/** Colores de resaltado (ARGB). */
val HighlightColors = listOf(0xFFFFF176.toInt(), 0xFFA5D6A7.toInt(), 0xFF90CAF9.toInt(), 0xFFF8BBD0.toInt(), 0xFFFFCC80.toInt(), 0xFFCE93D8.toInt())
val HighlightNames = listOf("Amarillo", "Verde", "Azul", "Rosa", "Naranja", "Violeta")
val TopicColors = listOf(0xFF7A4A1E.toInt(), 0xFF2E7D32.toInt(), 0xFF1565C0.toInt(), 0xFFAD1457.toInt(), 0xFFEF6C00.toInt(), 0xFF6A1B9A.toInt(), 0xFF00838F.toInt(), 0xFF5D4037.toInt())

fun verseTextStyle(size: Float, lineHeight: Float) = TextStyle(fontFamily = Serif, fontSize = size.sp, lineHeight = (size * lineHeight).sp)
