import SwiftData
import SwiftUI

@main
struct StashApp: App {
    let container: ModelContainer

    init() {
        // Thumbnails load through URLSession.shared; give its cache room for a few hundred images.
        URLCache.shared = URLCache(memoryCapacity: 50_000_000, diskCapacity: 300_000_000)
        do {
            #if DEBUG
            // Launch with `-sampleData` for an in-memory store seeded with sample saves.
            if ProcessInfo.processInfo.arguments.contains("-sampleData") {
                container = try .stash(inMemory: true)
                SampleData.insert(into: container.mainContext)
                return
            }
            #endif
            container = try .stash()
        } catch {
            fatalError("Could not open the Stash store: \(error)")
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(container)
    }
}
