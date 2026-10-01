package com.g1lg1l.stash.data

/**
 * Realistic saves for tests and the `sampleData` launch extra. The same set as on iOS, keyed like real shares,
 * so sharing one of these links again dedupes as it would.
 */
object SampleData {
    fun saves(now: Long = System.currentTimeMillis()): List<Save> {
        fun ago(hours: Double) = now - (hours * 3600 * 1000).toLong()
        fun wiki(path: String) = "https://upload.wikimedia.org/wikipedia/commons/thumb/$path"

        return listOf(
            Save(
                url = "https://www.tiktok.com/@weeknightkitchen/video/7401928374650182913",
                source = Source.TIKTOK, contentType = ContentType.VIDEO,
                createdAt = ago(0.2),
            ),
            Save(
                url = "https://www.youtube.com/watch?v=kCc8FmEb1nY",
                source = Source.YOUTUBE, contentType = ContentType.VIDEO, category = Category.TECH, status = SaveStatus.ENRICHED,
                title = "Let's build GPT: from scratch, in code, spelled out.",
                descriptionText = "We build a Generatively Pretrained Transformer (GPT), following the paper \"Attention is All You Need\".",
                thumbnailUrl = "https://i.ytimg.com/vi/kCc8FmEb1nY/hqdefault.jpg",
                author = "Andrej Karpathy",
                tags = listOf("ai", "transformers", "python"),
                createdAt = ago(2.0),
            ),
            Save(
                url = "https://www.instagram.com/reel/C8x2kLmN4pQ/",
                source = Source.INSTAGRAM, contentType = ContentType.POST, category = Category.FOOD, status = SaveStatus.ENRICHED,
                title = "Creamy cacio e pepe in 10 minutes",
                descriptionText = "Three ingredients, one pan, no cream.",
                thumbnailUrl = wiki("9/99/Cacio_e_pepe.jpg/960px-Cacio_e_pepe.jpg"),
                author = "@weeknight.pasta",
                tags = listOf("pasta", "recipe", "quick"),
                createdAt = ago(5.0),
            ),
            Save(
                url = "https://maps.google.com/?q=Senso-ji+Temple,+Asakusa,+Tokyo",
                source = Source.MAPS, contentType = ContentType.PLACE, category = Category.PLACES, status = SaveStatus.ENRICHED,
                title = "Sensō-ji",
                descriptionText = "Tokyo's oldest temple, in Asakusa.",
                thumbnailUrl = wiki("4/43/Sensoji_2023.jpg/960px-Sensoji_2023.jpg"),
                tags = listOf("tokyo", "japan", "temple"),
                createdAt = ago(26.0),
            ),
            Save(
                url = "https://paulgraham.com/startupideas.html",
                source = Source.WEB, contentType = ContentType.ARTICLE, category = Category.BUSINESS, status = SaveStatus.ENRICHED,
                title = "How to Get Startup Ideas",
                descriptionText = "The way to get startup ideas is not to try to think of startup ideas. It's to look for problems.",
                author = "Paul Graham",
                tags = listOf("startups", "essay"),
                createdAt = ago(30.0),
            ),
            Save(
                url = "https://x.com/indiehacker_jo/status/1839201928374650182",
                source = Source.X, contentType = ContentType.POST, category = Category.IDEAS, status = SaveStatus.ENRICHED,
                title = "12 lessons from bootstrapping to \$1M ARR",
                descriptionText = "1/ Charge from day one. Free users tell you what they like; paying users tell you what they need.",
                author = "@indiehacker_jo",
                tags = listOf("bootstrapping", "saas"),
                createdAt = ago(50.0),
            ),
            Save(
                url = "https://open.spotify.com/album/4m2880jivSbbyEGAKfITCa",
                source = Source.SPOTIFY, contentType = ContentType.MUSIC, category = Category.MUSIC, status = SaveStatus.ENRICHED,
                title = "Random Access Memories",
                thumbnailUrl = "https://image-cdn-fa.spotifycdn.com/image/ab67616d00001e029b9b36b0e22870b9f542d937",
                author = "Daft Punk",
                tags = listOf("album", "electronic"),
                createdAt = ago(70.0),
            ),
            Save(
                url = "https://www.youtube.com/shorts/Qm3vX8pLw2E",
                source = Source.YOUTUBE, contentType = ContentType.VIDEO, category = Category.TRAVEL, status = SaveStatus.ENRICHED,
                title = "Ice Lakes trail in 60 seconds",
                thumbnailUrl = wiki("a/a9/Hiking_to_the_Ice_Lakes._San_Juan_National_Forest%2C_Colorado.jpg/960px-Hiking_to_the_Ice_Lakes._San_Juan_National_Forest%2C_Colorado.jpg"),
                author = "Trail Notes",
                tags = listOf("hiking", "colorado"),
                createdAt = ago(75.0),
            ),
            Save(
                url = "https://www.reddit.com/r/bodyweightfitness/comments/1f2k9xq/the_recommended_routine/",
                source = Source.REDDIT, contentType = ContentType.POST, category = Category.FITNESS, status = SaveStatus.ENRICHED,
                title = "The Recommended Routine: a full beginner program",
                descriptionText = "Warm-up, strength work in pairs, and how to progress each week.",
                author = "r/bodyweightfitness",
                tags = listOf("calisthenics", "program"),
                createdAt = ago(100.0),
            ),
            Save(
                url = "https://www.youtube.com/watch?v=UF8uR6Z6KLc",
                source = Source.YOUTUBE, contentType = ContentType.VIDEO, category = Category.IDEAS, status = SaveStatus.ENRICHED,
                title = "Steve Jobs' 2005 Stanford Commencement Address",
                thumbnailUrl = "https://i.ytimg.com/vi/UF8uR6Z6KLc/hqdefault.jpg",
                author = "Stanford",
                tags = listOf("speech", "career"),
                createdAt = ago(150.0),
            ),
            Save(
                url = "https://www.roguefitness.com/rogue-kettlebells",
                source = Source.WEB, contentType = ContentType.PRODUCT, category = Category.SHOPPING, status = SaveStatus.ENRICHED,
                title = "Rogue Kettlebells",
                descriptionText = "Single-piece cast iron, 9–203 lb.",
                thumbnailUrl = wiki("1/14/Competition_kettlebell_16_kilo.jpg/960px-Competition_kettlebell_16_kilo.jpg"),
                tags = listOf("kettlebell", "home gym"),
                createdAt = ago(190.0),
            ),
            Save(
                url = "https://maps.google.com/?q=Tsukiji+Outer+Market,+Tokyo",
                source = Source.MAPS, contentType = ContentType.PLACE, category = Category.PLACES, status = SaveStatus.ENRICHED,
                title = "Tsukiji Outer Market",
                descriptionText = "Street food, knives and fresh seafood. Go early.",
                thumbnailUrl = wiki("f/fd/2018_Tsukiji_fish_market.jpg/960px-2018_Tsukiji_fish_market.jpg"),
                tags = listOf("tokyo", "japan", "market"),
                createdAt = ago(220.0),
            ),
            Save(
                url = "https://newsletter.example.com/p/the-quiet-power-of-slow-mornings",
                source = Source.WEB, contentType = ContentType.ARTICLE, status = SaveStatus.FAILED,
                createdAt = ago(260.0),
            ),
            Save(
                url = "https://chemexcoffeemaker.com/products/six-cup-classic-series-coffeemaker",
                source = Source.WEB, contentType = ContentType.PRODUCT, category = Category.SHOPPING, status = SaveStatus.ENRICHED,
                title = "Six Cup Classic Chemex",
                descriptionText = "Hand-blown glass pour-over, designed in 1941.",
                thumbnailUrl = wiki("a/a9/Peter_Schlumbohm._Coffee_Maker%2C_Designed_1941.jpg/960px-Peter_Schlumbohm._Coffee_Maker%2C_Designed_1941.jpg"),
                tags = listOf("coffee", "kitchen"),
                createdAt = ago(290.0),
            ),
            Save(
                url = "https://www.japan-guide.com/e/e2158.html",
                source = Source.WEB, contentType = ContentType.ARTICLE, category = Category.TRAVEL, status = SaveStatus.ENRICHED,
                title = "Kyoto Travel Guide",
                descriptionText = "Temples, gardens and where to stay in Japan's former capital.",
                thumbnailUrl = wiki("6/6b/Kyoto%2C_Japan_%2849667780482%29.jpg/960px-Kyoto%2C_Japan_%2849667780482%29.jpg"),
                tags = listOf("kyoto", "japan", "guide"),
                createdAt = ago(480.0),
                openedAt = ago(400.0),
            ),
        ).map { it.copy(canonicalUrl = UrlSourceDetector.analyze(it.url)?.canonicalUrl ?: it.url) }
    }
}
