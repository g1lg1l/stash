package com.g1lg1l.stash

import com.g1lg1l.stash.data.Category
import com.g1lg1l.stash.data.Classification
import com.g1lg1l.stash.data.ContentType
import com.g1lg1l.stash.data.Enricher
import com.g1lg1l.stash.data.LinkMetadata
import com.g1lg1l.stash.data.MetadataService
import com.g1lg1l.stash.data.Offline
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.SaveStatus
import com.g1lg1l.stash.data.Source
import com.g1lg1l.stash.data.Unavailable
import com.g1lg1l.stash.data.withCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataParsingTest {
    private val base = "https://blog.example.com/posts/pasta"

    @Test fun readsOpenGraphInAnyAttributeOrder() {
        val html = """
            <html><head>
            <title>Fallback title</title>
            <meta content="Creamy cacio e pepe" property="og:title">
            <meta property='og:description' content='Three ingredients &amp; one pan.'>
            <meta property="og:image" content="/img/cacio.jpg" />
            <meta name="author" content="Marta Rossi">
            <meta property="og:type" content="article">
            </head><body><meta property="og:title" content="Not in head"></body></html>
        """.trimIndent()
        val metadata = MetadataService.parseHtml(html, base)
        assertEquals("Creamy cacio e pepe", metadata.title)
        assertEquals("Three ingredients & one pan.", metadata.description)
        assertEquals("https://blog.example.com/img/cacio.jpg", metadata.imageUrl)
        assertEquals("Marta Rossi", metadata.author)
        assertEquals(ContentType.ARTICLE, metadata.contentType)
    }

    @Test fun fallsBackToTwitterCardsAndTitleTag() {
        val html = """
            <head><TITLE>Plain &#8220;title&#8221; &#x2014; site</TITLE>
            <meta name="twitter:image" content="https://cdn.example.com/card.png">
            <meta name="description" content="Meta description">
            <meta property="article:author" content="https://facebook.com/someone"></head>
        """.trimIndent()
        val metadata = MetadataService.parseHtml(html, base)
        assertEquals("Plain “title” — site", metadata.title)
        assertEquals("https://cdn.example.com/card.png", metadata.imageUrl)
        assertEquals("Meta description", metadata.description)
        assertNull(metadata.author) // A profile link isn't a name.
    }

    @Test fun mapsOpenGraphTypes() {
        listOf(
            "video.other" to ContentType.VIDEO, "music.album" to ContentType.MUSIC, "product" to ContentType.PRODUCT,
            "og:product" to ContentType.PRODUCT, "restaurant.restaurant" to ContentType.PLACE, "website" to null,
        ).forEach { (type, expected) ->
            assertEquals(type, expected, MetadataService.parseHtml("<head><meta property=\"og:type\" content=\"$type\"></head>", base).contentType)
        }
    }

    @Test fun emptyPageHasNoMetadata() {
        assertTrue(MetadataService.parseHtml("<html><head></head></html>", base).isEmpty)
    }

    @Test fun decodesEntitiesOnce() {
        assertEquals("Tom & Jerry 's <3 &lt;", MetadataService.decodeEntities("Tom &amp; Jerry &#39;s &lt;3 &amp;lt;"))
    }

    @Test fun readsOEmbedIncludingXPostText() {
        val youtube = MetadataService.parseOEmbed(
            """{"title":"Let's build GPT","author_name":"Andrej Karpathy","thumbnail_url":"https://i.ytimg.com/vi/x/hqdefault.jpg"}""",
        )
        assertEquals("Let's build GPT", youtube.title)
        assertEquals("Andrej Karpathy", youtube.author)
        assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg", youtube.imageUrl)

        val x = """{"author_name":"Jo","html":"<blockquote><p lang=\"en\">Charge from <a href=\"#\">day one</a>.</p>&mdash; Jo</blockquote>"}"""
        assertEquals("Charge from day one .", MetadataService.parseOEmbed(x).title)
    }

    @Test fun namesMapPlacesFromTheLinkItself() {
        assertEquals("Senso-ji Temple", MetadataService.placeName("https://maps.google.com/?q=Senso-ji+Temple"))
        assertEquals("Tsukiji Outer Market", MetadataService.placeName("https://www.google.com/maps/place/Tsukiji+Outer+Market/@35.66,139.77,17z"))
        assertNull(MetadataService.placeName("https://maps.app.goo.gl/AbC123"))
    }
}

class EnrichmentApplyTest {
    private val blogPost = Save(url = "https://blog.example.com/posts/42", source = Source.WEB, contentType = ContentType.ARTICLE)

