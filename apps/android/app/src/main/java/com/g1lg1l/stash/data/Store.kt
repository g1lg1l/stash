package com.g1lg1l.stash.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.glance.appwidget.updateAll
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import coil3.SingletonImageLoader
import com.g1lg1l.stash.widget.StashWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Enums are stored as the same lowercase strings as on iOS. Unknown values fall back, as they do there. */
class Converters {
    @TypeConverter fun fromSource(value: Source) = value.raw
    @TypeConverter fun toSource(raw: String) = Source.entries.find { it.raw == raw } ?: Source.UNKNOWN
    @TypeConverter fun fromContentType(value: ContentType) = value.raw
    @TypeConverter fun toContentType(raw: String) = ContentType.entries.find { it.raw == raw } ?: ContentType.OTHER
    @TypeConverter fun fromCategory(value: Category) = value.raw
    @TypeConverter fun toCategory(raw: String) = Category.entries.find { it.raw == raw } ?: Category.OTHER
    @TypeConverter fun fromStatus(value: SaveStatus) = value.raw
    @TypeConverter fun toStatus(raw: String) = SaveStatus.entries.find { it.raw == raw } ?: SaveStatus.PENDING
    @TypeConverter fun fromTags(tags: List<String>) = tags.joinToString("\n")
    @TypeConverter fun toTags(raw: String) = raw.split("\n").filter { it.isNotEmpty() }
}

@Dao
abstract class SaveDao {
    @Query("SELECT * FROM saves ORDER BY lastSavedAt DESC")
    abstract fun all(): Flow<List<Save>>

    @Query("SELECT * FROM saves WHERE id = :id")
    abstract suspend fun get(id: String): Save?

    @Query("SELECT * FROM saves WHERE canonicalUrl = :key LIMIT 1")
    abstract suspend fun withCanonicalUrl(key: String): Save?

    @Query("SELECT * FROM saves WHERE status IN (:statuses) ORDER BY lastSavedAt DESC")
    abstract suspend fun withStatus(statuses: List<String>): List<Save>

    @Upsert abstract suspend fun upsert(save: Save)

    @Upsert abstract suspend fun upsert(saves: List<Save>)

    @Delete abstract suspend fun delete(save: Save)

