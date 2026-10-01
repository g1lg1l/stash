package com.g1lg1l.stash

import com.g1lg1l.stash.data.Category
import com.g1lg1l.stash.data.RemoteSave
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.Tombstone
import com.g1lg1l.stash.data.isoDate
import com.g1lg1l.stash.data.parseIsoDate
import com.g1lg1l.stash.data.pullChanges
import com.g1lg1l.stash.data.pullUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTest {
    private val pushStart = 1_000L
    private val now = 2_000L

    private fun save(id: String, url: String = "https://example.com/$id", modifiedAt: Long = 0, title: String? = null) =
        Save(id = id, url = url, title = title, createdAt = 100, modifiedAt = modifiedAt)

    private fun pull(rows: List<RemoteSave>, local: List<Save>) = pullChanges(rows, local, pushStart, now)

    @Test fun skipsASaveChangedDuringTheSync() {
        val changes = pull(listOf(RemoteSave(save("a", title = "Server"), false)), listOf(save("a", modifiedAt = pushStart + 1)))
        assertTrue(changes.upserts.isEmpty() && changes.deletes.isEmpty())
    }

    @Test fun deletesWithoutATombstone() {
        val changes = pull(listOf(RemoteSave(save("a"), deleted = true)), listOf(save("a", modifiedAt = 500)))
        assertEquals(listOf("a"), changes.deletes)
        assertTrue(changes.tombstones.isEmpty())
    }

    @Test fun updatesWithTheServersFieldsWithoutMarkingForPush() {
        val changes = pull(listOf(RemoteSave(save("a", title = "Server"), false)), listOf(save("a", modifiedAt = 500, title = "Local")))
        assertEquals(listOf(save("a", title = "Server", modifiedAt = 500)), changes.upserts)
    }

    @Test fun mergesTheSameLinkIntoTheIdThatSortsFirst() {
        val link = "https://example.com/same"
        val server = save("B-server", url = link, title = "Server").copy(createdAt = 300, lastSavedAt = 900)
        val local = save("a-local", url = link).copy(createdAt = 200, lastSavedAt = 400, category = Category.FOOD, categoryIsManual = true, author = "Me")

        val merged = pull(listOf(RemoteSave(server, false)), listOf(local))
        val survivor = merged.upserts.single()
        assertEquals("a-local", survivor.id)
        assertEquals(listOf(Tombstone("B-server", now)), merged.tombstones)
        assertEquals(200L to 900L, survivor.createdAt to survivor.lastSavedAt)
        assertEquals("Server" to "Me", survivor.title to survivor.author)
        assertEquals(Category.FOOD, survivor.category)
        assertEquals(now, survivor.modifiedAt) // Goes back up.

        // The other way round, the local copy is the one that goes.
        val other = pull(listOf(RemoteSave(server.copy(id = "0-server"), false)), listOf(local))
        assertEquals("0-server", other.upserts.single().id)
        assertEquals(listOf("a-local"), other.deletes)
        assertEquals(listOf(Tombstone("a-local", now)), other.tombstones)
    }

    @Test fun insertsANewSaveWithoutMarkingForPush() {
        val changes = pull(listOf(RemoteSave(save("a"), false)), emptyList())
        assertEquals(listOf(save("a", modifiedAt = 0)), changes.upserts)
    }

    @Test fun encodesTheCursor() {
        val url = pullUrl("https://example.supabase.co".toHttpUrl(), "2026-10-01T12:00:00.123456+00:00")
        assertTrue(url.toString(), "updated_at=gt.2026-10-01T12%3A00%3A00.123456%2B00%3A00&" in url.toString())
    }

    @Test fun datesGoOutWithMillisecondsAndReadTheServersMicroseconds() {
        assertEquals("1970-01-01T00:00:01.500Z", isoDate(1_500))
        assertEquals(1_500L, parseIsoDate("1970-01-01T00:00:01.500123+00:00"))
    }
}
