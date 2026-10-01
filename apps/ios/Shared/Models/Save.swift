import Foundation
import SwiftData

enum Source: String, Codable, CaseIterable {
    case instagram, youtube, tiktok, reddit, x, spotify, maps, web, unknown
}

enum ContentType: String, Codable, CaseIterable {
    case video, article, post, product, place, music, other
}

enum Category: String, Codable, CaseIterable {
    case food, travel, fitness, ideas, business, tech, shopping, music, learning, places, other
}

/// Metadata enrichment state. The URL is saved regardless; this never gates a save.
enum SaveStatus: String, Codable {
    case pending, enriched, failed
}

@Model
final class Save {
    var id: UUID
    /// The URL exactly as it was shared. Source of truth.
    var url: URL
    /// Dedupe key. Not `@Attribute(.unique)`: CloudKit-backed stores reject unique
    /// constraints, so duplicates are resolved in code before inserting.
    var canonicalURL: String

    // Enums are stored as raw strings so they work inside #Predicate.
    var sourceRaw: String
    var contentTypeRaw: String
    var categoryRaw: String
    var statusRaw: String
    /// Set when the user picks a category, so automatic classification never overrides it.
    var categoryIsManual: Bool = false

    var title: String?
    // Not `description`: that name collides with NSObject in the Core Data layer.
    var descriptionText: String?
    var thumbnailURL: URL?
    var author: String?
    var tags: [String]
    var summary: String?

    var createdAt: Date
    var lastSavedAt: Date
    var openedAt: Date?
    /// Last local change, for sync: whatever changed after the last push goes up next time.
    /// Saves from before sync (and pulled ones) start at 1970, so the first push after signing in sends them.
    var modifiedAt = Date(timeIntervalSince1970: 0)

    init(
        url: URL,
        canonicalURL: String? = nil,
        source: Source = .unknown,
        contentType: ContentType = .other,
        category: Category = .other,
        status: SaveStatus = .pending,
        title: String? = nil,
        descriptionText: String? = nil,
        thumbnailURL: URL? = nil,
        author: String? = nil,
        tags: [String] = [],
        summary: String? = nil,
        createdAt: Date = .now,
        openedAt: Date? = nil
    ) {
        self.id = UUID()
        self.url = url
        self.canonicalURL = canonicalURL ?? url.absoluteString
        self.sourceRaw = source.rawValue
        self.contentTypeRaw = contentType.rawValue
        self.categoryRaw = category.rawValue
        self.statusRaw = status.rawValue
        self.title = title
        self.descriptionText = descriptionText
        self.thumbnailURL = thumbnailURL
        self.author = author
        self.tags = tags
        self.summary = summary
        self.createdAt = createdAt
        self.lastSavedAt = createdAt
        self.openedAt = openedAt
        self.modifiedAt = .now
    }

    /// Every local change calls this, so sync pushes it. Applying pulled rows doesn't.
    func touch() {
        modifiedAt = .now
    }

    var source: Source {
        get { Source(rawValue: sourceRaw) ?? .unknown }
        set { sourceRaw = newValue.rawValue }
    }

    var contentType: ContentType {
        get { ContentType(rawValue: contentTypeRaw) ?? .other }
        set { contentTypeRaw = newValue.rawValue }
    }

    var category: Category {
        get { Category(rawValue: categoryRaw) ?? .other }
        set { categoryRaw = newValue.rawValue }
    }

    /// The user's choice: sticks, whatever classification later thinks.
    func setCategory(_ category: Category) {
        self.category = category
        categoryIsManual = true
        touch()
    }

    /// Seen = opened. Unseen saves are what rediscovery resurfaces.
    func setSeen(_ seen: Bool) {
        guard seen != (openedAt != nil) else { return }
        openedAt = seen ? .now : nil
        touch()
    }

    var status: SaveStatus {
        get { SaveStatus(rawValue: statusRaw) ?? .pending }
        set { statusRaw = newValue.rawValue }
    }
}

/// A save deleted while signed in, until sync tells the server.
@Model
final class Tombstone {
    var id: UUID
    var deletedAt: Date

    init(id: UUID, deletedAt: Date = .now) {
        self.id = id
        self.deletedAt = deletedAt
    }
}
