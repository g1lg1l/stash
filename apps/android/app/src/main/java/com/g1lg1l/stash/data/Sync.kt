package com.g1lg1l.stash.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.g1lg1l.stash.ui.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/** A row from the server: the save as it is there (with [Save.modifiedAt] 0), and whether it was deleted. */
data class RemoteSave(val save: Save, val deleted: Boolean)

/** What one pulled page changes on this device. */
data class PullChanges(val upserts: List<Save>, val deletes: List<String>, val tombstones: List<Tombstone>)

/**
 * The pull rules, against the local saves that share an id or a canonical URL with the page.
 * [pushStart] is when this sync started: a save changed after that is skipped, and the next push wins.
 */
fun pullChanges(rows: List<RemoteSave>, local: List<Save>, pushStart: Long, now: Long): PullChanges {
    val saves = local.associateBy { it.id }.toMutableMap()
    val upserts = mutableMapOf<String, Save>()
    val deletes = mutableSetOf<String>()
    val tombstones = mutableListOf<Tombstone>()
    fun put(save: Save) {
        saves[save.id] = save
        upserts[save.id] = save
    }
    fun remove(id: String) {
        saves.remove(id)
        upserts.remove(id)
        deletes += id
    }

    for ((server, deleted) in rows) {
        val existing = saves[server.id]
        when {
            existing != null && existing.modifiedAt > pushStart -> Unit
            deleted -> if (existing != null) remove(server.id)
            // The server's fields, but not marked as changed here, so it isn't pushed back.
            existing != null -> put(server.copy(modifiedAt = existing.modifiedAt))
            else -> {
                val twin = saves.values.find { it.canonicalUrl == server.canonicalUrl }
                if (twin == null) {
                    put(server.copy(modifiedAt = 0))
                } else {
                    // The same link saved on two devices. Every device keeps the id that sorts first,
                    // or two devices pulling each other's copy would each tombstone their own.
                    val keepTwin = twin.id.lowercase() < server.id.lowercase()
                    if (!keepTwin) remove(twin.id)
                    tombstones += Tombstone(if (keepTwin) server.id else twin.id, now)
                    put(merge(server, twin).copy(id = if (keepTwin) twin.id else server.id, modifiedAt = now))
                }
            }
        }
    }
    return PullChanges(upserts.values.toList(), deletes.toList(), tombstones)
}

/** As [SaveDao.add] merges a re-save: the server's metadata, with local values filling its gaps. */
private fun merge(server: Save, local: Save): Save {
    val localWins = !server.categoryIsManual && (local.categoryIsManual || server.category == Category.OTHER)
    return server.copy(
        createdAt = minOf(server.createdAt, local.createdAt),
        lastSavedAt = maxOf(server.lastSavedAt, local.lastSavedAt),
        openedAt = server.openedAt ?: local.openedAt,
        category = if (localWins) local.category else server.category,
        categoryIsManual = server.categoryIsManual || local.categoryIsManual,
        status = if (local.status == SaveStatus.ENRICHED) local.status else server.status,
        title = server.title ?: local.title,
        descriptionText = server.descriptionText ?: local.descriptionText,
        thumbnailUrl = server.thumbnailUrl ?: local.thumbnailUrl,
        author = server.author ?: local.author,
        tags = server.tags.ifEmpty { local.tags },
        summary = server.summary ?: local.summary,
    )
}

private const val PAGE = 500

/** The cursor goes through the query encoder: its "+00:00" must reach the server as "%2B00:00". */
fun pullUrl(base: HttpUrl, cursor: String?): HttpUrl = base.newBuilder()
    .addPathSegments("rest/v1/saves")
    .addQueryParameter("select", "*")
    .apply { if (cursor != null) addQueryParameter("updated_at", "gt.$cursor") }
    .addQueryParameter("order", "updated_at.asc,id.asc")
    .addQueryParameter("limit", "$PAGE")
    .build()

/** UTC with milliseconds when sent; the server answers microseconds and "+00:00", which this reads too. */
private val isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC)

fun isoDate(millis: Long): String = isoFormat.format(Instant.ofEpochMilli(millis))

fun parseIsoDate(text: String): Long = OffsetDateTime.parse(text).toInstant().toEpochMilli()

/** A 4xx or 5xx, with the server's own message to show. */
class ServerError(val code: Int, message: String) : Exception(message)

/**
 * The optional account, on Supabase: plain HTTPS through OkHttp, no SDK. The key is public by design,
 * row level security protects the data. Signed in while there's a refresh token in [Prefs].
 */
object Account {
    val base = "https://kzgmhvqbvrylwydqskcm.supabase.co".toHttpUrl()
    private const val KEY = "sb_publishable_dCk3njz-F4vws7Y5ky6XNA__pOe081Z"
    private val json = "application/json".toMediaType()

