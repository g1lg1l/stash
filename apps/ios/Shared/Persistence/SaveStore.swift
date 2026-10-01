import Foundation
import OSLog
import SwiftData

enum SaveStore {
    /// Stashes a link, or bumps `lastSavedAt` when the same resource is already there. Never needs the network.
    @discardableResult
    static func add(_ analysis: URLAnalysis, in context: ModelContext, at date: Date = .now) throws -> Save {
        let key = analysis.canonicalURL
        var existing = FetchDescriptor<Save>(predicate: #Predicate { $0.canonicalURL == key })
        existing.fetchLimit = 1
        if let save = try context.fetch(existing).first {
            // Order-independent: the earliest share is when it was first stashed.
            save.createdAt = min(save.createdAt, date)
            save.lastSavedAt = max(save.lastSavedAt, date)
            save.touch()
            try context.save()
            return save
        }
        let save = Save(
            url: analysis.url,
            canonicalURL: key,
            source: analysis.source,
            contentType: analysis.contentType,
            createdAt: date
        )
        // First guess from the link alone (maps, Spotify, products); refined once metadata arrives.
        save.category = ClassificationService.category(for: save)
        context.insert(save)
        try context.save()
        return save
    }
}

extension SaveStore {
    /// Removes the save entirely, including its cached thumbnail. Signed in, it leaves a tombstone for sync to push.
    static func delete(_ save: Save, in context: ModelContext, tombstone: Bool) {
        if let thumbnail = save.thumbnailURL {
            URLCache.shared.removeCachedResponse(for: URLRequest(url: thumbnail))
        }
        if tombstone { context.insert(Tombstone(id: save.id)) }
        context.delete(save)
        try? context.save()
    }
}

/// Hand-off from the Share Extension to the app: one small JSON file per share, in the App Group.
/// The extension never opens the database, so a share is visible even while the app is running;
/// the app drains the inbox into SwiftData on launch and whenever it comes to the foreground.
enum ShareInbox {
    struct Item: Codable {
        let url: URL
        let sharedAt: Date
    }

    static var directory: URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: ModelContainer.appGroupID)?
            .appending(path: "share-inbox", directoryHint: .isDirectory)
    }

    static func add(_ url: URL, at date: Date = .now, to directory: URL? = directory) throws {
        guard let directory else { throw CocoaError(.fileNoSuchFile) }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let data = try JSONEncoder().encode(Item(url: url, sharedAt: date))
        try data.write(to: directory.appending(path: "\(UUID().uuidString).json"), options: .atomic)
    }

    /// Imports every pending share, deleting each file only after its save is committed.
    /// Returns the saves it touched, so callers can enrich them.
    @discardableResult
    static func drain(from directory: URL? = directory, into context: ModelContext) -> [Save] {
        guard let directory,
              let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
        else { return [] }
        let log = Logger(subsystem: "com.g1lg1l.stash", category: "inbox")
        var saves: [Save] = []
        var pending: [(file: URL, item: Item)] = []
        for file in files where file.pathExtension == "json" {
            do {
                pending.append((file, try JSONDecoder().decode(Item.self, from: Data(contentsOf: file))))
            } catch is DecodingError {
                log.error("Dropping unreadable inbox file \(file.lastPathComponent)")
                try? FileManager.default.removeItem(at: file)
            } catch {
                log.error("Couldn't read \(file.lastPathComponent): \(error)") // Kept for the next drain.
            }
        }
        // Oldest first, so createdAt and lastSavedAt reflect the real share order.
        for (file, item) in pending.sorted(by: { $0.item.sharedAt < $1.item.sharedAt }) {
            do {
                if let analysis = URLSourceDetector.analyze(item.url) {
                    saves.append(try SaveStore.add(analysis, in: context, at: item.sharedAt))
                } else {
                    log.error("Dropping inbox item that isn't a web link: \(item.url, privacy: .private)")
                }
                try FileManager.default.removeItem(at: file)
            } catch {
                // Keep the file; the next drain retries it.
                log.error("Couldn't import \(file.lastPathComponent): \(error)")
            }
        }
        return saves
    }
}
