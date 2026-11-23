package com.biblia.estudio.data.content

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Acceso de solo lectura a la base de contenido (Biblias, notas de estudio, contenido diario).
 * La base viaja comprimida en assets/bible_content.bin (gzip) y se descomprime la primera vez que se abre la app.
 */
class ContentRepository(private val context: Context) {
    companion object {
        const val DB_NAME = "bible_content.db"
        const val CONTENT_VERSION = 3
    }

    private var db: SQLiteDatabase? = null
    private val mutex = Mutex()
    var books: List<Book> = emptyList(); private set
    var versions: List<BibleVersion> = emptyList(); private set

    suspend fun ensureReady(onProgress: (Float) -> Unit = {}) = mutex.withLock {
        if (db != null) return@withLock
        withContext(Dispatchers.IO) {
            val file = context.getDatabasePath(DB_NAME)
            val prefs = context.getSharedPreferences("content", Context.MODE_PRIVATE)
            if (!file.exists() || prefs.getInt("version", 0) != CONTENT_VERSION) {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "$DB_NAME.tmp")
                val total = 110_000_000f
                var copied = 0L
                GZIPInputStream(context.assets.open("bible_content.bin"), 1 shl 16).use { input ->
                    tmp.outputStream().buffered(1 shl 16).use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); copied += n
                            onProgress((copied / total).coerceAtMost(0.99f))
                        }
                    }
                }
                file.delete(); tmp.renameTo(file)
                prefs.edit().putInt("version", CONTENT_VERSION).apply()
            }
            val d = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            db = d
            books = d.list("SELECT id,usfm,name,abbr,testament,chapters FROM books ORDER BY id") {
                Book(it.getInt(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5))
            }
            versions = d.list("SELECT id,name,copyright FROM versions ORDER BY ord") { BibleVersion(it.getString(0), it.getString(1), it.getString(2) ?: "") }
            onProgress(1f)
        }
    }

    private val d: SQLiteDatabase get() = db ?: error("Base de contenido no abierta")

    private inline fun <T> SQLiteDatabase.list(sql: String, vararg args: Any, map: (Cursor) -> T): List<T> {
        rawQuery(sql, args.map { it.toString() }.toTypedArray()).use { c ->
            val out = ArrayList<T>(c.count)
            while (c.moveToNext()) out.add(map(c))
            return out
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    fun book(id: Int): Book = books[id - 1]
    fun label(ref: PassageRef) = ref.label(books)

    // ---------------- Biblia ----------------
    suspend fun chapter(version: String, book: Int, chapter: Int): List<Verse> = io {
        d.list("SELECT book,chapter,verse,verse_end,para,text FROM verses WHERE version=? AND book=? AND chapter=? ORDER BY verse", version, book, chapter) {
            Verse(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4) == 1, it.getString(5))
        }
    }

    suspend fun headings(version: String, book: Int, chapter: Int): List<Heading> = io {
        d.list("SELECT before_verse,kind,text FROM headings WHERE version=? AND book=? AND chapter=?", version, book, chapter) {
            Heading(it.getInt(0), it.getString(1), it.getString(2))
        }
    }

    suspend fun passage(version: String, ref: PassageRef): List<Verse> = io {
        val ve = if (ref.verseEnd == 0) 999 else ref.verseEnd
        d.list(
            "SELECT book,chapter,verse,verse_end,para,text FROM verses WHERE version=? AND book=? AND (chapter>? OR (chapter=? AND verse>=?)) AND (chapter<? OR (chapter=? AND verse<=?)) ORDER BY chapter,verse",
            version, ref.book, ref.chapter, ref.chapter, ref.verse, ref.chapterEnd, ref.chapterEnd, ve
        ) { Verse(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4) == 1, it.getString(5)) }
    }

    suspend fun passageText(version: String, ref: PassageRef): String = passage(version, ref).joinToString(" ") { it.text }

    suspend fun verseText(version: String, book: Int, chapter: Int, verse: Int): String = io {
        d.list("SELECT text FROM verses WHERE version=? AND book=? AND chapter=? AND verse<=? AND verse_end>=? ORDER BY verse DESC LIMIT 1", version, book, chapter, verse, verse) { it.getString(0) }
            .firstOrNull() ?: ""
    }

    suspend fun compare(book: Int, chapter: Int, verse: Int): List<CompareRow> = io {
        val names = versions.associate { it.id to it.name }
        val order = versions.map { it.id }
        d.list("SELECT version,text FROM verses WHERE book=? AND chapter=? AND verse<=? AND verse_end>=?", book, chapter, verse, verse) {
            CompareRow(it.getString(0), names[it.getString(0)] ?: it.getString(0), it.getString(1))
        }.sortedBy { order.indexOf(it.version) }
    }

    /** Número de versículos del capítulo (según RVR1960). */
    suspend fun verseCount(book: Int, chapter: Int): Int = io {
        d.list("SELECT MAX(verse_end) FROM verses WHERE version='RVR1960' AND book=? AND chapter=?", book, chapter) { it.getInt(0) }.firstOrNull() ?: 0
    }

    suspend fun crossRefs(book: Int, chapter: Int, verse: Int): List<String> = io {
        d.list("SELECT refs FROM crossrefs WHERE book=? AND chapter=? AND verse=?", book, chapter, verse) { it.getString(0) }
    }

    suspend fun chaptersWithNotes(book: Int, chapter: Int): Set<Int> = io {
        d.list("SELECT verse, verse_end, chapter_end FROM study_notes WHERE book=? AND chapter=?", book, chapter) { it.getInt(0) }.toSet()
    }

    suspend fun search(query: String, version: String?, testament: String?, book: Int?, limit: Int = 300): List<SearchHit> = io {
        val q = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.replace(Regex("[\"*():^-]"), "") + "*" }
        if (q.isBlank()) return@io emptyList()
        val sb = StringBuilder("SELECT v.version,v.book,v.chapter,v.verse,v.text FROM verses_fts f JOIN verses v ON v.id=f.docid WHERE verses_fts MATCH ?")
        val args = mutableListOf<Any>(q)
        if (version != null) { sb.append(" AND v.version=?"); args.add(version) }
        if (testament != null) { sb.append(if (testament == "AT") " AND v.book<=39" else " AND v.book>=40") }
        if (book != null) { sb.append(" AND v.book=?"); args.add(book) }
        sb.append(" ORDER BY v.book,v.chapter,v.verse LIMIT $limit")
        d.list(sb.toString(), *args.toTypedArray()) { SearchHit(it.getString(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getString(4)) }
    }

    // ---------------- Estudio MacArthur ----------------
    suspend fun notesForChapter(book: Int, chapter: Int): List<StudyNote> = io {
        d.list("SELECT id,book,chapter,verse,chapter_end,verse_end,text FROM study_notes WHERE book=? AND (chapter=? OR (chapter<? AND chapter_end>=?)) ORDER BY chapter,verse,id", book, chapter, chapter, chapter) {
            StudyNote(it.getLong(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4), it.getInt(5), it.getString(6))
        }
    }

    suspend fun notesForVerse(book: Int, chapter: Int, verse: Int): List<StudyNote> =
        notesForChapter(book, chapter).filter { it.ref.contains(chapter, verse) }

    suspend fun intro(book: Int): List<IntroSection> = io {
        d.list("SELECT section,content FROM study_intros WHERE book=? ORDER BY ord", book) { IntroSection(it.getString(0), it.getString(1)) }
    }

    suspend fun articles(): List<Article> = io {
        d.list("SELECT id,category,title,'' FROM articles ORDER BY id") { Article(it.getInt(0), it.getString(1), it.getString(2), "") }
    }

    suspend fun article(id: Int): Article? = io {
        d.list("SELECT id,category,title,content FROM articles WHERE id=?", id) { Article(it.getInt(0), it.getString(1), it.getString(2), it.getString(3)) }.firstOrNull()
    }

    suspend fun indexTopics(filter: String = ""): List<IndexTopic> = io {
        d.list("SELECT id,name FROM topics WHERE name LIKE ? ORDER BY id", "%$filter%") { IndexTopic(it.getInt(0), it.getString(1)) }
    }

    suspend fun indexTopic(id: Int): Pair<String, List<String>> = io {
        val name = d.list("SELECT name FROM topics WHERE id=?", id) { it.getString(0) }.firstOrNull() ?: ""
        name to d.list("SELECT text FROM topic_entries WHERE topic=? ORDER BY ord", id) { it.getString(0) }
    }

    suspend fun abbreviations(): Map<String, Int> = io {
        d.list("SELECT abbr,book FROM book_abbr") { it.getString(0) to it.getInt(1) }.toMap()
    }

    // ---------------- Diccionario bíblico ----------------
    private fun fold(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().trim()

    suspend fun dictionarySearch(query: String, category: String?): List<DictEntry> = io {
        val q = fold(query)
        val sb = StringBuilder("SELECT id,term,category,'' FROM dictionary WHERE 1=1")
        val args = mutableListOf<Any>()
        if (q.isNotEmpty()) { sb.append(" AND (norm LIKE ? OR norm_def LIKE ?)"); args.add("$q%"); args.add("% $q%") }
        if (category != null) { sb.append(" AND category=?"); args.add(category) }
        sb.append(" ORDER BY CASE WHEN norm LIKE ? THEN 0 ELSE 1 END, norm"); args.add("$q%")
        d.list(sb.toString(), *args.toTypedArray()) { DictEntry(it.getInt(0), it.getString(1), it.getString(2), "") }
    }

    suspend fun dictionaryCategories(): List<String> = io { d.list("SELECT DISTINCT category FROM dictionary ORDER BY category") { it.getString(0) } }

    suspend fun dictionaryEntry(id: Int): DictEntry? = io {
        d.list("SELECT id,term,category,definition FROM dictionary WHERE id=?", id) { DictEntry(it.getInt(0), it.getString(1), it.getString(2), it.getString(3)) }.firstOrNull()
    }

    /** Busca una entrada cuyo término coincida exactamente (sin acentos ni mayúsculas). */
    suspend fun dictionaryFind(term: String): DictEntry? = io {
        val q = fold(term)
        d.list("SELECT id,term,category,definition FROM dictionary WHERE norm=? OR norm=? LIMIT 1", q, q.removeSuffix("s")) { DictEntry(it.getInt(0), it.getString(1), it.getString(2), it.getString(3)) }.firstOrNull()
    }

    suspend fun notesMentioning(term: String, limit: Int = 15): List<StudyNote> = io {
        d.list("SELECT id,book,chapter,verse,chapter_end,verse_end,text FROM study_notes WHERE text LIKE ? ORDER BY book,chapter,verse LIMIT $limit", "%$term%") {
            StudyNote(it.getLong(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4), it.getInt(5), it.getString(6))
        }
    }

    suspend fun countInBible(term: String, version: String = "RVR1960"): Int = io {
        val q = term.trim().split(Regex("\\s+")).joinToString(" ") { it.replace(Regex("[\"*():^-]"), "") }
        if (q.isBlank()) 0 else d.list("SELECT COUNT(*) FROM verses_fts f JOIN verses v ON v.id=f.docid WHERE verses_fts MATCH ? AND v.version=?", "\"$q\"", version) { it.getInt(0) }.first()
    }

    suspend fun indexTopicFor(term: String): IndexTopic? = io {
        val t = term.uppercase()
        d.list("SELECT id,name FROM topics WHERE name=? OR name LIKE ? OR name LIKE ? ORDER BY length(name) LIMIT 1", t, "$t,%", "$t %") { IndexTopic(it.getInt(0), it.getString(1)) }.firstOrNull()
    }

    // ---------------- Contenido diario ----------------
    private fun count(table: String): Int = d.list("SELECT COUNT(*) FROM $table") { it.getInt(0) }.first()

    suspend fun verseOfDay(day: Long): PassageRef = io {
        val n = count("daily_verses"); val off = Math.floorMod(day, n.toLong())
        d.list("SELECT book,chapter,verse,verse_end FROM daily_verses ORDER BY id LIMIT 1 OFFSET $off") {
            PassageRef(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(1), it.getInt(3))
        }.first()
    }

    suspend fun passageOfDay(day: Long): DailyPassage = io {
        val n = count("daily_passages"); val off = Math.floorMod(day, n.toLong())
        d.list("SELECT id,title,book,chapter,verse,chapter_end,verse_end FROM daily_passages ORDER BY id LIMIT 1 OFFSET $off") {
            DailyPassage(it.getInt(0), it.getString(1), PassageRef(it.getInt(2), it.getInt(3), it.getInt(4), it.getInt(5), it.getInt(6)))
        }.first()
    }

    suspend fun prayerOfDay(day: Long): Prayer = io {
        val n = count("prayers"); val off = Math.floorMod(day, n.toLong())
        d.list("SELECT id,title,text,book,chapter,verse,chapter_end,verse_end FROM prayers ORDER BY id LIMIT 1 OFFSET $off") {
            Prayer(it.getInt(0), it.getString(1), it.getString(2), PassageRef(it.getInt(3), it.getInt(4), it.getInt(5), it.getInt(6), it.getInt(7)))
        }.first()
    }

    suspend fun lessonCount(): Int = io { count("lessons") }

    suspend fun lessonOfDay(day: Long): Lesson = io {
        val n = count("lessons"); lessonById(Math.floorMod(day, n.toLong()).toInt() + 1)!!
    }

    suspend fun lesson(id: Int): Lesson? = io { lessonById(id) }

    private fun lessonById(id: Int): Lesson? =
        d.list("SELECT id,title,kind,book,content,questions,ref FROM lessons WHERE id=?", id) {
            val qa = JSONArray(it.getString(5)); Lesson(it.getInt(0), it.getString(1), it.getString(2), it.getInt(3), it.getString(4), List(qa.length()) { i -> qa.getString(i) }, it.getString(6))
        }.firstOrNull()

    suspend fun lessonTitles(): List<Pair<Int, String>> = io { d.list("SELECT id,title FROM lessons ORDER BY id") { it.getInt(0) to it.getString(1) } }

    /** Cinco preguntas del día, deterministas según la fecha, con dificultad mezclada. */
    suspend fun dailyQuiz(day: Long, size: Int = 5): List<QuizQuestion> = io {
        val rnd = java.util.Random(day * 7919 + 13)
        val byDiff = (1..3).associateWith { diff -> d.list("SELECT id FROM quiz WHERE difficulty=?", diff) { it.getInt(0) } }
        val pattern = listOf(1, 1, 2, 2, 3, 2, 1, 3, 2, 1)
        val ids = LinkedHashSet<Int>()
        var i = 0
        while (ids.size < size && i < 50) {
            val pool = byDiff[pattern[i % pattern.size]].orEmpty()
            if (pool.isNotEmpty()) ids.add(pool[rnd.nextInt(pool.size)])
            i++
        }
        ids.mapNotNull { quizById(it) }
    }

    suspend fun randomQuiz(size: Int, category: String? = null): List<QuizQuestion> = io {
        val ids = if (category == null) d.list("SELECT id FROM quiz ORDER BY RANDOM() LIMIT $size") { it.getInt(0) }
        else d.list("SELECT id FROM quiz WHERE category=? ORDER BY RANDOM() LIMIT $size", category) { it.getInt(0) }
        ids.mapNotNull { quizById(it) }
    }

    suspend fun quizCategories(): List<String> = io { d.list("SELECT DISTINCT category FROM quiz ORDER BY category") { it.getString(0) } }

    private fun quizById(id: Int): QuizQuestion? =
        d.list("SELECT id,question,options,answer,ref,category,difficulty FROM quiz WHERE id=?", id) {
            val o = JSONArray(it.getString(2))
            QuizQuestion(it.getInt(0), it.getString(1), List(o.length()) { i -> o.getString(i) }, it.getInt(3), it.getString(4), it.getString(5), it.getInt(6))
        }.firstOrNull()

    // ---------------- Planes ----------------
    suspend fun plans(): List<Plan> = io { d.list("SELECT id,name,description,days FROM plans ORDER BY id") { Plan(it.getInt(0), it.getString(1), it.getString(2), it.getInt(3)) } }

    suspend fun planDays(plan: Int): List<List<Reading>> = io {
        d.list("SELECT readings FROM plan_days WHERE plan=? ORDER BY day", plan) { parseReadings(it.getString(0)) }
    }

    private fun parseReadings(s: String): List<Reading> = s.split(";").filter { it.isNotBlank() }.map {
        val (b, r) = it.split(":"); val (a, e) = r.split("-")
        Reading(b.toInt(), a.toInt(), e.toInt())
    }

    fun readingLabel(r: Reading): String {
        val b = book(r.book)
        return if (r.chapterStart == r.chapterEnd) "${b.name} ${r.chapterStart}" else "${b.name} ${r.chapterStart}–${r.chapterEnd}"
    }
}
