package com.g1lg1l.stash.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

enum class Source { INSTAGRAM, YOUTUBE, TIKTOK, REDDIT, X, SPOTIFY, MAPS, WEB, UNKNOWN }

enum class ContentType { VIDEO, ARTICLE, POST, PRODUCT, PLACE, MUSIC, OTHER }

enum class Category { FOOD, TRAVEL, FITNESS, IDEAS, BUSINESS, TECH, SHOPPING, MUSIC, LEARNING, PLACES, OTHER }

/** Metadata enrichment state. The URL is saved regardless; this never gates a save. */
enum class SaveStatus { PENDING, ENRICHED, FAILED }

/** The stored value, the same lowercase string as on iOS ("food", "youtube"). */
val Enum<*>.raw: String get() = name.lowercase()

@Entity(tableName = "saves", indices = [Index("canonicalUrl"), Index("lastSavedAt")])
data class Save(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** The URL exactly as it was shared. Source of truth. */
    val url: String,
    /** Dedupe key. Not unique in the schema, so it stays sync-friendly: [SaveDao.add] resolves duplicates. */
    val canonicalUrl: String = url,
    val source: Source = Source.UNKNOWN,
    val contentType: ContentType = ContentType.OTHER,
    val category: Category = Category.OTHER,
    val status: SaveStatus = SaveStatus.PENDING,
    /** Set when the user picks a category, so automatic classification never overrides it. */
    val categoryIsManual: Boolean = false,
    val title: String? = null,
    val descriptionText: String? = null,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val tags: List<String> = emptyList(),
    val summary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSavedAt: Long = createdAt,
    val openedAt: Long? = null,
    /** Local time of the last change made on this device, so sync knows what to push. Pulled changes leave it alone. */
    @ColumnInfo(defaultValue = "0") val modifiedAt: Long = 0,
)

/** A save deleted while signed in, until sync has told the server. */
@Entity(tableName = "tombstones")
data class Tombstone(@PrimaryKey val id: String, val deletedAt: Long)

val Source.displayName: String
    get() = when (this) {
        Source.INSTAGRAM -> "Instagram"
        Source.YOUTUBE -> "YouTube"
        Source.TIKTOK -> "TikTok"
        Source.REDDIT -> "Reddit"
        Source.X -> "X"
        Source.SPOTIFY -> "Spotify"
        Source.MAPS -> "Maps"
        Source.WEB -> "Web"
        Source.UNKNOWN -> "Link"
    }

val Category.displayName: String get() = raw.replaceFirstChar { it.uppercase() }

/** Plural, for filters: "Videos", "Places". */
val ContentType.displayName: String
    get() = when (this) {
        ContentType.VIDEO -> "Videos"
        ContentType.ARTICLE -> "Articles"
        ContentType.POST -> "Posts"
        ContentType.PRODUCT -> "Products"
        ContentType.PLACE -> "Places"
        ContentType.MUSIC -> "Music"
        ContentType.OTHER -> "Other"
    }

/** Never empty: falls back to the host, then the full URL. */
val Save.displayTitle: String
    get() = title?.takeIf { it.isNotEmpty() } ?: url.toHttpUrlOrNull()?.host?.removePrefix("www.") ?: url

/** Author when known, otherwise where it came from. */
val Save.byline: String get() = author ?: source.displayName

/** Text-first saves without an image get a typographic treatment instead of a placeholder picture. */
val Save.showsMedia: Boolean
    get() = thumbnailUrl != null || contentType !in setOf(ContentType.ARTICLE, ContentType.POST, ContentType.OTHER)

val Save.mediaAspectRatio: Float
    get() = when (contentType) {
        ContentType.VIDEO -> 16f / 9
        ContentType.ARTICLE -> 16f / 10
        ContentType.PRODUCT, ContentType.MUSIC -> 1f
        else -> 4f / 3
    }
