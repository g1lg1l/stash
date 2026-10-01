package com.g1lg1l.stash.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** What a page says about itself. Every field is optional; a save is complete without any of them. */
data class LinkMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val author: String? = null,
    val contentType: ContentType? = null,
) {
    val isEmpty: Boolean get() = title == null && description == null && imageUrl == null && author == null

    fun merge(other: LinkMetadata) = LinkMetadata(
        title ?: other.title, description ?: other.description, imageUrl ?: other.imageUrl,
        author ?: other.author, contentType ?: other.contentType,
    )
}

/** No connection or it timed out: worth retrying soon. */
class Offline : Exception()

/** The page answered but had nothing usable. */
class Unavailable : Exception()

object MetadataService {
    private const val MAX_PAGE_BYTES = 1_000_000L

    // A normal mobile Chrome identity, marked as Stash, like in-app browsers do.
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36 Stash/1.0"

    /** Also loads thumbnails: some hosts (Wikimedia, image CDNs) refuse OkHttp's default identity. */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build(),
                )
            }
            .build()
    }

    /** Throws [IOException] when the network fails and [Unavailable] when the page had nothing. */
    suspend fun fetch(url: String, source: Source): LinkMetadata = withContext(Dispatchers.IO) {
        var metadata = oEmbedEndpoint(url, source)?.let { endpoint ->
            try {
                parseOEmbed(get(endpoint).second)
            } catch (_: Exception) {
                null
            }
        } ?: LinkMetadata()
        if (metadata.imageUrl == null || metadata.title == null) {
            val (finalUrl, page) = get(url)
            metadata = metadata.merge(parseHtml(page, finalUrl))
        }
        val title = metadata.title
        if (source == Source.MAPS && (title == null || title.contains("google maps", ignoreCase = true))) {
            metadata = metadata.copy(title = placeName(url))
        }
        if (metadata.isEmpty) throw Unavailable()
        metadata
    }

    /** The final URL after redirects, and the start of the body. */
    private fun get(url: String): Pair<String, String> {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body?.takeIf { response.isSuccessful }?.source() ?: throw Unavailable()
            body.request(MAX_PAGE_BYTES)
            val bytes = body.buffer.readByteArray(minOf(body.buffer.size, MAX_PAGE_BYTES))
            return response.request.url.toString() to bytes.decodeToString()
        }
    }

    // oEmbed (official, keyless endpoints)

    private fun oEmbedEndpoint(url: String, source: Source): String? {
        val base = when (source) {
            Source.YOUTUBE -> "https://www.youtube.com/oembed?format=json&url="
            Source.TIKTOK -> "https://www.tiktok.com/oembed?url="
            Source.X -> "https://publish.twitter.com/oembed?omit_script=1&url="
            // Its pages bounce browsers to an open-in-the-app link with no metadata.
            Source.SPOTIFY -> "https://open.spotify.com/oembed?url="
            else -> return null
        }
        return base + URLEncoder.encode(url, "UTF-8")
    }

    fun parseOEmbed(json: String): LinkMetadata {
        val oEmbed = JSONObject(json)
        fun string(key: String) = if (oEmbed.isNull(key)) null else oEmbed.optString(key).nullIfBlank()
        // X returns no title, only the post as HTML; its text is the best title there is.
        val title = string("title") ?: string("html")?.let(::firstParagraph)
        return LinkMetadata(title = title, imageUrl = string("thumbnail_url"), author = string("author_name"))
    }

    // HTML (Open Graph, Twitter Cards, plain tags)

    private val metaTag = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)
    private val titleTag = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("""([a-zA-Z:_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    fun parseHtml(html: String, baseUrl: String): LinkMetadata {
        val headEnd = html.indexOf("</head>", ignoreCase = true)
        val head = if (headEnd >= 0) html.substring(0, headEnd) else html
        val tags = mutableMapOf<String, String>()
        for (match in metaTag.findAll(head)) {
            val attributes = attributes(match.value)
            val key = (attributes["property"] ?: attributes["name"] ?: attributes["itemprop"])?.lowercase() ?: continue
            val content = attributes["content"]?.let(::decodeEntities)?.nullIfBlank() ?: continue
            tags.putIfAbsent(key, content)
        }
        val title = tags["og:title"] ?: tags["twitter:title"]
            ?: titleTag.find(head)?.groupValues?.get(1)?.let(::decodeEntities)?.nullIfBlank()
        val base = baseUrl.toHttpUrlOrNull()
        val image = (tags["og:image:secure_url"] ?: tags["og:image"] ?: tags["og:image:url"]
            ?: tags["twitter:image"] ?: tags["twitter:image:src"])?.let { base?.resolve(it)?.toString() }
        // "Album by Daft Punk | Spotify", "Cacio e pepe - Wikipedia": the site name adds nothing.
        val siteNames = listOfNotNull(tags["og:site_name"], base?.host?.split('.')?.dropLast(1)?.lastOrNull())
        return LinkMetadata(
            title = title?.let { cleanTitle(it, siteNames) },
            description = tags["og:description"] ?: tags["twitter:description"] ?: tags["description"],
            imageUrl = image,
            author = tags["author"] ?: tags["article:author"]?.takeUnless { it.startsWith("http") }
                ?: tags["music:musician_description"],
            contentType = tags["og:type"]?.let(::contentType),
        )
    }

    /** Strips a trailing " | Site" / " - Site"; a title that is only the site name is no title. */
    fun cleanTitle(title: String, siteNames: List<String>): String? {
        var result = title
        for (name in siteNames.filter { it.isNotEmpty() }) {
            if (result.equals(name, ignoreCase = true)) return null
            for (separator in listOf(" | ", " - ", " – ", " — ", " · ")) {
                if (result.endsWith(separator + name, ignoreCase = true)) result = result.dropLast(separator.length + name.length)
            }
        }
        return result.nullIfBlank()
    }

    private fun contentType(openGraphType: String): ContentType? {
        val type = openGraphType.lowercase()
        return when {
            type.startsWith("video") -> ContentType.VIDEO
            type.startsWith("music") -> ContentType.MUSIC
            type.startsWith("article") || type == "blog" -> ContentType.ARTICLE
            "product" in type -> ContentType.PRODUCT
            "place" in type || type.startsWith("business") || "restaurant" in type -> ContentType.PLACE
            else -> null
        }
    }

    private fun attributes(tag: String): Map<String, String> =
        attribute.findAll(tag).associate { m -> m.groupValues[1].lowercase() to m.groupValues[2].ifEmpty { m.groupValues[3] } }

    private val paragraph = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
    private val anyTag = Regex("<[^>]+>")
    private val whitespace = Regex("\\s+")

    private fun firstParagraph(html: String): String? {
        val inner = paragraph.find(html)?.groupValues?.get(1) ?: return null
        val text = inner.replace(anyTag, " ").replace(whitespace, " ")
        return decodeEntities(text).nullIfBlank()?.let { if (it.length > 140) it.take(139) + "…" else it }
    }

    private val namedEntities = mapOf(
        "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">", "&nbsp;" to " ",
    )
    private val numericEntity = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")

    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        var result = namedEntities.entries.fold(text) { acc, (entity, character) -> acc.replace(entity, character) }
        result = numericEntity.replace(result) { match ->
            val value = match.groupValues[1]
            val codePoint = if (value.startsWith("x")) value.drop(1).toIntOrNull(16) else value.toIntOrNull()
            codePoint?.takeIf { Character.isValidCodePoint(it) && it !in 0xD800..0xDFFF }
                ?.let { String(Character.toChars(it)) } ?: match.value
        }
        return result.replace("&amp;", "&") // Last, so "&amp;lt;" stays "&lt;".
    }

    /** "maps.google.com/?q=Senso-ji" or ".../maps/place/Senso-ji/@35.7,139.7" → "Senso-ji". Works offline. */
    fun placeName(url: String): String? {
        val link = url.toHttpUrlOrNull() ?: return null
        (link.queryParameter("q") ?: link.queryParameter("query"))?.nullIfBlank()?.let { return it.replace('+', ' ') }
        val parts = link.pathSegments
        val index = parts.indexOf("place")
        return if (index >= 0) parts.getOrNull(index + 1)?.replace('+', ' ')?.nullIfBlank() else null
    }
}

fun String.nullIfBlank(): String? = trim().ifEmpty { null }

/** Fills in pending saves in the background. Each result is applied to the row as it is then, never a stale copy. */
object Enricher {
    private val running = AtomicBoolean(false)

    /** [retryFailed] once per launch, so pages that had nothing get another chance without hammering them. */
    suspend fun enrichPending(dao: SaveDao, retryFailed: Boolean, isOnline: () -> Boolean) {
        if (!running.compareAndSet(false, true)) return
        try {
            val statuses = listOfNotNull(SaveStatus.PENDING, SaveStatus.FAILED.takeIf { retryFailed })
            val slots = Semaphore(4) // A few at a time is plenty and polite.
            coroutineScope {
                for (save in dao.withStatus(statuses.map { it.raw })) launch {
                    val result = slots.withPermit { fetch(save, isOnline) }
                    dao.update(save.id) { apply(result, it) }
                }
            }
        } finally {
            running.set(false)
        }
    }

    private suspend fun fetch(save: Save, isOnline: () -> Boolean): Result<LinkMetadata> = try {
        Result.success(MetadataService.fetch(save.url, save.source))
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.failure(if (e is InterruptedIOException || !isOnline()) Offline() else e)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun apply(result: Result<LinkMetadata>, save: Save): Save {
        val updated = result.fold(
            // Fill gaps only: never overwrite what's already there.
            onSuccess = { metadata ->
                save.copy(
                    title = save.title ?: metadata.title,
                    descriptionText = save.descriptionText ?: metadata.description,
                    thumbnailUrl = save.thumbnailUrl ?: metadata.imageUrl,
                    author = save.author ?: metadata.author,
                    contentType = metadata.contentType?.takeIf { save.source == Source.WEB } ?: save.contentType,
                    status = SaveStatus.ENRICHED,
                )
            },
            // Offline stays pending; the next foreground retries.
            onFailure = { if (it is Offline) save else save.copy(status = SaveStatus.FAILED) },
        )
        // Classify with whatever we now know. Only unclassified saves, so a category is never taken away.
        return if (updated.category == Category.OTHER && !updated.categoryIsManual) {
            updated.copy(category = Classification.category(updated))
        } else updated
    }
}
