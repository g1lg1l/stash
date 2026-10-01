package com.g1lg1l.stash

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.g1lg1l.stash.data.Category
import com.g1lg1l.stash.data.ContentType
import com.g1lg1l.stash.data.Enricher
import com.g1lg1l.stash.data.LinkMetadata
import com.g1lg1l.stash.data.SampleData
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.SaveDao
import com.g1lg1l.stash.data.SaveStatus
import com.g1lg1l.stash.data.Source
import com.g1lg1l.stash.data.StashDatabase
import com.g1lg1l.stash.data.UrlSourceDetector
import com.g1lg1l.stash.data.raw
import com.g1lg1l.stash.data.withCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaveDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, StashDatabase::class.java,
    ).build()
    private val dao: SaveDao = database.saves()

    @After fun close() = database.close()

    private suspend fun add(url: String, at: Long) = dao.add(requireNotNull(UrlSourceDetector.analyze(url)), at)

    private suspend fun all() = dao.all().first()

    @Test fun saveRoundTrips() = runBlocking {
        val save = Save(
            url = "https://www.youtube.com/watch?v=kCc8FmEb1nY", source = Source.YOUTUBE,
            contentType = ContentType.VIDEO, category = Category.TECH, tags = listOf("ai", "home gym"),
        )
        dao.upsert(save)
        assertEquals(save, dao.get(save.id))
    }

    @Test fun savingTheSameResourceAgainUpdatesLastSavedAt() = runBlocking {
        val original = add("https://www.youtube.com/watch?v=kCc8FmEb1nY", at = 1_000)
        val duplicate = add("https://youtu.be/kCc8FmEb1nY?si=share", at = 2_000)

        assertEquals(original.id, duplicate.id)
        val saves = all()
        assertEquals(1, saves.size)
        assertEquals(1_000L, saves[0].createdAt)
        assertEquals(2_000L, saves[0].lastSavedAt)
        assertEquals("https://www.youtube.com/watch?v=kCc8FmEb1nY", saves[0].url)
    }

    @Test fun anOlderDuplicateKeepsTheEarliestCreatedAt() = runBlocking {
        add("https://x.com/jack/status/20", at = 2_000)
        val save = add("https://twitter.com/jack/status/20", at = 1_000)
        assertEquals(1_000L, save.createdAt)
        assertEquals(2_000L, save.lastSavedAt)
    }

    @Test fun differentResourcesAreSeparateSaves() = runBlocking {
        add("https://www.youtube.com/watch?v=aaa", at = 1)
        add("https://www.youtube.com/watch?v=bbb", at = 1)
        assertEquals(2, all().size)
    }

    @Test fun newSavesStartUnseenAndPendingWithDetectedSourceAndFirstGuess() = runBlocking {
        val save = add("https://open.spotify.com/album/4m2880jivSbbyEGAKfITCa", at = 1)
        assertEquals(Source.SPOTIFY, save.source)
        assertEquals(ContentType.MUSIC, save.contentType)
        assertEquals(Category.MUSIC, save.category)
        assertEquals(SaveStatus.PENDING, save.status)
        assertNull(save.openedAt)
    }

    @Test fun aManualCategorySticksThroughABackgroundUpdate() = runBlocking {
        val save = add("https://example.com/posts/42", at = 1)
        dao.update(save.id) { it.withCategory(Category.OTHER) }
        // Enrichment applies its result to the row as it is now, not the copy it started with.
        dao.update(save.id) { Enricher.apply(Result.success(LinkMetadata(title = "Weeknight pasta recipe")), it) }
        val enriched = requireNotNull(dao.get(save.id))
        assertEquals(Category.OTHER, enriched.category)
        assertEquals("Weeknight pasta recipe", enriched.title)
    }

    @Test fun findsSavesToEnrichByStatus() = runBlocking {
        dao.upsert(SampleData.saves())
        assertEquals(1, dao.withStatus(listOf(SaveStatus.PENDING.raw)).size)
        assertEquals(2, dao.withStatus(listOf(SaveStatus.PENDING.raw, SaveStatus.FAILED.raw)).size)
    }

    @Test fun deleteRemovesTheSave() = runBlocking {
        val save = add("https://example.com/a", at = 1)
        dao.delete(save)
        assertEquals(0, all().size)
    }

    @Test fun newestFirst() = runBlocking {
        add("https://example.com/old", at = 1)
        add("https://example.com/new", at = 2)
        assertEquals(listOf("https://example.com/new", "https://example.com/old"), all().map { it.url })
    }
}
