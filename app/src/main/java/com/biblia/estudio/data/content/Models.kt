package com.biblia.estudio.data.content

data class Book(val id: Int, val usfm: String, val name: String, val abbr: String, val testament: String, val chapters: Int)

data class BibleVersion(val id: String, val name: String, val copyright: String)

data class Verse(val book: Int, val chapter: Int, val verse: Int, val verseEnd: Int, val para: Boolean, val text: String) {
    val label: String get() = if (verseEnd > verse) "$verse-$verseEnd" else "$verse"
}

data class Heading(val beforeVerse: Int, val kind: String, val text: String)

/** Referencia a un pasaje. verse = 0 significa "capítulo completo". */
data class PassageRef(val book: Int, val chapter: Int, val verse: Int = 0, val chapterEnd: Int = chapter, val verseEnd: Int = verse) {
    fun label(books: List<Book>): String {
        val name = books.getOrNull(book - 1)?.name ?: "?"
        val sb = StringBuilder("$name $chapter")
        if (verse > 0) sb.append(":$verse")
        if (chapterEnd != chapter) {
            sb.append("–$chapterEnd"); if (verseEnd > 0) sb.append(":$verseEnd")
        } else if (verseEnd > verse && verse > 0) sb.append("–$verseEnd")
        return sb.toString()
    }
    fun contains(ch: Int, v: Int): Boolean {
        val start = chapter * 1000 + (if (verse == 0) 0 else verse)
        val end = chapterEnd * 1000 + (if (verseEnd == 0) 999 else verseEnd)
        val x = ch * 1000 + v
        return x in start..end
    }
}

data class StudyNote(val id: Long, val book: Int, val chapter: Int, val verse: Int, val chapterEnd: Int, val verseEnd: Int, val text: String) {
    val ref get() = PassageRef(book, chapter, verse, chapterEnd, verseEnd)
}

data class IntroSection(val section: String, val content: String)
data class Article(val id: Int, val category: String, val title: String, val content: String)
data class Lesson(val id: Int, val title: String, val kind: String, val book: Int, val content: String, val questions: List<String>, val ref: String)
data class Prayer(val id: Int, val title: String, val text: String, val ref: PassageRef)
data class DailyPassage(val id: Int, val title: String, val ref: PassageRef)
data class QuizQuestion(val id: Int, val question: String, val options: List<String>, val answer: Int, val ref: String, val category: String, val difficulty: Int)
data class Plan(val id: Int, val name: String, val description: String, val days: Int)
data class Reading(val book: Int, val chapterStart: Int, val chapterEnd: Int)
data class SearchHit(val version: String, val book: Int, val chapter: Int, val verse: Int, val text: String)
data class IndexTopic(val id: Int, val name: String)
data class CompareRow(val version: String, val versionName: String, val text: String)
