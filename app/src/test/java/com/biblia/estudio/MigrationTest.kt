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
}
