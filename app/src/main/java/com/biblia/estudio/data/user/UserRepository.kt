package com.biblia.estudio.data.user

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

object Points {
    const val CHAPTER = 10
    const val DEVOTIONAL = 5
    const val QUIZ_CORRECT = 10
    const val QUIZ_PERFECT_BONUS = 20
    const val PLAN_DAY = 20
    const val STUDY = 15
    const val MEMORY = 5
}

fun today(): Long = LocalDate.now().toEpochDay()

@Serializable
data class Backup(
    val app: String = "BibliaEstudio", val version: Int = 2, val created: Long = System.currentTimeMillis(),
    val highlights: List<Highlight>, val notes: List<VerseNote>, val bookmarks: List<Bookmark>,
    val topics: List<UserTopic>, val topicVerses: List<TopicVerse>, val tags: List<Tag>, val tagVerses: List<TagVerse>,
    val chaptersRead: List<ChapterRead>, val enrollments: List<PlanEnrollment>, val planDays: List<PlanDayDone>,
    val activity: List<ActivityDay>, val quizDays: List<QuizDay>, val devotional: List<DevotionalDone>, val studies: List<Study>,
    val reflections: List<LessonReflection> = emptyList(), val memory: List<MemoryVerse> = emptyList(),
    val wordHighlights: List<WordHighlight> = emptyList(),
    val journal: List<JournalEntry> = emptyList()
)

class UserRepository(private val db: UserDatabase) {
    val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun addPoints(points: Int, day: Long = today()) {
        val cur = dao.activityDay(day)
        dao.upsertActivity(ActivityDay(day, (cur?.points ?: 0) + points))
    }

    suspend fun setHighlight(book: Int, chapter: Int, verse: Int, color: Int?) {
        if (color == null) dao.deleteHighlight(book, chapter, verse)
        else {
            val id = dao.highlightId(book, chapter, verse) ?: 0
            dao.upsertHighlight(Highlight(id = id, book = book, chapter = chapter, verse = verse, color = color))
        }
    }

    suspend fun markChapterRead(book: Int, chapter: Int, read: Boolean) {
        if (read) { if (dao.markRead(ChapterRead(book, chapter)) != -1L) addPoints(Points.CHAPTER) }
        else dao.unmarkRead(book, chapter)
    }

    suspend fun markDevotional(type: String) {
        if (dao.insertDevotional(DevotionalDone(today(), type)) != -1L) addPoints(Points.DEVOTIONAL)
    }

    suspend fun saveQuiz(correct: Int, total: Int): Boolean {
        val inserted = dao.insertQuizDay(QuizDay(today(), correct, total)) != -1L
        if (inserted) addPoints(correct * Points.QUIZ_CORRECT + if (correct == total) Points.QUIZ_PERFECT_BONUS else 0)
        return inserted
    }

    suspend fun practicePoints(correct: Int) = addPoints(correct * 2)

    suspend fun markPlanDay(planId: Int, day: Int, done: Boolean) {
        if (done) { if (dao.markPlanDay(PlanDayDone(planId, day)) != -1L) addPoints(Points.PLAN_DAY) }
        else dao.unmarkPlanDay(planId, day)
    }

    suspend fun enroll(planId: Int) = dao.upsertEnrollment(PlanEnrollment(planId, today(), true))
    suspend fun leavePlan(planId: Int) { dao.deleteEnrollment(planId); dao.clearPlanDays(planId) }

    suspend fun saveStudy(s: Study): Long {
        val now = System.currentTimeMillis()
        return if (s.id == 0L) { addPoints(Points.STUDY); dao.insertStudy(s.copy(created = now, updated = now)) }
        else { dao.upsertStudy(s.copy(updated = now)); s.id }
    }

    suspend fun createTopic(name: String, description: String, color: Int): Long =
        dao.insertTopic(UserTopic(name = name.trim(), description = description.trim(), color = color))

    suspend fun deleteTopic(id: Long) = db.withTransaction { dao.deleteTopicVerses(id); dao.deleteTopic(id) }

    suspend fun createTag(name: String): Long {
        val n = name.trim().removePrefix("#")
        return dao.tagId(n) ?: dao.insertTag(Tag(name = n))
    }

    suspend fun deleteTag(id: Long) = db.withTransaction { dao.deleteTagVerses(id); dao.deleteTag(id) }

    suspend fun addMemory(book: Int, chapter: Int, verse: Int, verseEnd: Int) =
        dao.addMemory(MemoryVerse(book = book, chapter = chapter, verse = verse, verseEnd = verseEnd, nextReview = today()))

    /** Registra un repaso con el algoritmo SM-2 (calidad 0..3: otra vez, difícil, bien, fácil). */
    suspend fun reviewMemory(m: MemoryVerse, grade: Int) {
        val next = Sm2.review(m, grade, today())
        dao.upsertMemory(next)
        if (grade >= Sm2.HARD) addPoints(Points.MEMORY)
    }

    /** Compatibilidad: «lo sabía» = bien, «no lo sabía» = otra vez. */
    suspend fun reviewMemory(m: MemoryVerse, knew: Boolean) = reviewMemory(m, if (knew) Sm2.GOOD else Sm2.AGAIN)

    /** Reemplaza los resaltados de palabras de un versículo. ranges = pares (inicio, fin) de índices de palabra con su color. */
    suspend fun setWordHighlights(version: String, book: Int, chapter: Int, verse: Int, ranges: List<Triple<Int, Int, Int>>) = db.withTransaction {
        dao.clearWordHighlights(version, book, chapter, verse)
        ranges.forEach { (s, e, col) -> dao.insertWordHighlight(WordHighlight(version = version, book = book, chapter = chapter, verse = verse, wordStart = s, wordEnd = e, color = col)) }
    }

    suspend fun exportJson(): String = json.encodeToString(Backup.serializer(), Backup(
        highlights = dao.dumpHighlights(), notes = dao.dumpNotes(), bookmarks = dao.dumpBookmarks(),
        topics = dao.dumpTopics(), topicVerses = dao.dumpTopicVerses(), tags = dao.dumpTags(), tagVerses = dao.dumpTagVerses(),
        chaptersRead = dao.dumpChaptersRead(), enrollments = dao.dumpEnrollments(), planDays = dao.dumpPlanDays(),
        activity = dao.dumpActivity(), quizDays = dao.dumpQuizDays(), devotional = dao.dumpDevotional(), studies = dao.dumpStudies(),
        reflections = dao.dumpReflections(), memory = dao.dumpMemory(), wordHighlights = dao.dumpWordHighlights(),
        journal = dao.dumpJournal()
    ))

    /** Restaura un respaldo reemplazando todos los datos personales. */
    suspend fun importJson(text: String): Int {
        val b = json.decodeFromString(Backup.serializer(), text)
        db.withTransaction {
            db.clearAllTables()
            dao.insHighlights(b.highlights); dao.insNotes(b.notes); dao.insBookmarks(b.bookmarks)
            dao.insTopics(b.topics); dao.insTopicVerses(b.topicVerses); dao.insTags(b.tags); dao.insTagVerses(b.tagVerses)
            dao.insChaptersRead(b.chaptersRead); dao.insEnrollments(b.enrollments); dao.insPlanDays(b.planDays)
            dao.insActivity(b.activity); dao.insQuizDays(b.quizDays); dao.insDevotional(b.devotional); dao.insStudies(b.studies)
            dao.insReflections(b.reflections); dao.insMemory(b.memory); dao.insWordHighlights(b.wordHighlights); dao.insJournal(b.journal)
        }
        return b.highlights.size + b.notes.size + b.topicVerses.size + b.studies.size
    }
}
