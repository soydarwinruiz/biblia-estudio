package com.biblia.estudio.data.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Highlight::class, VerseNote::class, Bookmark::class, UserTopic::class, TopicVerse::class, Tag::class, TagVerse::class,
        ChapterRead::class, PlanEnrollment::class, PlanDayDone::class, ActivityDay::class, QuizDay::class, DevotionalDone::class, Study::class],
    version = 1, exportSchema = true
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun dao(): UserDao
    companion object {
        fun create(context: Context): UserDatabase =
            Room.databaseBuilder(context, UserDatabase::class.java, "user_data.db").build()
    }
}
