package com.g1lg1l.stash.data

import java.time.Instant
import java.time.ZoneId

/** Local search. Swappable for semantic search later without touching the UI. */
object Search {
    /**
     * Every word must match some field, ignoring case and accents ("senso tokyo" finds "Sensō-ji" tagged tokyo).
     * Keeps the input order, so newest-first stays newest-first.
     */
    // ponytail: linear scan per keystroke, fine for thousands of saves; add an FTS table if it ever shows up in profiles.
    fun results(query: String, saves: List<Save>): List<Save> {
        val terms = query.split(' ', '\t', '\n').filter { it.isNotEmpty() }.map(::fold)
        if (terms.isEmpty()) return emptyList()
        return saves.filter { save ->
            val fields = (listOfNotNull(save.title, save.descriptionText, save.summary, save.author) +
                listOf(save.category.displayName, save.source.displayName, save.url) + save.tags).map(::fold)
            terms.all { term -> fields.any { term in it } }
        }
    }
}

data class FeedSection(val title: String?, val saves: List<Save>) {
    companion object {
        /** Today / Yesterday / Last 7 Days / Earlier. Expects saves sorted newest first. */
        fun byDay(saves: List<Save>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<FeedSection> {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val weekAgo = today.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            val sections = mutableListOf<Pair<String, MutableList<Save>>>()
            for (save in saves) {
                val date = Instant.ofEpochMilli(save.lastSavedAt).atZone(zone).toLocalDate()
                val title = when {
                    date == today -> "Today"
                    date == today.minusDays(1) -> "Yesterday"
                    save.lastSavedAt >= weekAgo -> "Last 7 Days"
                    else -> "Earlier"
                }
                if (sections.lastOrNull()?.first == title) sections.last().second += save else sections += title to mutableListOf(save)
            }
            return sections.map { FeedSection(it.first, it.second) }
        }
    }
}

/** "Worth another look": unseen saves old enough to have slipped your mind. Deliberately simple. */
object Rediscovery {
    private const val DAY = 24 * 3600 * 1000L

    fun picks(saves: List<Save>, now: Long = System.currentTimeMillis(), limit: Int = 6): List<Save> {
        val cutoff = now - 3 * DAY
        // Oldest first: the longer something waits, the more it deserves a nudge. Opening it retires it.
        return saves.filter { it.openedAt == null && it.lastSavedAt <= cutoff }.sortedBy { it.lastSavedAt }.take(limit)
    }

    /** The widget's one save: something worth another look, else the newest unseen one, else the newest. */
    fun widgetPick(saves: List<Save>, now: Long = System.currentTimeMillis()): Pair<Save, String>? {
        picks(saves, now, limit = 1).firstOrNull()?.let { return it to "Worth another look" }
        val newest = saves.maxByOrNull { it.lastSavedAt } ?: return null
        val unseen = saves.filter { it.openedAt == null }.maxByOrNull { it.lastSavedAt }
        return if (unseen != null) unseen to "Waiting for you" else newest to "Latest save"
    }
}
