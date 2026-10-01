import SwiftData
import SwiftUI

struct RootView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @State private var retriedFailed = false
    @AppStorage("theme") private var theme: Theme = .automatic

    var body: some View {
        TabView {
            Tab("Home", systemImage: "house") {
                HomeView()
            }
            Tab("Explore", systemImage: "square.grid.2x2") {
                ExploreView()
            }
            Tab(role: .search) {
                SearchView()
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .preferredColorScheme(theme.colorScheme)
        .onChange(of: scenePhase, initial: true) { _, phase in
            // Pick up anything shared from other apps while Stash was in the background.
            guard phase == .active else { return }
            let retryFailed = !retriedFailed // Pages that had nothing get one more try per launch.
            retriedFailed = true
            Task { await refreshFromInbox(modelContext, retryFailed: retryFailed) }
        }
    }
}

/// Imports shares from the extension, then fills in metadata. Cheap to call often.
@MainActor
func refreshFromInbox(_ context: ModelContext, retryFailed: Bool) async {
    // Never into a throwaway in-memory store (-sampleData): draining deletes the inbox files.
    guard !context.container.configurations.contains(where: \.isStoredInMemoryOnly) else { return }
    ShareInbox.drain(into: context)
    await MetadataEnricher.enrichPending(in: context, retryFailed: retryFailed)
}

#Preview {
    RootView()
        .modelContainer(.preview)
}
