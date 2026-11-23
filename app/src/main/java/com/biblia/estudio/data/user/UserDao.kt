package com.biblia.estudio.data.user

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class TopicWithCount(val id: Long, val name: String, val description: String, val color: Int, val count: Int)
data class TagWithCount(val id: Long, val name: String, val count: Int)
data class BookCount(val book: Int, val count: Int)

@Dao
interface UserDao {
    // Resaltados
    @Query("SELECT * FROM highlights WHERE book=:book AND chapter=:chapter")
    fun highlightsForChapter(book: Int, chapter: Int): Flow<List<Highlight>>
    @Query("SELECT * FROM highlights ORDER BY created DESC")
    fun allHighlights(): Flow<List<Highlight>>
    @Upsert suspend fun upsertHighlight(h: Highlight)
    @Query("DELETE FROM highlights WHERE book=:book AND chapter=:chapter AND verse=:verse")
    suspend fun deleteHighlight(book: Int, chapter: Int, verse: Int)
    @Query("SELECT id FROM highlights WHERE book=:book AND chapter=:chapter AND verse=:verse")
    suspend fun highlightId(book: Int, chapter: Int, verse: Int): Long?

    // Notas personales
    @Query("SELECT * FROM verse_notes WHERE book=:book AND chapter=:chapter")
    fun notesForChapter(book: Int, chapter: Int): Flow<List<VerseNote>>
    @Query("SELECT * FROM verse_notes ORDER BY updated DESC")
    fun allNotes(): Flow<List<VerseNote>>
    @Upsert suspend fun upsertNote(n: VerseNote)
    @Delete suspend fun deleteNote(n: VerseNote)

    // Marcadores
    @Query("SELECT * FROM bookmarks ORDER BY created DESC")
    fun bookmarks(): Flow<List<Bookmark>>
    @Query("SELECT * FROM bookmarks WHERE book=:book AND chapter=:chapter")
    fun bookmarksForChapter(book: Int, chapter: Int): Flow<List<Bookmark>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addBookmark(b: Bookmark)
    @Query("DELETE FROM bookmarks WHERE book=:book AND chapter=:chapter AND verse=:verse")
    suspend fun deleteBookmark(book: Int, chapter: Int, verse: Int)

    // Temas
    @Query("SELECT t.id, t.name, t.description, t.color, (SELECT COUNT(*) FROM topic_verses v WHERE v.topicId=t.id) AS count FROM user_topics t ORDER BY t.name COLLATE NOCASE")
    fun topics(): Flow<List<TopicWithCount>>
    @Query("SELECT * FROM user_topics WHERE id=:id") fun topic(id: Long): Flow<UserTopic?>
    @Insert suspend fun insertTopic(t: UserTopic): Long
    @Upsert suspend fun upsertTopic(t: UserTopic)
    @Query("DELETE FROM user_topics WHERE id=:id") suspend fun deleteTopic(id: Long)
    @Query("DELETE FROM topic_verses WHERE topicId=:id") suspend fun deleteTopicVerses(id: Long)
    @Query("SELECT * FROM topic_verses WHERE topicId=:id ORDER BY book, chapter, verse")
    fun topicVerses(id: Long): Flow<List<TopicVerse>>
    @Query("SELECT topicId FROM topic_verses WHERE book=:book AND chapter=:chapter AND verse=:verse")
    suspend fun topicsOfVerse(book: Int, chapter: Int, verse: Int): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addTopicVerse(v: TopicVerse)
    @Upsert suspend fun upsertTopicVerse(v: TopicVerse)
    @Query("DELETE FROM topic_verses WHERE topicId=:topicId AND book=:book AND chapter=:chapter AND verse=:verse")
    suspend fun removeTopicVerse(topicId: Long, book: Int, chapter: Int, verse: Int)

