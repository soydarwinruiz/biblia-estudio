package com.biblia.estudio

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.biblia.estudio.data.user.UserDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Datos de la versión 1.4 (esquema 3) deben sobrevivir intactos al actualizar a la 1.5 (esquema 4). */
@RunWith(AndroidJUnit4::class)
@Config(application = BibliaApp::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UserDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun from3to4KeepsData() {
        helper.createDatabase("mig.db", 3).use { db ->
            db.execSQL("INSERT INTO highlights(book,chapter,verse,color,created) VALUES (43,3,16,-1,1)")
            db.execSQL("INSERT INTO memory_verses(book,chapter,verse,verseEnd,box,nextReview,reviews,created) VALUES (19,23,1,1,4,100,6,1)")
            db.execSQL("INSERT INTO studies(title,book,chapter,verse,chapterEnd,verseEnd,contextHistorical,contextLiterary,observation,keyWords,structure,exegeticalIdea,originalMeaning,principle,crossReferences,application,proposition,purpose,outline,illustrations,sermonApplication,conclusion,created,updated) VALUES ('Romanos 8',45,8,1,8,39,'','','obs','','','','','','','','','','','','','',1,1)")
        }
        val db = helper.runMigrationsAndValidate("mig.db", 4, true)
        db.query("SELECT COUNT(*) FROM highlights").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT box, ease, interval, lapses, reviews FROM memory_verses").use {
            it.moveToFirst(); assertEquals(4, it.getInt(0)); assertEquals(2.5, it.getDouble(1), 0.0); assertEquals(0, it.getInt(2)); assertEquals(0, it.getInt(3)); assertEquals(6, it.getInt(4))
        }
        db.query("SELECT title FROM studies").use { it.moveToFirst(); assertEquals("Romanos 8", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM journal").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }

    @Test fun from4to5KeepsTopicsAndJournal() {
        helper.createDatabase("mig5.db", 4).use { db ->
            db.execSQL("INSERT INTO user_topics(id,name,description,color,created) VALUES (1,'Fe','',0,1)")
            db.execSQL("INSERT INTO topic_verses(topicId,book,chapter,verse,verseEnd,note,created) VALUES (1,58,11,1,1,'',5)")
            db.execSQL("INSERT INTO topic_verses(topicId,book,chapter,verse,verseEnd,note,created) VALUES (1,1,15,6,6,'',9)")
            db.execSQL("INSERT INTO journal(kind,title,text,book,chapter,verse,answered,answeredAt,answerNote,created,updated) VALUES ('prayer','Familia','',0,0,0,0,0,'',1,1)")
        }
        val db = helper.runMigrationsAndValidate("mig5.db", 5, true)
        // Sin orden guardado, se respeta el orden en que se añadieron (Hebreos antes que Génesis)
        db.query("SELECT book, position FROM topic_verses WHERE topicId=1 ORDER BY position, created, id").use {
            it.moveToFirst(); assertEquals(58, it.getInt(0)); assertEquals(0, it.getInt(1)); it.moveToNext(); assertEquals(1, it.getInt(0))
        }
        db.query("SELECT title, eventType, eventAt, place, reminderMinutes FROM journal").use {
            it.moveToFirst(); assertEquals("Familia", it.getString(0)); assertEquals("", it.getString(1)); assertEquals(0L, it.getLong(2)); assertEquals(-1, it.getInt(4))
        }
        db.close()
    }

    @Test fun from5to6AddsRepeatAndAwards() {
        helper.createDatabase("mig6.db", 5).use { db ->
            db.execSQL("INSERT INTO journal(kind,title,text,book,chapter,verse,answered,answeredAt,answerNote,created,updated,eventType,eventAt,place,reminderMinutes) VALUES ('event','Culto','',0,0,0,0,0,'',1,1,'Culto',1800000000000,'Templo',60)")
            db.execSQL("INSERT INTO chapters_read(book,chapter,readAt) VALUES (43,3,1)")
        }
        val db = helper.runMigrationsAndValidate("mig6.db", 6, true)
        db.query("SELECT title, place, repeatRule, repeatUntil FROM journal").use {
            it.moveToFirst(); assertEquals("Culto", it.getString(0)); assertEquals("Templo", it.getString(1)); assertEquals("", it.getString(2)); assertEquals(0L, it.getLong(3))
        }
        db.query("SELECT COUNT(*) FROM point_awards").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM quiz_seen").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }
}
