package com.biblia.estudio

import com.biblia.estudio.domain.Recurrence
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceTest {
    private val z = ZoneId.of("America/Bogota")
    private fun t(y: Int, m: Int, d: Int, h: Int = 19, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(z).toInstant().toEpochMilli()
    private fun dates(l: List<Long>) = l.map { java.time.Instant.ofEpochMilli(it).atZone(z).toLocalDate() }

    @Test fun weeklyAndBiweekly() {
        val start = t(2026, 11, 29)   // domingo
        val w = dates(Recurrence.occurrences(start, "weekly", 0, t(2026, 12, 1, 0), t(2026, 12, 31, 23), zone = z))
        assertEquals(listOf(6, 13, 20, 27), w.map { it.dayOfMonth })
        val b = dates(Recurrence.occurrences(start, "biweekly", 0, start, t(2027, 1, 10, 23), zone = z))
        assertEquals(listOf(LocalDate.of(2026, 11, 29), LocalDate.of(2026, 12, 13), LocalDate.of(2026, 12, 27), LocalDate.of(2027, 1, 10)), b)
    }

    @Test fun monthlyKeepsDayAndClampsShortMonths() {
        val start = t(2027, 1, 31)
        val m = dates(Recurrence.occurrences(start, "monthly", 0, start, t(2027, 4, 30, 23), zone = z))
        assertEquals(listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30)), m)
    }

    @Test fun firstMondayAndLastFriday() {
        val firstMonday = t(2026, 12, 7)   // primer lunes de diciembre 2026
        assertEquals("Cada mes (el primer lunes)", Recurrence.label("monthly_nth", firstMonday, z))
        val fm = dates(Recurrence.occurrences(firstMonday, "monthly_nth", 0, firstMonday, t(2027, 3, 31, 23), zone = z))
        assertEquals(listOf(LocalDate.of(2026, 12, 7), LocalDate.of(2027, 1, 4), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1)), fm)
        val lastFriday = t(2027, 1, 29)    // 29 = quinta semana → «último viernes»
        assertEquals("Cada mes (el último viernes)", Recurrence.label("monthly_nth", lastFriday, z))
        val lf = dates(Recurrence.occurrences(lastFriday, "monthly_nth", 0, lastFriday, t(2027, 3, 31, 23), zone = z))
        assertEquals(listOf(LocalDate.of(2027, 1, 29), LocalDate.of(2027, 2, 26), LocalDate.of(2027, 3, 26)), lf)
    }

    @Test fun untilAndNext() {
        val start = t(2026, 12, 1, 6, 30)
        val until = t(2026, 12, 3, 0, 0)
        assertEquals(3, Recurrence.occurrences(start, "daily", until, start, t(2027, 1, 1), zone = z).size) // 1, 2 y 3 de dic.
        assertEquals(t(2026, 12, 2, 6, 30), Recurrence.next(start, "daily", until, t(2026, 12, 1, 7), z))
        assertNull(Recurrence.next(start, "daily", until, t(2026, 12, 3, 7), z))
        assertNull(Recurrence.next(start, "", 0, start + 1, z))
        // Encuentra rápido una repetición lejana
        assertEquals(LocalDate.of(2036, 12, 3), dates(listOf(Recurrence.next(t(2026, 12, 2), "weekly", 0, t(2036, 12, 1), z)!!)).first())  // mismo día de la semana (miércoles)
    }

    @Test fun calendarRule() {
        assertEquals("FREQ=WEEKLY;INTERVAL=2", Recurrence.rrule("biweekly", t(2026, 11, 29), 0, z))
        assertEquals("FREQ=MONTHLY;BYDAY=1MO", Recurrence.rrule("monthly_nth", t(2026, 12, 7), 0, z))
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR", Recurrence.rrule("monthly_nth", t(2027, 1, 29), 0, z))
        assertEquals("FREQ=DAILY;UNTIL=20261204T050000Z", Recurrence.rrule("daily", t(2026, 12, 1), t(2026, 12, 3, 0), z))
        assertNull(Recurrence.rrule("", t(2026, 12, 1), 0, z))
    }
}
