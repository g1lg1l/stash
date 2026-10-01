package com.g1lg1l.stash.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.Normalizer

/** Deterministic, local and explainable. An on-device model can replace `category(...)` later without touching callers. */
object Classification {
    fun category(save: Save): Category =
        category(save.title, save.descriptionText, save.tags, save.url, save.source, save.contentType)

    fun category(
        title: String?, description: String? = null, tags: List<String> = emptyList(),
        url: String, source: Source, contentType: ContentType,
    ): Category {
        // Structure beats words: a map pin is a place whatever it's called.
        if (source == Source.MAPS || contentType == ContentType.PLACE) return Category.PLACES
        if (source == Source.SPOTIFY || contentType == ContentType.MUSIC) return Category.MUSIC
        if (contentType == ContentType.PRODUCT) return Category.SHOPPING

        // ponytail: keyword scoring; swap for an on-device model when this misfiles too often.
        val link = url.toHttpUrlOrNull()
        val titleWords = words(title.orEmpty())
        val bodyWords = words(listOf(description.orEmpty(), tags.joinToString(" "), link?.host.orEmpty(), link?.encodedPath.orEmpty()).joinToString(" "))
        var best = Category.OTHER
        var bestScore = 0
        for ((category, vocabulary) in keywords) {
            val score = 2 * titleWords.count { it in vocabulary } + bodyWords.count { it in vocabulary }
            if (score > bestScore) {
                best = category
                bestScore = score
            }
        }
        return best
    }

    private val separators = Regex("[^\\p{L}\\p{N}]+")

    private fun words(text: String): Set<String> =
        fold(text).split(separators).filter { it.isNotEmpty() }.toSet()

    /** In priority order: on a tie the earlier category wins. English plus common Italian. */
    private val keywords: List<Pair<Category, Set<String>>> = listOf(
        Category.FOOD to setOf(
            "recipe", "recipes", "cook", "cooking", "chef", "kitchen", "bake", "baking", "pasta", "pizza", "ramen",
            "sushi", "dinner", "lunch", "breakfast", "brunch", "dessert", "vegan", "food", "foodie", "meal", "snack",
            "ricetta", "ricette", "cucina", "cena", "pranzo", "dolce", "colazione",
        ),
        Category.FITNESS to setOf(
            "workout", "workouts", "exercise", "exercises", "gym", "fitness", "training", "routine", "calisthenics",
            "running", "marathon", "yoga", "pilates", "strength", "muscle", "hiit", "cardio", "stretching",
            "mobility", "kettlebell", "squat", "allenamento", "palestra", "esercizi",
        ),
        Category.TRAVEL to setOf(
            "travel", "trip", "itinerary", "flight", "flights", "hotel", "hostel", "airbnb", "vacation", "holiday",
            "backpacking", "hiking", "trail", "trek", "beach", "island", "roadtrip", "destination", "viaggio",
            "vacanza", "vacanze", "spiaggia", "escursione",
        ),
        Category.BUSINESS to setOf(
            "startup", "startups", "founder", "founders", "business", "marketing", "sales", "revenue", "arr", "mrr",
            "saas", "entrepreneur", "investing", "investor", "venture", "vc", "bootstrapping", "pricing", "growth",
            "customers", "azienda", "impresa", "investimenti",
        ),
        Category.TECH to setOf(
            "ai", "gpt", "llm", "ml", "programming", "code", "coding", "software", "developer", "developers", "swift",
            "swiftui", "python", "javascript", "typescript", "api", "iphone", "ios", "android", "gadget", "tech",
            "computer", "transformer", "transformers", "linux", "tecnologia", "programmazione",
        ),
        Category.LEARNING to setOf(
            "learn", "learning", "course", "tutorial", "lecture", "lesson", "explained", "explainer", "university",
            "study", "science", "history", "documentary", "book", "books", "corso", "lezione", "imparare",
            "storia", "libro",
        ),
        Category.IDEAS to setOf(
            "idea", "ideas", "inspiration", "lessons", "mindset", "creativity", "philosophy", "speech", "commencement",
            "essay", "productivity", "life", "advice", "career", "idee", "ispirazione", "consigli",
        ),
        Category.SHOPPING to setOf(
            "buy", "shop", "sale", "deal", "deals", "discount", "price", "store", "amazon", "wishlist", "offerta",
            "sconto", "acquista",
        ),
        Category.MUSIC to setOf("song", "songs", "album", "playlist", "music", "band", "concert", "lyrics", "musica", "canzone"),
        Category.PLACES to setOf("museum", "temple", "park", "cafe", "bar", "market", "gallery", "neighborhood", "museo", "piazza"),
    )
}

private val marks = Regex("\\p{Mn}+")

/** Lowercased, without accents: "Sensō-ji" → "senso-ji". */
fun fold(text: String): String = marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()