    // Etiquetas
    @Query("SELECT t.id, t.name, (SELECT COUNT(*) FROM tag_verses v WHERE v.tagId=t.id) AS count FROM tags t ORDER BY t.name COLLATE NOCASE")
    fun tags(): Flow<List<TagWithCount>>
    @Query("SELECT * FROM tags WHERE id=:id") fun tag(id: Long): Flow<Tag?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTag(t: Tag): Long
    @Query("SELECT id FROM tags WHERE name=:name") suspend fun tagId(name: String): Long?
    @Query("DELETE FROM tags WHERE id=:id") suspend fun deleteTag(id: Long)
    @Query("DELETE FROM tag_verses WHERE tagId=:id") suspend fun deleteTagVerses(id: Long)
    @Query("SELECT * FROM tag_verses WHERE tagId=:id ORDER BY book, chapter, verse")
    fun tagVerses(id: Long): Flow<List<TagVerse>>
    @Query("SELECT tagId FROM tag_verses WHERE book=:book AND chapter=:chapter AND verse=:verse")
    suspend fun tagsOfVerse(book: Int, chapter: Int, verse: Int): List<Long>
    @Query("SELECT DISTINCT verse FROM tag_verses WHERE book=:book AND chapter=:chapter UNION SELECT DISTINCT verse FROM topic_verses WHERE book=:book AND chapter=:chapter")
    fun taggedVersesInChapter(book: Int, chapter: Int): Flow<List<Int>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addTagVerse(v: TagVerse)
    @Query("DELETE FROM tag_verses WHERE tagId=:tagId AND book=:book AND chapter=:chapter AND verse=:verse")
    suspend fun removeTagVerse(tagId: Long, book: Int, chapter: Int, verse: Int)

    // Progreso de lectura
    @Query("SELECT * FROM chapters_read") fun chaptersRead(): Flow<List<ChapterRead>>
    @Query("SELECT COUNT(*) FROM chapters_read WHERE book=:book AND chapter=:chapter") fun isRead(book: Int, chapter: Int): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun markRead(c: ChapterRead): Long
    @Query("DELETE FROM chapters_read WHERE book=:book AND chapter=:chapter") suspend fun unmarkRead(book: Int, chapter: Int)

    // Planes
    @Query("SELECT * FROM plan_enrollments WHERE active=1") fun enrollments(): Flow<List<PlanEnrollment>>
    @Upsert suspend fun upsertEnrollment(e: PlanEnrollment)
    @Query("DELETE FROM plan_enrollments WHERE planId=:planId") suspend fun deleteEnrollment(planId: Int)
    @Query("DELETE FROM plan_days_done WHERE planId=:planId") suspend fun clearPlanDays(planId: Int)
    @Query("SELECT * FROM plan_days_done WHERE planId=:planId") fun planDaysDone(planId: Int): Flow<List<PlanDayDone>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun markPlanDay(d: PlanDayDone): Long
    @Query("DELETE FROM plan_days_done WHERE planId=:planId AND day=:day") suspend fun unmarkPlanDay(planId: Int, day: Int)

    // Gamificación
    @Query("SELECT * FROM activity_days ORDER BY epochDay DESC") fun activity(): Flow<List<ActivityDay>>
    @Query("SELECT * FROM activity_days WHERE epochDay=:day") suspend fun activityDay(day: Long): ActivityDay?
    @Upsert suspend fun upsertActivity(a: ActivityDay)
    @Query("SELECT * FROM quiz_days ORDER BY epochDay DESC") fun quizDays(): Flow<List<QuizDay>>
    @Query("SELECT * FROM quiz_days WHERE epochDay=:day") fun quizDay(day: Long): Flow<QuizDay?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertQuizDay(q: QuizDay): Long
    @Query("SELECT * FROM devotional_done WHERE epochDay=:day") fun devotionalDone(day: Long): Flow<List<DevotionalDone>>
    @Query("SELECT COUNT(*) FROM devotional_done") fun devotionalCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDevotional(d: DevotionalDone): Long

