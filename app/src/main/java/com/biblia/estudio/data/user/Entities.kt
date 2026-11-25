package com.biblia.estudio.data.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "highlights", indices = [Index(value = ["book", "chapter", "verse"], unique = true)])
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: Int, val chapter: Int, val verse: Int, val color: Int,
    val created: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "verse_notes", indices = [Index(value = ["book", "chapter", "verse"])])
data class VerseNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: Int, val chapter: Int, val verse: Int, val text: String,
    val created: Long = System.currentTimeMillis(), val updated: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "bookmarks", indices = [Index(value = ["book", "chapter", "verse"], unique = true)])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: Int, val chapter: Int, val verse: Int,
    val created: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "user_topics")
data class UserTopic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val description: String = "", val color: Int = 0,
    val created: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "topic_verses", indices = [Index("topicId"), Index(value = ["topicId", "book", "chapter", "verse"], unique = true)])
data class TopicVerse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: Long, val book: Int, val chapter: Int, val verse: Int, val verseEnd: Int = verse,
    val note: String = "", val created: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Serializable
@Entity(tableName = "tag_verses", indices = [Index("tagId"), Index(value = ["tagId", "book", "chapter", "verse"], unique = true)])
data class TagVerse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tagId: Long, val book: Int, val chapter: Int, val verse: Int,
    val created: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "chapters_read", primaryKeys = ["book", "chapter"])
data class ChapterRead(val book: Int, val chapter: Int, val readAt: Long = System.currentTimeMillis())

@Serializable
@Entity(tableName = "plan_enrollments")
data class PlanEnrollment(@PrimaryKey val planId: Int, val startDay: Long, val active: Boolean = true)

@Serializable
@Entity(tableName = "plan_days_done", primaryKeys = ["planId", "day"])
data class PlanDayDone(val planId: Int, val day: Int, val doneAt: Long = System.currentTimeMillis())

/** Puntos ganados por día (sirve para racha y nivel). */
@Serializable
@Entity(tableName = "activity_days")
data class ActivityDay(@PrimaryKey val epochDay: Long, val points: Int)

@Serializable
@Entity(tableName = "quiz_days")
data class QuizDay(@PrimaryKey val epochDay: Long, val correct: Int, val total: Int)

/** Elementos devocionales completados: tipo = verse | passage | lesson | prayer */
@Serializable
@Entity(tableName = "devotional_done", primaryKeys = ["epochDay", "type"])
data class DevotionalDone(val epochDay: Long, val type: String)

@Serializable
@Entity(tableName = "studies")
data class Study(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val book: Int, val chapter: Int, val verse: Int, val chapterEnd: Int, val verseEnd: Int,
    // Contexto
    val contextHistorical: String = "", val contextLiterary: String = "",
    // Exégesis
    val observation: String = "", val keyWords: String = "", val structure: String = "", val exegeticalIdea: String = "",
    // Hermenéutica
    val originalMeaning: String = "", val principle: String = "", val crossReferences: String = "", val application: String = "",
    // Homilética
    val proposition: String = "", val purpose: String = "", val outline: String = "", val illustrations: String = "",
    val sermonApplication: String = "", val conclusion: String = "",
    val created: Long = System.currentTimeMillis(), val updated: Long = System.currentTimeMillis()
)

/** Reflexión escrita por el usuario para una lección. */
@Serializable
@Entity(tableName = "lesson_reflections")
data class LessonReflection(@PrimaryKey val lessonId: Int, val text: String, val updated: Long = System.currentTimeMillis())

/** Versículo en memorización (sistema Leitner: caja 0..6, próxima revisión en días epoch). */
@Serializable
@Entity(tableName = "memory_verses", indices = [Index(value = ["book", "chapter", "verse"], unique = true)])
data class MemoryVerse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: Int, val chapter: Int, val verse: Int, val verseEnd: Int,
    val box: Int = 0, val nextReview: Long = 0, val reviews: Int = 0,
    val created: Long = System.currentTimeMillis(),
    // Repaso espaciado SM-2 (v4): factor de facilidad e intervalo actual en días.
    @ColumnInfo(defaultValue = "2.5") val ease: Double = 2.5,
    @ColumnInfo(defaultValue = "0") val interval: Int = 0,
    @ColumnInfo(defaultValue = "0") val lapses: Int = 0,
)

/** Resaltado de palabras sueltas dentro de un versículo (índices de palabra, en una versión concreta). */
@Serializable
@Entity(tableName = "word_highlights", indices = [Index(value = ["version", "book", "chapter"])])
data class WordHighlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val version: String, val book: Int, val chapter: Int, val verse: Int,
    val wordStart: Int, val wordEnd: Int, val color: Int,
    val created: Long = System.currentTimeMillis()
)

/**
 * Diario espiritual (v4): peticiones de oración (kind = "prayer") con seguimiento de respuesta,
 * y entradas de diario (kind = "journal"), opcionalmente ligadas a un versículo.
 */
@Serializable
@Entity(tableName = "journal", indices = [Index("kind")])
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String = "prayer",
    val title: String = "",
    val text: String = "",
    val book: Int = 0, val chapter: Int = 0, val verse: Int = 0,
    val answered: Boolean = false,
    val answeredAt: Long = 0,
    val answerNote: String = "",
    val created: Long = System.currentTimeMillis(), val updated: Long = System.currentTimeMillis()
)