    @Test fun successFillsGapsClassifiesAndNeverOverwrites() {
        val metadata = LinkMetadata(
            title = "Weeknight pasta recipe", description = "Dinner in 10 minutes",
            imageUrl = "https://blog.example.com/a.jpg", author = "Replaced?", contentType = ContentType.ARTICLE,
        )
        val save = Enricher.apply(Result.success(metadata), blogPost.copy(author = "Kept"))

        assertEquals("Weeknight pasta recipe", save.title)
        assertEquals("Kept", save.author)
        assertEquals(SaveStatus.ENRICHED, save.status)
        assertEquals(Category.FOOD, save.category)
    }

    @Test fun offlineStaysPendingAndUnavailableFailsButKeepsTheSave() {
        assertEquals(SaveStatus.PENDING, Enricher.apply(Result.failure(Offline()), blogPost).status)
        val failed = Enricher.apply(Result.failure(Unavailable()), blogPost)
        assertEquals(SaveStatus.FAILED, failed.status)
        assertEquals(blogPost.url, failed.url)
    }

    @Test fun aManualCategorySticksThroughEnrichment() {
        // Deliberately "Other": the case classification would otherwise touch.
        val save = Enricher.apply(Result.success(LinkMetadata(title = "Weeknight pasta recipe")), blogPost.withCategory(Category.OTHER))
        assertEquals(Category.OTHER, save.category)
        assertEquals("Weeknight pasta recipe", save.title)
    }

    @Test fun ogTypeRefinesOnlyWebSaves() {
        val video = LinkMetadata(title = "A", contentType = ContentType.VIDEO)
        assertEquals(ContentType.VIDEO, Enricher.apply(Result.success(video), blogPost).contentType)
        val reel = Save(url = "https://instagram.com/p/x", source = Source.INSTAGRAM, contentType = ContentType.POST)
        assertEquals(ContentType.POST, Enricher.apply(Result.success(video), reel).contentType)
    }
}

class ClassificationTest {
    private fun classify(
        title: String?, description: String? = null, tags: List<String> = emptyList(),
        url: String = "https://example.com/post", source: Source = Source.WEB, type: ContentType = ContentType.ARTICLE,
    ) = Classification.category(title, description, tags, url, source, type)

    @Test fun structureWinsOverWords() {
        assertEquals(Category.PLACES, classify("Best pasta in town", url = "https://maps.google.com/?q=x", source = Source.MAPS, type = ContentType.PLACE))
        assertEquals(Category.MUSIC, classify("Workout mix", url = "https://open.spotify.com/playlist/1", source = Source.SPOTIFY, type = ContentType.MUSIC))
        assertEquals(Category.SHOPPING, classify("Kettlebell", type = ContentType.PRODUCT))
    }

    @Test fun titles() {
        listOf(
            "Creamy cacio e pepe pasta in 10 minutes" to Category.FOOD,
            "Ricetta della carbonara" to Category.FOOD,
            "The Recommended Routine: a full beginner program" to Category.FITNESS,
            "Ice Lakes trail in 60 seconds" to Category.TRAVEL,
            "How to Get Startup Ideas" to Category.BUSINESS,
            "Let's build GPT: from scratch, in code" to Category.TECH,
            "Steve Jobs' 2005 Stanford Commencement Address" to Category.IDEAS,
            "Quantum physics explained" to Category.LEARNING,
            "The quiet power of slow mornings" to Category.OTHER,
        ).forEach { (title, expected) -> assertEquals(title, expected, classify(title)) }
    }

    @Test fun titleOutweighsBodyAndIgnoresCaseAndAccents() {
        assertEquals(Category.FOOD, classify("TRÈS BON RECIPE", description = "a founder cooks"))
    }

    @Test fun urlWordsHelpWhenThereIsNoTitle() {
        assertEquals(Category.FOOD, classify(null, url = "https://example.com/recipes/lemon-pasta"))
    }
}

class TitleCleaningTest {
    @Test fun stripsSiteNames() {
        listOf(
            Triple("Random Access Memories - Album by Daft Punk | Spotify", listOf("Spotify"), "Random Access Memories - Album by Daft Punk"),
            Triple("Cacio e pepe - Wikipedia", listOf("wikipedia"), "Cacio e pepe"),
            Triple("Reddit", listOf("reddit"), null),
            Triple("How to Get Startup Ideas", listOf("paulgraham"), "How to Get Startup Ideas"),
            Triple("Pipe | dream", emptyList(), "Pipe | dream"),
        ).forEach { (title, siteNames, expected) -> assertEquals(title, expected, MetadataService.cleanTitle(title, siteNames)) }
    }
}
