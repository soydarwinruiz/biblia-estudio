package com.biblia.estudio

import com.biblia.estudio.data.user.MemoryVerse
import com.biblia.estudio.data.user.Sm2
import com.biblia.estudio.domain.Gamification
import com.biblia.estudio.data.user.ActivityDay
import com.biblia.estudio.work.Notifications
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/** Pruebas de lógica pura (sin Android). */
class Sm2Test {
    private val base = MemoryVerse(book = 43, chapter = 3, verse = 16, verseEnd = 16)

    @Test fun intervalsGrowWhenRemembered() {
        var m = base; val days = mutableListOf<Int>()
        repeat(6) { m = Sm2.review(m, Sm2.GOOD, 0); days += m.interval }
        assertEquals(listOf(1, 3), days.take(2))
        assertTrue("los intervalos deben crecer: $days", days.zipWithNext().all { (a, b) -> b > a })
        assertTrue(m.box >= 5)
    }

    @Test fun forgettingResetsIntervalAndLowersEase() {
        var m = base
        repeat(4) { m = Sm2.review(m, Sm2.GOOD, 0) }
        val ease = m.ease
        m = Sm2.review(m, Sm2.AGAIN, 100)
        assertEquals(1, m.interval)
        assertEquals(101, m.nextReview)
        assertEquals(1, m.lapses)
        assertTrue(m.ease < ease)
        assertTrue(m.ease >= 1.3)
    }

    @Test fun easyIsLongerThanGoodIsLongerThanHard() {
        var m = base
        repeat(3) { m = Sm2.review(m, Sm2.GOOD, 0) }
        val (again, hard, good, easy) = Sm2.preview(m, 0)
        assertTrue("$again $hard $good $easy", again < hard && hard < good && good < easy)
    }

    @Test fun legacyLeitnerBoxIsRespected() {
        // Un versículo de la versión 1.4 en la caja 4 (15 días) no vuelve a empezar desde cero
        val old = base.copy(box = 4, interval = 0)
        assertTrue(Sm2.review(old, Sm2.GOOD, 0).interval > 15)
    }

    @Test fun typedComparisonIgnoresAccentsAndPunctuation() {
        val (score, words) = Sm2.compareTyped("Jehová es mi pastor; nada me faltará.", "jehova es mi pastor nada me faltara")
        assertEquals(1f, score)
        assertTrue(words.all { it.second })
        val (partial, w2) = Sm2.compareTyped("Jehová es mi pastor; nada me faltará.", "Jehová es pastor nada faltará")
        assertEquals(5f / 7f, partial, 0.001f)
        assertFalse(w2[2].second) // «mi» omitida
        assertEquals(Sm2.HARD, Sm2.gradeFor(partial))
        assertEquals(Sm2.EASY, Sm2.gradeFor(1f))
    }

    @Test fun reminderIsNextOccurrence() {
        val now = LocalDateTime.of(2026, 11, 25, 8, 30)
        assertEquals(LocalDateTime.of(2026, 11, 26, 7, 0), Notifications.nextTrigger(now, 7, 0))
        assertEquals(LocalDateTime.of(2026, 11, 25, 21, 15), Notifications.nextTrigger(now, 21, 15))
        assertEquals(LocalDateTime.of(2026, 11, 26, 8, 30), Notifications.nextTrigger(now, 8, 30))
    }

    @Test fun streaks() {
        val act = listOf(10L, 11L, 12L, 14L, 15L).map { ActivityDay(it, 5) }
        assertEquals(2, Gamification.streak(act, 15))
        assertEquals(2, Gamification.streak(act, 16)) // hoy aún sin actividad: cuenta hasta ayer
        assertEquals(0, Gamification.streak(act, 17))
        assertEquals(3, Gamification.bestStreak(act))
        assertEquals("Discípulo", Gamification.level(300).name)
    }

    @Test fun greetingByTimeOfDay() {
        val g = com.biblia.estudio.domain.Greeting
        assertEquals("Buenos días, Ana", g.forTime(java.time.LocalTime.of(7, 30), "Ana"))
        assertEquals("Buenas tardes, Ana", g.forTime(java.time.LocalTime.of(12, 0), "Ana"))
        assertEquals("Buenas tardes", g.forTime(java.time.LocalTime.of(18, 59), "  "))
        assertEquals("Buenas noches, Ana", g.forTime(java.time.LocalTime.of(19, 0), "Ana"))
        assertEquals("Buenas noches, Ana", g.forTime(java.time.LocalTime.of(2, 0), "Ana"))
    }
}
