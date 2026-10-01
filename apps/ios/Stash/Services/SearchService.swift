import Foundation

/// Local search. Swappable for semantic search later without touching the UI.
enum SearchService {
    /// Every word must match some field, ignoring case and accents ("senso tokyo" finds "Sensō-ji" tagged tokyo).
    /// Keeps the input order, so newest-first stays newest-first.
    // ponytail: linear scan per keystroke, fine for thousands of saves; add an index if it ever shows up in profiles.
    static func results(for query: String, in saves: [Save]) -> [Save] {
        let terms = query.split(whereSeparator: \.isWhitespace)
        guard !terms.isEmpty else { return [] }
        return saves.filter { save in
            let fields = [save.title, save.descriptionText, save.summary, save.author].compactMap { $0 }
                + [save.category.displayName, save.source.displayName, save.url.absoluteString]
                + save.tags
            return terms.allSatisfy { term in fields.contains { $0.localizedStandardContains(term) } }
        }
    }
}
