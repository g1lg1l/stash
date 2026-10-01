import Foundation

/// Deterministic, local and explainable. An on-device model can replace `category(...)` later without touching callers.
enum ClassificationService {
    static func category(for save: Save) -> Category {
        category(title: save.title, description: save.descriptionText, tags: save.tags,
                 url: save.url, source: save.source, contentType: save.contentType)
    }

    static func category(title: String?, description: String? = nil, tags: [String] = [],
                         url: URL, source: Source, contentType: ContentType) -> Category {
        // Structure beats words: a map pin is a place whatever it's called.
        if source == .maps || contentType == .place { return .places }
        if source == .spotify || contentType == .music { return .music }
        if contentType == .product { return .shopping }

        // ponytail: keyword scoring; swap for an on-device model when this misfiles too often.
        let titleWords = words(in: title ?? "")
        let bodyWords = words(in: [description ?? "", tags.joined(separator: " "), url.host() ?? "", url.path()].joined(separator: " "))
        var best = (category: Category.other, score: 0)
        for (category, vocabulary) in keywords {
            let score = 2 * titleWords.intersection(vocabulary).count + bodyWords.intersection(vocabulary).count
            if score > best.score { best = (category, score) }
        }
        return best.category
    }

    private static func words(in text: String) -> Set<String> {
        let folded = text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
        return Set(folded.split { !$0.isLetter && !$0.isNumber }.map(String.init))
    }

    /// In priority order: on a tie the earlier category wins. English plus common Italian.
    private static let keywords: KeyValuePairs<Category, Set<String>> = [
        .food: ["recipe", "recipes", "cook", "cooking", "chef", "kitchen", "bake", "baking", "pasta", "pizza", "ramen",
                "sushi", "dinner", "lunch", "breakfast", "brunch", "dessert", "vegan", "food", "foodie", "meal", "snack",
                "ricetta", "ricette", "cucina", "cena", "pranzo", "dolce", "colazione"],
        .fitness: ["workout", "workouts", "exercise", "exercises", "gym", "fitness", "training", "routine", "calisthenics",
                   "running", "marathon", "yoga", "pilates", "strength", "muscle", "hiit", "cardio", "stretching",
                   "mobility", "kettlebell", "squat", "allenamento", "palestra", "esercizi"],
        .travel: ["travel", "trip", "itinerary", "flight", "flights", "hotel", "hostel", "airbnb", "vacation", "holiday",
                  "backpacking", "hiking", "trail", "trek", "beach", "island", "roadtrip", "destination", "viaggio",
                  "vacanza", "vacanze", "spiaggia", "escursione"],
        .business: ["startup", "startups", "founder", "founders", "business", "marketing", "sales", "revenue", "arr", "mrr",
                    "saas", "entrepreneur", "investing", "investor", "venture", "vc", "bootstrapping", "pricing", "growth",
                    "customers", "azienda", "impresa", "investimenti"],
        .tech: ["ai", "gpt", "llm", "ml", "programming", "code", "coding", "software", "developer", "developers", "swift",
                "swiftui", "python", "javascript", "typescript", "api", "iphone", "ios", "android", "gadget", "tech",
                "computer", "transformer", "transformers", "linux", "tecnologia", "programmazione"],
        .learning: ["learn", "learning", "course", "tutorial", "lecture", "lesson", "explained", "explainer", "university",
                    "study", "science", "history", "documentary", "book", "books", "corso", "lezione", "imparare",
                    "storia", "libro"],
        .ideas: ["idea", "ideas", "inspiration", "lessons", "mindset", "creativity", "philosophy", "speech", "commencement",
                 "essay", "productivity", "life", "advice", "career", "idee", "ispirazione", "consigli"],
        .shopping: ["buy", "shop", "sale", "deal", "deals", "discount", "price", "store", "amazon", "wishlist", "offerta",
                    "sconto", "acquista"],
        .music: ["song", "songs", "album", "playlist", "music", "band", "concert", "lyrics", "musica", "canzone"],
        .places: ["museum", "temple", "park", "cafe", "bar", "market", "gallery", "neighborhood", "museo", "piazza"],
    ]
}
