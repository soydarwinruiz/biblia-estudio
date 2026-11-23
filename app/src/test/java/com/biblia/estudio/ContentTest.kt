package com.biblia.estudio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biblia.estudio.data.content.PassageRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = BibliaApp::class)
class ContentTest {
    private val c get() = ApplicationProvider.getApplicationContext<BibliaApp>().container

    @Test fun contentQueries() = runBlocking {
        c.init()
        val content = c.content
        assertEquals(66, content.books.size)
        assertEquals(12, content.versions.size)
        val jn3 = content.chapter("RVR1960", 43, 3)
        assertTrue(jn3.first { it.verse == 16 }.text.startsWith("Porque de tal manera amó Dios"))
        val hits = content.search("corazon contrito", "RVR1960", null, null)
        assertTrue(hits.any { it.book == 19 && it.chapter == 51 && it.verse == 17 })
        val hitsNoAccent = content.search("Jehova pastor", "RVR1960", null, null)
        assertTrue(hitsNoAccent.any { it.book == 19 && it.chapter == 23 })
        assertTrue(content.notesForVerse(43, 3, 16).isNotEmpty())
        assertTrue(content.intro(1).any { it.section == "Autor y fecha" })
        assertEquals(12, content.compare(43, 3, 16).size)
        for (d in 0L..400L step 37) {
            content.verseOfDay(d); content.passageOfDay(d); content.prayerOfDay(d); content.lessonOfDay(d)
            assertEquals(5, content.dailyQuiz(d).size)
        }
        assertEquals(9, content.plans().size)
        assertEquals(365, content.planDays(1).size)
        assertTrue(content.indexTopics("FE").isNotEmpty())
        println("OK: ${content.notesForChapter(1, 1).size} notas en Génesis 1")
    }

    @Test fun refParser() = runBlocking {
        c.init()
        val p = c.refParser!!
        val spans = p.parse("Cp. Is. 40:28; 45:8, 12 y 1 Co. 4.6; vea Jn. 1:1–3 y el v. 5", 1, 1)
        val refs = spans.map { c.content.label(it.ref) }
        println(refs)
        assertTrue("Isaías 40:28" in refs)
        assertTrue("Isaías 45:8" in refs)
        assertTrue("Isaías 45:12" in refs)
        assertTrue("1 Corintios 4:6" in refs)
        assertTrue("Juan 1:1–3" in refs)
        assertTrue("Génesis 1:5" in refs)
        assertEquals(PassageRef(43, 3, 16, 3, 16), p.parseQuery("juan 3:16"))
        assertEquals(45, p.parseQuery("Romanos 8")!!.book)
        assertEquals(46, p.parseQuery("1 Co 13")!!.book)
    }

    @Test fun userDataAndBackup() = runBlocking {
        c.init()
        val u = c.user
        u.setHighlight(43, 3, 16, 0xFFFFF176.toInt())
        u.markChapterRead(43, 3, true)
        val topic = u.createTopic("Amor de Dios", "", 0)
        u.dao.addTopicVerse(com.biblia.estudio.data.user.TopicVerse(topicId = topic, book = 43, chapter = 3, verse = 16))
        u.saveQuiz(4, 5)
        val json = u.exportJson()
        assertTrue(json.contains("Amor de Dios"))
        u.importJson(json)
        assertEquals(1, u.dao.dumpHighlights().size)
        assertEquals(1, u.dao.dumpTopicVerses().size)
        assertTrue(u.dao.dumpActivity().sumOf { it.points } >= 10 + 40)
    }
}
