package com.biblia.estudio.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Repetición de actividades de la agenda.
 * Reglas: "" (una vez), "daily", "weekly", "biweekly", "monthly" (mismo día del mes)
 * y "monthly_nth" (mismo día de la semana en la misma semana del mes: «primer lunes», «último viernes»).
 */
object Recurrence {
    val RULES = listOf("", "daily", "weekly", "biweekly", "monthly", "monthly_nth")
    private val es = Locale("es")

    /** Semana del mes de una fecha: 1..4, o -1 si es la quinta (días 29–31), que se trata como «la última». */
    internal fun nth(d: LocalDate): Int { val n = (d.dayOfMonth - 1) / 7 + 1; return if (n >= 5) -1 else n }

    private fun nthOf(month: LocalDate, n: Int, dow: DayOfWeek): LocalDate =
        if (n == -1) month.with(TemporalAdjusters.lastInMonth(dow)) else month.with(TemporalAdjusters.dayOfWeekInMonth(n, dow))

    private val ORD = mapOf(1 to "primer", 2 to "segundo", 3 to "tercer", 4 to "cuarto", -1 to "último")

    /** Texto para la interfaz, p. ej. «Cada mes, el primer lunes». */
    fun label(rule: String, startMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val dow = d.dayOfWeek.getDisplayName(TextStyle.FULL, es)
        return when (rule) {
            "daily" -> "Cada día"
            "weekly" -> "Cada semana ($dow)"
            "biweekly" -> "Cada 2 semanas ($dow)"
            "monthly" -> "Cada mes (día ${d.dayOfMonth})"
            "monthly_nth" -> "Cada mes (el ${ORD[nth(d)]} $dow)"
            else -> "No se repite"
        }
    }

    /** k-ésima repetición (k = 0 es la fecha original). */
    private fun occurrence(start: LocalDateTime, rule: String, k: Long): LocalDateTime = when (rule) {
        "daily" -> start.plusDays(k)
        "weekly" -> start.plusWeeks(k)
        "biweekly" -> start.plusWeeks(2 * k)
        "monthly" -> start.plusMonths(k)   // 31 → último día de los meses cortos
        "monthly_nth" -> {
            val d = start.toLocalDate()
            nthOf(d.plusMonths(k).withDayOfMonth(1), nth(d), d.dayOfWeek).atTime(start.toLocalTime())
        }
        else -> start
    }

    /**
     * Fechas (millis) de la actividad entre [from] y [to] (incluidos), como máximo [max].
     * [until] = último día en que se repite (0 = sin fin).
     */
    fun occurrences(startMillis: Long, rule: String, until: Long, from: Long, to: Long, max: Int = 60, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDateTime()
        if (rule.isBlank()) return if (startMillis in from..to) listOf(startMillis) else emptyList()
        val untilEnd = if (until > 0) Instant.ofEpochMilli(until).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1 else Long.MAX_VALUE
        val last = minOf(to, untilEnd)
        // Saltar rápido hasta cerca de «from»
        val approxStep = when (rule) { "daily" -> 1L; "weekly" -> 7L; "biweekly" -> 14L; else -> 28L } * 86_400_000L
        var k = if (from > startMillis) ((from - startMillis) / approxStep - 2).coerceAtLeast(0) else 0
        val out = ArrayList<Long>()
        while (out.size < max) {
            val t = occurrence(start, rule, k).atZone(zone).toInstant().toEpochMilli()
            if (t > last) break
            if (t >= from) out.add(t)
            k++
        }
        return out
    }

    /** Próxima fecha de la actividad a partir de [from], o null si ya no se repite. */
    fun next(startMillis: Long, rule: String, until: Long, from: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
        occurrences(startMillis, rule, until, from, Long.MAX_VALUE / 4, 1, zone).firstOrNull()

    /** Regla RRULE (RFC 5545) para el calendario del teléfono. */
    fun rrule(rule: String, startMillis: Long, until: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
        val d = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val day = d.dayOfWeek.name.take(2)
        val base = when (rule) {
            "daily" -> "FREQ=DAILY"
            "weekly" -> "FREQ=WEEKLY"
            "biweekly" -> "FREQ=WEEKLY;INTERVAL=2"
            "monthly" -> "FREQ=MONTHLY"
            "monthly_nth" -> "FREQ=MONTHLY;BYDAY=${nth(d)}$day"
            else -> return null
        }
        if (until <= 0) return base
        val u = Instant.ofEpochMilli(until).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().atZone(ZoneId.of("UTC"))
        return base + ";UNTIL=%04d%02d%02dT%02d%02d%02dZ".format(u.year, u.monthValue, u.dayOfMonth, u.hour, u.minute, u.second)
    }
}
