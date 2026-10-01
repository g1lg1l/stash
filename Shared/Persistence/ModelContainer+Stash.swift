import Foundation
import OSLog
import SwiftData

extension ModelContainer {
    static let appGroupID = "group.com.g1lg1l.stash"

    /// The single store shared by the app and the Share Extension, in the App Group container.
    static func stash(inMemory: Bool = false) throws -> ModelContainer {
        let configuration: ModelConfiguration
        if inMemory {
            configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        } else if FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID) != nil {
            configuration = ModelConfiguration("Stash", groupContainer: .identifier(appGroupID), cloudKitDatabase: .none)
        } else {
            // Builds signed without the App Group (e.g. a personal team before the group is registered)
            // still work; the Share Extension just can't see this store.
            Logger(subsystem: "com.g1lg1l.stash", category: "persistence")
                .warning("App Group \(appGroupID) unavailable, using a local store")
            configuration = ModelConfiguration("Stash", groupContainer: .none, cloudKitDatabase: .none)
        }
        return try ModelContainer(for: Save.self, configurations: configuration)
    }
}
