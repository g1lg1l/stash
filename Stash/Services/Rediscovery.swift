import Foundation

/// "Worth another look": unseen saves old enough to have slipped your mind. Deliberately simple.
enum Rediscovery {
    static func picks(from saves: [Save], now: Date = .now, limit: Int = 6) -> [Save] {
        let cutoff = now.addingTimeInterval(-3 * 24 * 3600)
        // Oldest first: the longer something waits, the more it deserves a nudge. Opening it retires it.
        return Array(
            saves.filter { $0.openedAt == nil && $0.lastSavedAt <= cutoff }
                .sorted { $0.lastSavedAt < $1.lastSavedAt }
                .prefix(limit)
        )
    }
}