    val signedIn: Boolean get() = Prefs.refreshToken != null

    fun url(path: String) = base.newBuilder().addPathSegments(path)

    /** Signs in or creates the account, then syncs. Returns a note when there's no session yet (email confirmation). */
    suspend fun signIn(email: String, password: String, create: Boolean): String? {
        val url = if (create) url("auth/v1/signup").build() else url("auth/v1/token").addQueryParameter("grant_type", "password").build()
        val session = JSONObject(request("POST", url, JSONObject().put("email", email).put("password", password).toString()))
        if (!session.has("access_token")) return "Check your email to confirm, then sign in."
        store(session)
        Prefs.email = session.optJSONObject("user")?.optString("email")?.ifEmpty { null } ?: email
        Prefs.syncError = null
        Stash.scope.launch { Sync.sync() }
        return null
    }

    /** Every save stays. The cursors reset, so the next sign-in, to any account, uploads everything again. */
    fun signOut() {
        val token = Prefs.accessToken
        // Best effort: the session ends here whatever the server says.
        if (token != null) Stash.scope.launch { runCatching { request("POST", url("auth/v1/logout").build(), "{}", token) } }
        forget()
    }

    /** Smart categories run on the server, so the choice lives with the account, where every device sees it. */
    suspend fun setSmartCategories(on: Boolean) {
        authorized("PUT", url("auth/v1/user").build(), JSONObject().put("data", JSONObject().put("smart_categories", on)).toString())
        Prefs.smartCategories = on
    }

    /** The account and the copy on the server go; the saves on this device stay. */
    suspend fun deleteAccount() {
        authorized("POST", url("rest/v1/rpc/delete_account").build(), "{}")
        forget()
    }

    fun describe(error: Exception): String = when (error) {
        is ServerError -> error.message.orEmpty()
        is IOException -> "Couldn't connect. Check your connection and try again."
        else -> "Something went wrong. Try again."
    }

    /** Refreshes the session before it expires or after a 401, once. */
    suspend fun authorized(method: String, url: HttpUrl, body: String? = null, prefer: String? = null): String {
        if (System.currentTimeMillis() / 1000 >= Prefs.expiresAt - 60) refresh()
        return try {
            request(method, url, body, Prefs.accessToken, prefer)
        } catch (e: ServerError) {
            if (e.code != 401) throw e
            refresh()
            request(method, url, body, Prefs.accessToken, prefer)
        }
    }

    private suspend fun refresh() {
        val token = checkNotNull(Prefs.refreshToken) { "Signed out" }
        val url = url("auth/v1/token").addQueryParameter("grant_type", "refresh_token").build()
        try {
            store(JSONObject(request("POST", url, JSONObject().put("refresh_token", token).toString())))
        } catch (e: ServerError) {
            if (e.code in 400..499) {
                forget()
                Prefs.syncError = "Signed out. Sign in again to keep syncing."
            }
            throw e
        }
    }

    private fun store(session: JSONObject) {
        Prefs.accessToken = session.getString("access_token")
        Prefs.refreshToken = session.getString("refresh_token")
        Prefs.expiresAt = session.optLong("expires_at", System.currentTimeMillis() / 1000 + session.optLong("expires_in", 3600))
        // What another device set comes back with sign-in and every refresh.
        Prefs.smartCategories = session.optJSONObject("user")?.optJSONObject("user_metadata")?.optBoolean("smart_categories") ?: false
    }

    private fun forget() {
        Prefs.email = null
        Prefs.accessToken = null
        Prefs.refreshToken = null
        Prefs.expiresAt = 0
        Prefs.lastPushedAt = 0
        Prefs.pullCursor = null
        Prefs.lastSyncedAt = 0
        Prefs.syncError = null
        Prefs.smartCategories = false
    }

    private suspend fun request(method: String, url: HttpUrl, body: String? = null, token: String? = null, prefer: String? = null): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).method(method, body?.toRequestBody(json)).header("apikey", KEY)
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .apply { prefer?.let { header("Prefer", it) } }
                .build()
            MetadataService.client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ServerError(response.code, serverMessage(text) ?: "Something went wrong (${response.code}).")
                text
            }
        }

    // Auth answers `msg` (or `error_description`); the REST API answers `message`.
    private fun serverMessage(text: String): String? {
        val error = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return listOf("msg", "error_description", "message").firstNotNullOfOrNull { error.optString(it).ifEmpty { null } }
    }
}

/** Pushes local changes, then pulls everyone else's. Offline or failing, it stops and never touches local data. */
object Sync {
    private val running = AtomicBoolean(false)

