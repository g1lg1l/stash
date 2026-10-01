package com.g1lg1l.stash

import com.g1lg1l.stash.data.Category
import com.g1lg1l.stash.data.ContentType
import com.g1lg1l.stash.data.Converters
import com.g1lg1l.stash.data.FeedSection
import com.g1lg1l.stash.data.Rediscovery
import com.g1lg1l.stash.data.SampleData
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.SaveStatus
import com.g1lg1l.stash.data.Search
import com.g1lg1l.stash.data.Source
import com.g1lg1l.stash.data.UrlSourceDetector
import com.g1lg1l.stash.data.displayTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SearchTest {
    private val saves = SampleData.saves()

    private fun titles(query: String) = Search.results(query, saves).map { it.displayTitle }

    @Test fun ignoresCaseAndAccents() {
        assertEquals(listOf("Sensō-ji"), titles("SENSO"))
    }

    @Test fun everyWordMustMatchSomeField() {
        // "tokyo" is a tag on both Tokyo places; "temple" only on Sensō-ji.
        assertEquals(setOf("Sensō-ji", "Tsukiji Outer Market"), titles("tokyo").toSet())
        assertEquals(listOf("Sensō-ji"), titles("tokyo temple"))
    }

    @Test fun searchesAuthorCategorySourceAndUrl() {
        assertEquals(listOf("Let's build GPT: from scratch, in code, spelled out."), titles("karpathy"))
        assertEquals(listOf("How to Get Startup Ideas"), titles("business"))
        assertEquals(listOf("Random Access Memories"), titles("spotify"))
        assertEquals(listOf("How to Get Startup Ideas"), titles("paulgraham.com"))
    }

    @Test fun blankQueryReturnsNothing() {
        assertTrue(titles("   ").isEmpty())
    }

    @Test fun keepsInputOrder() {
        val results = Search.results("youtube", saves)
        assertEquals(results.map { it.lastSavedAt }.sortedDescending(), results.map { it.lastSavedAt })
        assertEquals(3, results.size)
    }
}

class FeedSectionTest {
    @Test fun groupsByDay() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 10, 1, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun save(hoursAgo: Long) = Save(url = "https://example.com/$hoursAgo", createdAt = now - hoursAgo * 3600_000)
        // 1h: today. 20h: yesterday. 3 days: this week. 30 days: earlier.
        val saves = listOf(save(1), save(2), save(20), save(72), save(720))

        val sections = FeedSection.byDay(saves, now, zone)

        assertEquals(listOf("Today", "Yesterday", "Last 7 Days", "Earlier"), sections.map { it.title })
        assertEquals(listOf(2, 1, 1, 1), sections.map { it.saves.size })
    }
}

class RediscoveryTest {
    private val now = 10_000_000_000L
    private val day = 86_400_000L

    private fun save(daysAgo: Int, seen: Boolean = false): Save {
        val date = now - daysAgo * day
        return Save(url = "https://example.com/$daysAgo", createdAt = date, openedAt = if (seen) date else null)
    }

    @Test fun picksOldUnseenSavesOldestFirst() {
        val saves = listOf(save(1), save(4), save(30), save(10, seen = true), save(3))
        assertEquals(listOf("30", "4", "3"), Rediscovery.picks(saves, now).map { it.url.substringAfterLast('/') })
    }

    @Test fun isCapped() {
        assertEquals(6, Rediscovery.picks((4..20).map { save(it) }, now, limit = 6).size)
    }

    @Test fun widgetPrefersWorthAnotherLookThenUnseenThenNewest() {
        val old = save(5)
        val (pick, headline) = requireNotNull(Rediscovery.widgetPick(listOf(save(0, seen = true), old), now))
        assertEquals(old, pick)
        assertEquals("Worth another look", headline)

        val fresh = save(1)
        assertEquals(fresh to "Waiting for you", Rediscovery.widgetPick(listOf(save(0, seen = true), fresh), now))

        val newest = save(0, seen = true)
        assertEquals(newest to "Latest save", Rediscovery.widgetPick(listOf(save(2, seen = true), newest), now))
        assertNull(Rediscovery.widgetPick(emptyList(), now))
    }
}

class PersistenceTest {
    private val converters = Converters()

    @Test fun enumsAreStoredAsTheIosRawValues() {
        assertEquals("youtube", converters.fromSource(Source.YOUTUBE))
        assertEquals("food", converters.fromCategory(Category.FOOD))
        assertEquals(Source.X, converters.toSource("x"))
    }

    @Test fun unknownRawValuesFallBack() {
        assertEquals(Source.UNKNOWN, converters.toSource("myspace"))
        assertEquals(ContentType.OTHER, converters.toContentType("hologram"))
        assertEquals(Category.OTHER, converters.toCategory("gardening"))
        assertEquals(SaveStatus.PENDING, converters.toStatus("exploded"))
    }

    @Test fun tagsRoundTrip() {
        listOf(emptyList(), listOf("ai"), listOf("home gym", "kettlebell")).forEach {
            assertEquals(it, converters.toTags(converters.fromTags(it)))
        }
    }

    @Test fun sampleDataIsKeyedLikeRealShares() {
        SampleData.saves().forEach { assertEquals(it.url, UrlSourceDetector.analyze(it.url)?.canonicalUrl, it.canonicalUrl) }
    }

    @Test fun sampleDataHasNoDuplicateUrls() {
        val urls = SampleData.saves().map { it.canonicalUrl }
        assertEquals(urls.size, urls.toSet().size)
    }
}

class PerformanceTest {
    /** Evidence, not optimization: catches accidental O(n²) in the per-keystroke and per-render paths. */
    @Test fun searchAndSectioningStayFastAt2000Saves() {
        val samples = SampleData.saves()
        val saves = (0 until 2000).map { i ->
            val s = samples[i % samples.size]
            s.copy(id = "$i", url = "${s.url}?n=$i", createdAt = System.currentTimeMillis() - i * 3600_000L, lastSavedAt = System.currentTimeMillis() - i * 3600_000L)
        }
        val start = System.nanoTime()
        for (query in listOf("tokyo", "pasta recipe", "youtube", "nothing matches this")) Search.results(query, saves)
        FeedSection.byDay(saves)
        Rediscovery.picks(saves)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        println("4 searches + sectioning + rediscovery over 2000 saves: $elapsedMs ms")
        assertTrue("took $elapsedMs ms", elapsedMs < 1000)
    }
}
