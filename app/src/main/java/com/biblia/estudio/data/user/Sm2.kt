package com.biblia.estudio.data.user

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Repaso espaciado al estilo SM-2 (SuperMemo / Anki) con cuatro respuestas.
 * Cada versículo guarda su facilidad (ease) y su intervalo; si se olvida, vuelve a 1 día
 * pero conserva parte del progreso en la facilidad.
 */
object Sm2 {
    const val AGAIN = 0; const val HARD = 1; const val GOOD = 2; const val EASY = 3
    private const val MIN_EASE = 1.3

    fun review(m: MemoryVerse, grade: Int, today: Long): MemoryVerse {
        // Versículos creados con el sistema anterior (cajas Leitner): se deduce un intervalo aproximado.
        val prev = if (m.interval > 0) m.interval else listOf(0, 1, 2, 4, 7, 15, 30, 60).getOrElse(m.box) { 60 }
        var ease = m.ease
        val interval: Int
        when (grade) {
            AGAIN -> { interval = 1; ease -= 0.2 }
            HARD -> { interval = max(1, (prev * 1.2).roundToInt()); ease -= 0.15 }
            GOOD -> interval = when (prev) { 0 -> 1; 1 -> 3; else -> max(prev + 1, (prev * ease).roundToInt()) }
            else -> { interval = when (prev) { 0 -> 3; 1 -> 5; else -> max(prev + 2, (prev * ease * 1.3).roundToInt()) }; ease += 0.15 }
        }
        ease = ease.coerceIn(MIN_EASE, 3.0)
        val capped = interval.coerceAtMost(365)
        return m.copy(
            ease = ease, interval = capped, nextReview = today + capped, reviews = m.reviews + 1,
            lapses = m.lapses + if (grade == AGAIN) 1 else 0,
            // «box» se mantiene como nivel visible (0..6) para la interfaz y los logros
            box = when { capped >= 60 -> 6; capped >= 30 -> 5; capped >= 15 -> 4; capped >= 7 -> 3; capped >= 4 -> 2; capped >= 2 -> 1; else -> 0 }
        )
    }

    /** Próximo intervalo que daría cada respuesta (para mostrarlo en los botones). */
    fun preview(m: MemoryVerse, today: Long): List<Int> = (AGAIN..EASY).map { review(m, it, today).interval }

    /** Compara lo escrito con el texto real. Devuelve la proporción de palabras correctas (0..1) y las palabras marcadas. */
    fun compareTyped(expected: String, typed: String): Pair<Float, List<Pair<String, Boolean>>> {
        fun norm(w: String) = java.text.Normalizer.normalize(w, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^\\p{L}\\d]"), "")
        val exp = expected.split(Regex("\\s+")).filter { it.isNotBlank() }
        val got = typed.split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }
        // Alineación por subsecuencia común más larga: tolera palabras omitidas o añadidas.
        val a = exp.map(::norm); val n = a.size; val k = got.size
        val dp = Array(n + 1) { IntArray(k + 1) }
        for (i in n - 1 downTo 0) for (j in k - 1 downTo 0)
            dp[i][j] = if (a[i] == got[j] && a[i].isNotEmpty()) dp[i + 1][j + 1] + 1 else max(dp[i + 1][j], dp[i][j + 1])
        val ok = BooleanArray(n)
        var i = 0; var j = 0
        while (i < n && j < k) {
            when {
                a[i] == got[j] && a[i].isNotEmpty() -> { ok[i] = true; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        val words = exp.mapIndexed { idx, w -> w to (ok[idx] || a[idx].isEmpty()) }
        val score = if (n == 0) 1f else words.count { it.second }.toFloat() / n
        return score to words
    }

    /** Respuesta sugerida según el porcentaje de palabras acertadas al escribir. */
    fun gradeFor(score: Float): Int = when { score >= 0.98f -> EASY; score >= 0.85f -> GOOD; score >= 0.6f -> HARD; else -> AGAIN }
}
