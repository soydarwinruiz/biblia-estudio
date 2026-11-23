package com.biblia.estudio.data.user

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
