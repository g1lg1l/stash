import Foundation
import OSLog
import SwiftData

extension ModelContainer {
    /// AltStore and Sideloadly re-sign the release .ipa under the installer's team, which renames the group:
    /// read it from the embedded profile. App Store builds have none.
    static let appGroupID = provisionedAppGroup(
        Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision").flatMap { try? Data(contentsOf: $0) }
    ) ?? "group.com.g1lg1l.stash"

    /// The profile is a signed (CMS) blob with the plist inside it as plain text.
    static func provisionedAppGroup(_ profile: Data?) -> String? {
        guard let profile,
              let start = profile.range(of: Data("<?xml".utf8)),
              let end = profile.range(of: Data("</plist>".utf8), in: start.lowerBound..<profile.endIndex),
              let plist = try? PropertyListSerialization.propertyList(from: profile[start.lowerBound..<end.upperBound], format: nil) as? [String: Any],
              let entitlements = plist["Entitlements"] as? [String: Any]
        else { return nil }
        return (entitlements["com.apple.security.application-groups"] as? [String])?.first
    }

    /// The single store, in the App Group container. The app writes it; the widget only reads it.
    static func stash(inMemory: Bool = false) throws -> ModelContainer {
        let configuration: ModelConfiguration
        if inMemory {
            configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        } else if FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID) != nil {
            configuration = ModelConfiguration("Stash", groupContainer: .identifier(appGroupID), cloudKitDatabase: .none)
        } else {
            // Builds signed without the App Group (e.g. a personal team before the group is registered)
            // still work; the extensions just can't see this store.
            Logger(subsystem: "com.g1lg1l.stash", category: "persistence")
                .warning("App Group \(appGroupID) unavailable, using a local store")
            configuration = ModelConfiguration("Stash", groupContainer: .none, cloudKitDatabase: .none)
        }
        return try ModelContainer(for: Save.self, Tombstone.self, configurations: configuration)
    }

    /// `-sampleData` and tests: never drained into, never synced.
    var isInMemory: Bool { configurations.contains(where: \.isStoredInMemoryOnly) }
}