    var syncing by mutableStateOf(false)
        private set

    /** One at a time: a call while one runs is dropped. */
    suspend fun sync() {
        if (Stash.isSample || !Account.signedIn || !running.compareAndSet(false, true)) return
        syncing = true
        try {
            val pushStart = System.currentTimeMillis()
            push(pushStart)
            if (pull(pushStart)) Stash.updateWidget()
            Prefs.lastSyncedAt = System.currentTimeMillis()
            Prefs.syncError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Signed out meanwhile, there's nothing to report (or the reason is already there).
            if (Account.signedIn) Prefs.syncError = Account.describe(e)
        } finally {
            syncing = false
            running.set(false)
        }
    }

    private suspend fun push(pushStart: Long) {
        val saves = Account.url("rest/v1/saves").build()
        for (page in Stash.dao.modifiedSince(Prefs.lastPushedAt).chunked(PAGE)) {
            val rows = JSONArray(page.map { it.toJson() }).toString()
            Account.authorized("POST", saves, rows, prefer = "resolution=merge-duplicates,return=minimal")
        }
        val tombstones = Stash.dao.tombstones()
        // ponytail: one request per delete time, fine for deletes made by hand; batch by time range if bulk deletes arrive.
        for ((deletedAt, group) in tombstones.groupBy { it.deletedAt }) for (ids in group.chunked(100)) {
            val url = Account.url("rest/v1/saves").addQueryParameter("id", "in.(${ids.joinToString(",") { it.id }})").build()
            Account.authorized("PATCH", url, JSONObject().put("deleted_at", isoDate(deletedAt)).toString())
        }
        check(Account.signedIn) // Signed out meanwhile: the reset cursors must stay reset.
        Prefs.lastPushedAt = pushStart
        Stash.dao.deleteTombstones(tombstones.map { it.id })
    }

    /** Page by page, each applied in one transaction. True when anything came down. */
    private suspend fun pull(pushStart: Long): Boolean {
        var pulled = false
        do {
            val page = JSONArray(Account.authorized("GET", pullUrl(Account.base, Prefs.pullCursor)))
            val rows = List(page.length()) { page.getJSONObject(it) }
            if (rows.isEmpty()) break
            check(Account.signedIn)
            Stash.dao.applyPull(rows.map { it.toRemoteSave() }, pushStart)
            Prefs.pullCursor = rows.last().getString("updated_at")
            pulled = true
        } while (rows.size == PAGE)
        return pulled
    }

    // The server's columns are the Save fields in snake_case. user_id and updated_at are the server's, never sent.

    private fun Save.toJson() = JSONObject()
        .put("id", id)
        .put("url", url)
        .put("canonical_url", canonicalUrl)
        .put("source", source.raw)
        .put("content_type", contentType.raw)
        .put("category", category.raw)
        .put("status", status.raw)
        .put("category_is_manual", categoryIsManual)
        .put("title", title ?: JSONObject.NULL)
        .put("description_text", descriptionText ?: JSONObject.NULL)
        .put("thumbnail_url", thumbnailUrl ?: JSONObject.NULL)
        .put("author", author ?: JSONObject.NULL)
        .put("tags", JSONArray(tags))
        .put("summary", summary ?: JSONObject.NULL)
        .put("created_at", isoDate(createdAt))
        .put("last_saved_at", isoDate(lastSavedAt))
        .put("opened_at", openedAt?.let(::isoDate) ?: JSONObject.NULL)
        // A save brought back with Undo after its delete reached the server comes back there too.
        .put("deleted_at", JSONObject.NULL)

    private fun JSONObject.toRemoteSave(): RemoteSave {
        fun text(key: String) = if (isNull(key)) null else getString(key)
        val tags = optJSONArray("tags")
        val converters = Converters() // Unknown enum strings fall back, as everywhere else.
        val save = Save(
            id = getString("id"),
            url = getString("url"),
            canonicalUrl = getString("canonical_url"),
            source = converters.toSource(getString("source")),
            contentType = converters.toContentType(getString("content_type")),
            category = converters.toCategory(getString("category")),
            status = converters.toStatus(getString("status")),
            categoryIsManual = getBoolean("category_is_manual"),
            title = text("title"),
            descriptionText = text("description_text"),
            thumbnailUrl = text("thumbnail_url"),
            author = text("author"),
            tags = if (tags == null) emptyList() else List(tags.length()) { tags.getString(it) },
            summary = text("summary"),
            createdAt = parseIsoDate(getString("created_at")),
            lastSavedAt = parseIsoDate(getString("last_saved_at")),
            openedAt = text("opened_at")?.let(::parseIsoDate),
        )
        return RemoteSave(save, deleted = !isNull("deleted_at"))
    }
}