    // Estudios
    @Query("SELECT * FROM studies ORDER BY updated DESC") fun studies(): Flow<List<Study>>
    @Query("SELECT * FROM studies WHERE id=:id") suspend fun study(id: Long): Study?
    @Insert suspend fun insertStudy(s: Study): Long
    @Upsert suspend fun upsertStudy(s: Study)
    @Query("DELETE FROM studies WHERE id=:id") suspend fun deleteStudy(id: Long)

    // Reflexiones de lecciones
    @Query("SELECT * FROM lesson_reflections WHERE lessonId=:id") fun reflection(id: Int): Flow<LessonReflection?>
    @Upsert suspend fun upsertReflection(r: LessonReflection)
    @Query("DELETE FROM lesson_reflections WHERE lessonId=:id") suspend fun deleteReflection(id: Int)

    // Memorización
    @Query("SELECT * FROM memory_verses ORDER BY box, created") fun memoryVerses(): Flow<List<MemoryVerse>>
    @Query("SELECT * FROM memory_verses WHERE nextReview<=:day ORDER BY box, nextReview") suspend fun dueMemory(day: Long): List<MemoryVerse>
    @Query("SELECT COUNT(*) FROM memory_verses WHERE nextReview<=:day") fun dueMemoryCount(day: Long): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMemory(m: MemoryVerse): Long
    @Upsert suspend fun upsertMemory(m: MemoryVerse)
    @Query("DELETE FROM memory_verses WHERE id=:id") suspend fun deleteMemory(id: Long)

    // Respaldo
    @Query("SELECT * FROM highlights") suspend fun dumpHighlights(): List<Highlight>
    @Query("SELECT * FROM verse_notes") suspend fun dumpNotes(): List<VerseNote>
    @Query("SELECT * FROM bookmarks") suspend fun dumpBookmarks(): List<Bookmark>
    @Query("SELECT * FROM user_topics") suspend fun dumpTopics(): List<UserTopic>
    @Query("SELECT * FROM topic_verses") suspend fun dumpTopicVerses(): List<TopicVerse>
    @Query("SELECT * FROM tags") suspend fun dumpTags(): List<Tag>
    @Query("SELECT * FROM tag_verses") suspend fun dumpTagVerses(): List<TagVerse>
    @Query("SELECT * FROM chapters_read") suspend fun dumpChaptersRead(): List<ChapterRead>
    @Query("SELECT * FROM plan_enrollments") suspend fun dumpEnrollments(): List<PlanEnrollment>
    @Query("SELECT * FROM plan_days_done") suspend fun dumpPlanDays(): List<PlanDayDone>
    @Query("SELECT * FROM activity_days") suspend fun dumpActivity(): List<ActivityDay>
    @Query("SELECT * FROM quiz_days") suspend fun dumpQuizDays(): List<QuizDay>
    @Query("SELECT * FROM devotional_done") suspend fun dumpDevotional(): List<DevotionalDone>
    @Query("SELECT * FROM studies") suspend fun dumpStudies(): List<Study>
    @Query("SELECT * FROM lesson_reflections") suspend fun dumpReflections(): List<LessonReflection>
    @Query("SELECT * FROM memory_verses") suspend fun dumpMemory(): List<MemoryVerse>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insHighlights(x: List<Highlight>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insNotes(x: List<VerseNote>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insBookmarks(x: List<Bookmark>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insTopics(x: List<UserTopic>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insTopicVerses(x: List<TopicVerse>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insTags(x: List<Tag>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insTagVerses(x: List<TagVerse>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insChaptersRead(x: List<ChapterRead>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insEnrollments(x: List<PlanEnrollment>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insPlanDays(x: List<PlanDayDone>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insActivity(x: List<ActivityDay>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insQuizDays(x: List<QuizDay>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insDevotional(x: List<DevotionalDone>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insStudies(x: List<Study>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insReflections(x: List<LessonReflection>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insMemory(x: List<MemoryVerse>)
}
