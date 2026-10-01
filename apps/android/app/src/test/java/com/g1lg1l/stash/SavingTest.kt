package com.g1lg1l.stash

import com.g1lg1l.stash.data.ContentType
import com.g1lg1l.stash.data.Source
import com.g1lg1l.stash.data.UrlAnalysis
import com.g1lg1l.stash.data.UrlSourceDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlSourceDetectorTest {
    private fun analyze(url: String): UrlAnalysis = requireNotNull(UrlSourceDetector.analyze(url)) { url }

    @Test fun detectsSourceAndType() {
        listOf(
            Triple("https://www.instagram.com/reel/C8x2kLmN4pQ/?igsh=abc", Source.INSTAGRAM, ContentType.VIDEO),
            Triple("https://instagram.com/p/C8x2kLmN4pQ/", Source.INSTAGRAM, ContentType.POST),
            Triple("https://www.youtube.com/watch?v=kCc8FmEb1nY", Source.YOUTUBE, ContentType.VIDEO),
            Triple("https://youtu.be/kCc8FmEb1nY?si=xyz", Source.YOUTUBE, ContentType.VIDEO),
            Triple("https://youtube.com/shorts/Qm3vX8pLw2E", Source.YOUTUBE, ContentType.VIDEO),
            Triple("https://music.youtube.com/watch?v=abc", Source.YOUTUBE, ContentType.MUSIC),
            Triple("https://www.tiktok.com/@chef/video/7401928374650182913", Source.TIKTOK, ContentType.VIDEO),
            Triple("https://vm.tiktok.com/ZMabc123/", Source.TIKTOK, ContentType.VIDEO),
            Triple("https://old.reddit.com/r/swift/comments/abc/title/", Source.REDDIT, ContentType.POST),
            Triple("https://x.com/jack/status/20", Source.X, ContentType.POST),
            Triple("https://mobile.twitter.com/jack/status/20", Source.X, ContentType.POST),
            Triple("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=1", Source.SPOTIFY, ContentType.MUSIC),
            Triple("https://maps.google.com/?q=Senso-ji", Source.MAPS, ContentType.PLACE),
            Triple("https://www.google.com/maps/place/Senso-ji", Source.MAPS, ContentType.PLACE),
            Triple("https://maps.app.goo.gl/AbC123", Source.MAPS, ContentType.PLACE),
            Triple("https://maps.apple.com/?q=Kyoto", Source.MAPS, ContentType.PLACE),
            Triple("https://paulgraham.com/startupideas.html", Source.WEB, ContentType.ARTICLE),
            Triple("https://www.amazon.com/dp/B0CHX1W1XY", Source.WEB, ContentType.PRODUCT),
            Triple("https://example.com", Source.WEB, ContentType.OTHER),
        ).forEach { (url, source, type) ->
            val analysis = analyze(url)
            assertEquals(url, source, analysis.source)
            assertEquals(url, type, analysis.contentType)
        }
    }

    @Test fun acceptsLinksWithoutScheme() {
        val analysis = analyze("  youtu.be/kCc8FmEb1nY\n")
        assertEquals("https://youtu.be/kCc8FmEb1nY", analysis.url)
        assertEquals(Source.YOUTUBE, analysis.source)
    }

    @Test fun rejectsNonWebLinks() {
        listOf(
            "", "not a url", "mailto:hi@example.com", "tel:+391234", "spotify:track:123",
            "hi@example.com", "ftp://example.com/file", "localhost", "https://", "https://.com",
        ).forEach { assertNull(it, UrlSourceDetector.analyze(it)) }
    }

    @Test fun keepsTheSharedUrlAsSourceOfTruth() {
        val shared = "https://www.youtube.com/watch?v=kCc8FmEb1nY&t=42s&si=abc"
        assertEquals(shared, analyze(shared).url)
    }

    @Test fun sameResourceSharesOneKey() {
        listOf(
            // Same video however it's linked.
            listOf(
                "https://www.youtube.com/watch?v=kCc8FmEb1nY", "https://youtu.be/kCc8FmEb1nY?si=abc",
                "https://m.youtube.com/watch?v=kCc8FmEb1nY&t=42s", "https://youtube.com/shorts/kCc8FmEb1nY",
                "http://youtube.com/embed/kCc8FmEb1nY",
            ),
            // Same post, any handle, any host alias, share tokens dropped.
            listOf("https://x.com/jack/status/20", "https://twitter.com/someone/status/20?s=20&t=abc"),
            listOf("https://www.instagram.com/reel/C8x/?igsh=1", "https://instagram.com/reel/C8x"),
            listOf("https://www.reddit.com/r/swift/comments/abc/t/", "https://old.reddit.com/r/swift/comments/abc/t?share_id=9"),
            // Tracking parameters, anchors, trailing slashes, www and http don't make a new page.
            listOf(
                "https://example.com/post?utm_source=x&utm_medium=y", "http://www.example.com/post/#comments",
                "https://example.com/post?fbclid=123",
            ),
            listOf("https://shop.example.com/item?id=5&color=red", "https://shop.example.com/item?color=red&id=5&gclid=1"),
        ).forEach { urls ->
            val keys = urls.map { analyze(it).canonicalUrl }.toSet()
            assertEquals(keys.toString(), 1, keys.size)
        }
    }

    @Test fun differentResourcesNeverMerge() {
        listOf(
            "https://www.youtube.com/watch?v=aaa" to "https://www.youtube.com/watch?v=bbb",
            "https://www.youtube.com/playlist?list=PL1" to "https://www.youtube.com/playlist?list=PL2",
            "https://shop.example.com/item?id=5" to "https://shop.example.com/item?id=6",
            "https://example.com/Page" to "https://example.com/page",
            "https://app.example.com/#/inbox" to "https://app.example.com/#/settings",
            "https://example.com/post" to "https://blog.example.com/post",
            "https://instagram.com/p/C8x" to "https://instagram.com/reel/C8x",
        ).forEach { (a, b) -> assertNotEquals("$a vs $b", analyze(a).canonicalUrl, analyze(b).canonicalUrl) }
    }
}

class SharedTextLinkTest {
    @Test fun findsTheLinkInsideSharedText() {
        val text = "Check this out 😍 https://www.instagram.com/reel/C8x2kLmN4pQ/?igsh=abc via @friend"
        assertEquals("https://www.instagram.com/reel/C8x2kLmN4pQ/?igsh=abc", UrlSourceDetector.firstLink(text))
    }

    @Test fun ignoresTextWithoutWebLinks() {
        assertNull(UrlSourceDetector.firstLink("call me at +39 333 1234567 or hi@example.com"))
    }

    @Test fun dropsSentencePunctuationButKeepsBalancedParentheses() {
        assertEquals("https://example.com/a", UrlSourceDetector.firstLink("Read this: https://example.com/a."))
        assertEquals(
            "https://en.wikipedia.org/wiki/Mole_(animal)",
            UrlSourceDetector.firstLink("(see https://en.wikipedia.org/wiki/Mole_(animal))"),
        )
    }

    @Test fun takesABareLinkWhenTheTextHasNoScheme() {
        assertEquals("youtu.be/kCc8FmEb1nY", UrlSourceDetector.sharedLink(listOf(" youtu.be/kCc8FmEb1nY ")))
        assertEquals(
            "https://youtu.be/abc",
            UrlSourceDetector.sharedLink(listOf("Look at this", "Great video https://youtu.be/abc")),
        )
        assertNull(UrlSourceDetector.sharedLink(listOf("no links here")))
    }
}
