package com.g1lg1l.stash.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What a shared link is, judged from the URL alone. No network, so it works offline and in the share sheet. */
data class UrlAnalysis(
    /** The link as shared, only trimmed and given a scheme if it had none. Source of truth. */
    val url: String,
    /** Dedupe key. Two links share it only when they point at the same resource. */
    val canonicalUrl: String,
    val source: Source,
    val contentType: ContentType,
)

object UrlSourceDetector {
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /** Accepts any http(s) link, with or without a scheme ("youtu.be/abc"). Null only for things that aren't web links. */
    fun analyze(string: String): UrlAnalysis? {
        val trimmed = string.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = when (scheme.find(trimmed)?.value?.dropLast(1)?.lowercase()) {
            null -> if ('@' in trimmed) return null else "https://$trimmed" // "youtu.be/abc"; a bare "a@b.com" is an email
            "http", "https" -> trimmed
            else -> return null // mailto:, tel:, spotify:
        }
        val url = withScheme.toHttpUrlOrNull() ?: return null
        val host = url.host
        if ('.' !in host || host.startsWith('.') || host.endsWith('.')) return null

        val bareHost = host.removeFirstPrefix("www.", "m.", "mobile.")
        val source = source(bareHost, url.encodedPath)
        return UrlAnalysis(
            url = url.toString(),
            canonicalUrl = canonicalKey(url, bareHost, source),
            source = source,
            contentType = contentType(source, bareHost, url.encodedPath.lowercase()),
        )
    }

    private val link = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /** First http(s) link inside shared text ("Look at this 😍 https://…"). Emails and phone numbers don't count. */
    fun firstLink(text: String): String? =
        link.findAll(text).map { trimTrailingPunctuation(it.value) }.firstOrNull { analyze(it) != null }

    /**
     * The link in whatever another app shared: text with a link in it ("Look at this https://…"),
     * or a bare link without a scheme ("youtu.be/abc").
     */
    fun sharedLink(texts: List<String>): String? =
        texts.firstNotNullOfOrNull(::firstLink) ?: texts.map { it.trim() }.firstOrNull { analyze(it) != null }

    // ponytail: trailing-punctuation heuristic like most linkifiers; use a real URL tokenizer if links come out clipped.
    private fun trimTrailingPunctuation(link: String): String {
        var end = link.length
        while (end > 0) {
            val c = link[end - 1]
            val unbalancedParen = c == ')' && link.take(end).count { it == '(' } < link.take(end).count { it == ')' }
            if (c in ".,;:!?'\"”’»]}" || unbalancedParen) end-- else break
        }
        return link.take(end)
    }

    // Source

    private fun source(host: String, path: String): Source {
        fun matches(vararg domains: String) = domains.any { host == it || host.endsWith(".$it") }
        return when {
            matches("instagram.com", "instagr.am") -> Source.INSTAGRAM
            matches("youtube.com", "youtu.be", "youtube-nocookie.com") -> Source.YOUTUBE
            matches("tiktok.com") -> Source.TIKTOK
            matches("reddit.com", "redd.it") -> Source.REDDIT
            matches("x.com", "twitter.com") -> Source.X
            matches("spotify.com", "spotify.link") -> Source.SPOTIFY
            matches("maps.app.goo.gl", "maps.apple.com", "maps.google.com")
                || (matches("google.com") && path.startsWith("/maps"))
                || (host == "goo.gl" && path.startsWith("/maps")) -> Source.MAPS
            else -> Source.WEB
        }
    }

    // Content type

    private fun contentType(source: Source, host: String, path: String): ContentType = when (source) {
        Source.YOUTUBE -> if (host.startsWith("music.")) ContentType.MUSIC else ContentType.VIDEO
        Source.TIKTOK -> ContentType.VIDEO
        Source.INSTAGRAM -> if (listOf("/reel/", "/reels/", "/tv/").any { it in path }) ContentType.VIDEO else ContentType.POST
        Source.REDDIT, Source.X -> ContentType.POST
        Source.SPOTIFY -> ContentType.MUSIC
        Source.MAPS -> ContentType.PLACE
        // Metadata refines this later (og:type); the URL only gives strong hints.
        Source.WEB, Source.UNKNOWN -> when {
            listOf("/product/", "/products/", "/dp/", "/gp/product/", "/item/", "/itm/").any { it in path } -> ContentType.PRODUCT
            path.isEmpty() || path == "/" -> ContentType.OTHER
            else -> ContentType.ARTICLE
        }
    }

    // Canonical key

    /** Query items that never identify a resource. */
    private val trackingItems = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid", "igsh", "igshid", "_ga", "ref_src", "ref_url",
    )

    private fun canonicalKey(url: HttpUrl, bareHost: String, source: Source): String {
        var host = bareHost
        var path = url.encodedPath
        var query = (0 until url.querySize).map { url.queryParameterName(it) to url.queryParameterValue(it) }
        val parts = url.pathSegments.filter { it.isNotEmpty() }

        when (source) {
            Source.YOUTUBE -> {
                // youtu.be/ID, /shorts/ID, /embed/ID, /live/ID and /watch?v=ID are the same video.
                val id = when {
                    bareHost == "youtu.be" -> parts.firstOrNull()
                    parts.size >= 2 && parts[0] in setOf("shorts", "embed", "live", "v") -> parts[1]
                    parts.firstOrNull() == "watch" -> query.firstOrNull { it.first == "v" }?.second
                    else -> null
                }
                if (id != null) {
                    host = if (bareHost.startsWith("music.")) bareHost else "youtube.com"
                    path = "/watch"
                    query = listOf("v" to id)
                } else {
                    if (bareHost == "youtu.be") host = "youtube.com"
                    query = query.filter { it.first == "list" } // Playlists are identified by list=.
                }
            }
            Source.X -> {
                host = "x.com"
                // x.com/<anyone>/status/ID resolves to the same post whatever the handle.
                if (parts.size >= 3 && parts[1] == "status") path = "/i/status/${parts[2]}"
                query = emptyList()
            }
            Source.REDDIT -> {
                if (bareHost != "redd.it") host = "reddit.com" // old., new., np. are the same post
                query = emptyList()
            }
            // Their query strings are share and tracking tokens only.
            Source.INSTAGRAM, Source.TIKTOK, Source.SPOTIFY -> query = emptyList()
            Source.MAPS, Source.WEB, Source.UNKNOWN -> query = query.filter {
                val name = it.first.lowercase()
                !name.startsWith("utm_") && name !in trackingItems
            }
        }

        if (path.length > 1 && path.endsWith("/")) path = path.dropLast(1)
        val key = HttpUrl.Builder().scheme("https").host(host).encodedPath(path)
        if (url.port != HttpUrl.defaultPort(url.scheme)) key.port(url.port)
        query.sortedWith(compareBy({ it.first }, { it.second.orEmpty() })).forEach { key.addQueryParameter(it.first, it.second) }
        // Plain anchors (#section) are the same page; hash routes (#/page, #!/page) are not.
        url.fragment?.takeIf { it.startsWith("/") || it.startsWith("!") }?.let { key.fragment(it) }
        return key.build().toString()
    }

    private fun String.removeFirstPrefix(vararg prefixes: String): String =
        prefixes.firstOrNull { startsWith(it) }?.let { removePrefix(it) } ?: this
}