    @Query("DELETE FROM saves WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<String>)

    @Query("SELECT * FROM saves WHERE modifiedAt > :since")
    abstract suspend fun modifiedSince(since: Long): List<Save>

    @Query("SELECT * FROM saves WHERE id IN (:ids) OR canonicalUrl IN (:canonicalUrls)")
    abstract suspend fun matching(ids: List<String>, canonicalUrls: List<String>): List<Save>

    @Query("SELECT * FROM tombstones")
    abstract suspend fun tombstones(): List<Tombstone>

    @Upsert abstract suspend fun addTombstones(tombstones: List<Tombstone>)

    @Query("DELETE FROM tombstones WHERE id IN (:ids)")
    abstract suspend fun deleteTombstones(ids: List<String>)

    /** Stashes a link, or bumps `lastSavedAt` when the same resource is already there. Never needs the network. */
    @Transaction
    open suspend fun add(analysis: UrlAnalysis, at: Long): Save {
        val existing = withCanonicalUrl(analysis.canonicalUrl)
        val save = if (existing != null) {
            // Order-independent: the earliest share is when it was first stashed.
            existing.copy(
                createdAt = minOf(existing.createdAt, at), lastSavedAt = maxOf(existing.lastSavedAt, at),
                modifiedAt = System.currentTimeMillis(),
            )
        } else {
            val new = Save(
                url = analysis.url, canonicalUrl = analysis.canonicalUrl,
                source = analysis.source, contentType = analysis.contentType, createdAt = at,
            )
            // First guess from the link alone (maps, Spotify, products); refined once metadata arrives.
            new.copy(category = Classification.category(new), modifiedAt = System.currentTimeMillis())
        }
        upsert(save)
        return save
    }

    /**
     * Changes the row as it is now, so a background write never undoes an edit made meanwhile.
     * A real change is marked for sync; one that changes nothing (enrichment while offline) isn't.
     */
    @Transaction
    open suspend fun update(id: String, change: (Save) -> Save) {
        val save = get(id) ?: return
        val changed = change(save)
        if (changed != save) upsert(changed.copy(modifiedAt = System.currentTimeMillis()))
    }

    /** Signed in, a tombstone remembers the delete until sync tells the server. */
    @Transaction
    open suspend fun delete(save: Save, tombstone: Boolean) {
        delete(save)
        if (tombstone) addTombstones(listOf(Tombstone(save.id, System.currentTimeMillis())))
    }

    /** Undo: back as it was, and pushed again, which also clears a delete that already reached the server. */
    @Transaction
    open suspend fun restore(save: Save) {
        deleteTombstones(listOf(save.id))
        upsert(save.copy(modifiedAt = System.currentTimeMillis()))
    }

    /** One page from the server, against the rows as they are now. */
    @Transaction
    open suspend fun applyPull(rows: List<RemoteSave>, pushStart: Long) {
        val local = matching(rows.map { it.save.id }, rows.map { it.save.canonicalUrl })
        val changes = pullChanges(rows, local, pushStart, System.currentTimeMillis())
        delete(changes.deletes)
        upsert(changes.upserts)
        addTombstones(changes.tombstones)
    }
}

@Database(entities = [Save::class, Tombstone::class], version = 2, autoMigrations = [AutoMigration(from = 1, to = 2)])
@TypeConverters(Converters::class)
abstract class StashDatabase : RoomDatabase() {
    abstract fun saves(): SaveDao
}

/** The user's choice: sticks, whatever classification later thinks. */
fun Save.withCategory(category: Category) = copy(category = category, categoryIsManual = true)

/** Seen = opened. Unseen saves are what rediscovery resurfaces. */
fun Save.seen(now: Long = System.currentTimeMillis()) = copy(openedAt = openedAt ?: now)

/**
 * The single store and the work around it. The share sheet runs in the app's own process,
 * so everything writes here directly: no hand-off inbox like the iOS extensions need.
 */
@SuppressLint("StaticFieldLeak") // Only ever the application context, which lives as long as the process.
object Stash {
    private lateinit var app: Context
    lateinit var dao: SaveDao
        private set

    /** Every save, newest first. Null until the first read lands, so screens don't flash an empty state. */
    lateinit var saves: StateFlow<List<Save>?>
        private set

    /** The `sampleData` store (debug): in memory, never enriched, so screenshots stay put. */
    var isSample = false
        private set

    /** Work that outlives a screen: saving from the share sheet, enrichment, edits, undo. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var retriedFailed = false

    fun open(context: Context, sample: Boolean = false) {
        app = context.applicationContext
        val database = if (sample) {
            Room.inMemoryDatabaseBuilder(app, StashDatabase::class.java)
        } else {
            Room.databaseBuilder(app, StashDatabase::class.java, "stash.db")
        }
        dao = database.build().saves()
        isSample = sample
        // Seeded before anyone reads, so the first frame already has them.
        if (sample) runBlocking(Dispatchers.IO) { dao.upsert(SampleData.saves()) }
        saves = dao.all().stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** Syncs and fills in metadata for new saves, side by side, then refreshes the widget. Cheap to call often. */
    suspend fun refresh(retryFailed: Boolean = false) {
        if (isSample) return
        // Pages that had nothing get one more try per launch.
        val retry = retryFailed || !retriedFailed
        retriedFailed = true
        coroutineScope {
            launch { Sync.sync() }
            Enricher.enrichPending(dao, retry) { app.getSystemService<ConnectivityManager>()?.activeNetwork != null }
        }
        updateWidget()
    }

    suspend fun updateWidget() = StashWidget().updateAll(app)

    fun edit(save: Save, change: (Save) -> Save) {
        scope.launch { dao.update(save.id, change) }
    }

    /** Removes the save; signed in, the other devices hear about it on the next sync. The thumbnail goes separately. */
    suspend fun delete(save: Save) = dao.delete(save, tombstone = Account.signedIn)

    fun forgetThumbnail(save: Save) {
        val url = save.thumbnailUrl ?: return
        val images = SingletonImageLoader.get(app)
        images.diskCache?.remove(url)
        images.memoryCache?.let { cache -> cache.keys.filter { it.key == url }.forEach(cache::remove) }
    }
}
