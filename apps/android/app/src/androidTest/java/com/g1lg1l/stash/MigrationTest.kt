package com.g1lg1l.stash

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.g1lg1l.stash.data.StashDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A version 1 database, as schemas/…/1.json has it, keeps its saves after the upgrade. */
    @Test fun savesSurviveTheUpgradeToVersion2(): Unit = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL(
                "CREATE TABLE saves (id TEXT NOT NULL, url TEXT NOT NULL, canonicalUrl TEXT NOT NULL, source TEXT NOT NULL, " +
                    "contentType TEXT NOT NULL, category TEXT NOT NULL, status TEXT NOT NULL, categoryIsManual INTEGER NOT NULL, " +
                    "title TEXT, descriptionText TEXT, thumbnailUrl TEXT, author TEXT, tags TEXT NOT NULL, summary TEXT, " +
                    "createdAt INTEGER NOT NULL, lastSavedAt INTEGER NOT NULL, openedAt INTEGER, PRIMARY KEY(id))",
            )
            db.execSQL("CREATE INDEX index_saves_canonicalUrl ON saves (canonicalUrl)")
            db.execSQL("CREATE INDEX index_saves_lastSavedAt ON saves (lastSavedAt)")
            db.execSQL(
                "INSERT INTO saves VALUES ('a', 'https://example.com', 'example.com', 'web', 'other', 'food', 'enriched', 1, " +
                    "'Pasta', NULL, NULL, NULL, '', NULL, 1, 2, NULL)",
            )
            db.version = 1
        }

        val database = Room.databaseBuilder(context, StashDatabase::class.java, name).build()
        val save = requireNotNull(database.saves().get("a"))
        assertEquals("Pasta", save.title)
        assertEquals(0L, save.modifiedAt)
        assertEquals(0, database.saves().tombstones().size)
        database.close()
        context.deleteDatabase(name)
    }
}
