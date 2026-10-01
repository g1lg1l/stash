import SwiftData
import SwiftUI
import WidgetKit

struct RootView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @State private var retriedFailed = false
    @AppStorage("theme") private var theme: Theme = .automatic
    @State private var openedSave: Save?
    @AppStorage("seenWelcome") private var seenWelcome = false
    @State private var showingWelcome = false

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
            // Leaving the app is when saves have changed: refresh the Home Screen widget once, here, and push them.
            if phase == .background {
                WidgetCenter.shared.reloadAllTimelines()
                let sync = Task { await Account.shared.sync(modelContext) }
                let task = UIApplication.shared.beginBackgroundTask { sync.cancel() } // Out of time: stop; the next sync resumes.
                Task {
                    await sync.value
                    UIApplication.shared.endBackgroundTask(task)
                }
            }
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
        // Once, on first launch, whatever the choice. Not over sample data, nor when a reinstall kept the session.
        .onAppear {
            showingWelcome = !seenWelcome && !Account.shared.isSignedIn && !modelContext.container.isInMemory
            seenWelcome = true
        }
        .fullScreenCover(isPresented: $showingWelcome) { WelcomeView() }
    }
}

/// Imports shares from the extension, syncs, then fills in metadata. Cheap to call often.
@MainActor
func refreshFromInbox(_ context: ModelContext, retryFailed: Bool) async {
    // Never into a throwaway in-memory store (-sampleData): draining deletes the inbox files.
    guard !context.container.isInMemory else { return }
    ShareInbox.drain(into: context)
    await Account.shared.sync(context) // New shares go up; other devices' come down and get enriched with the rest.
    await MetadataEnricher.enrichPending(in: context, retryFailed: retryFailed)
}

#Preview {
    RootView()
        .modelContainer(.preview)
}
