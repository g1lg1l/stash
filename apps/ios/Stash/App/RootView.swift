import SwiftData
import SwiftUI
import WidgetKit

struct RootView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @State private var retriedFailed = false
    @AppStorage("theme") private var theme: Theme = .automatic
    @State private var openedSave: Save?

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
            // Leaving the app is when saves have changed: refresh the Home Screen widget once, here.
            if phase == .background { WidgetCenter.shared.reloadAllTimelines() }
            // Pick up anything shared from other apps while Stash was in the background.
            guard phase == .active else { return }
            let retryFailed = !retriedFailed // Pages that had nothing get one more try per launch.
            retriedFailed = true
            Task { await refreshFromInbox(modelContext, retryFailed: retryFailed) }
        }
        // The widget links to stash://save/<id>.
        .onOpenURL { url in
            guard url.scheme == "stash", url.host() == "save", let id = UUID(uuidString: url.lastPathComponent) else { return }
            openedSave = try? modelContext.fetch(FetchDescriptor<Save>(predicate: #Predicate { $0.id == id })).first
        }
        .sheet(item: $openedSave) { save in
            NavigationStack { SaveDetailView(save: save) }
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
