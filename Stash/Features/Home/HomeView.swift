import SwiftData
import SwiftUI

struct HomeView: View {
    @Query(sort: \Save.lastSavedAt, order: .reverse) private var saves: [Save]
    @Environment(\.modelContext) private var modelContext
    @AppStorage("feedLayout") private var layout: FeedLayout = .cards
    @State private var unseenOnly = false
    @State private var type: ContentType?
    @State private var showingSettings = false

    var body: some View {
        let shown = saves.filter { (!unseenOnly || $0.openedAt == nil) && (type == nil || $0.contentType == type) }
        NavigationStack {
            SaveFeed(sections: FeedSection.byDay(shown))
                .overlay {
                    if saves.isEmpty {
                        EmptyStashView()
                    } else if shown.isEmpty {
                        ContentUnavailableView {
                            Label("Nothing matches", systemImage: "line.3.horizontal.decrease.circle")
                        } description: {
                            Text("No saves match these filters.")
                        } actions: {
                            Button("Show All") { clearFilters() }
                        }
                    }
                }
                .refreshable { await refreshFromInbox(modelContext, retryFailed: true) }
                .navigationTitle(greeting)
                .navigationSubtitle(subtitle)
                .toolbar {
                    if !saves.isEmpty {
                        ToolbarItem { optionsMenu }
                        ToolbarSpacer(.fixed)
                    }
                    ToolbarItem {
                        Button("Settings", systemImage: "gearshape") { showingSettings = true }
                    }
                }
                .sheet(isPresented: $showingSettings) { SettingsView() }
                .sensoryFeedback(.selection, trigger: layout)
                .sensoryFeedback(.selection, trigger: unseenOnly)
        }
    }

    private var optionsMenu: some View {
        Menu {
            Picker("Layout", selection: $layout) {
                Label("Cards", systemImage: "rectangle.grid.1x2").tag(FeedLayout.cards)
                Label("Compact", systemImage: "list.bullet").tag(FeedLayout.compact)
            }
            .pickerStyle(.inline)

            Section {
                Toggle("Unseen Only", systemImage: "circle.badge", isOn: $unseenOnly)
                Picker(selection: $type) {
                    Text("All Types").tag(ContentType?.none)
                    // Only types the user actually has.
                    ForEach(ContentType.allCases.filter { t in saves.contains { $0.contentType == t } }, id: \.self) { type in
                        Label(type.displayName, systemImage: type.symbol).tag(Optional(type))
                    }
                } label: {
                    Label("Type", systemImage: type?.symbol ?? "square.stack")
                }
                .pickerStyle(.menu)
            }

            if isFiltering {
                Button("Clear Filters", systemImage: "xmark.circle") { clearFilters() }
            }
        } label: {
            Label("Options", systemImage: isFiltering ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
        }
    }

    private var isFiltering: Bool { unseenOnly || type != nil }

    private func clearFilters() {
        unseenOnly = false
        type = nil
    }

    private var subtitle: String {
        guard !saves.isEmpty else { return "" }
        let unseen = saves.count { $0.openedAt == nil }
        return unseen == 0 ? "\(saves.count) saved" : "\(saves.count) saved · \(unseen) unseen"
    }

    private var greeting: String {
        switch Calendar.current.component(.hour, from: .now) {
        case 5..<12: "Good morning"
        case 12..<18: "Good afternoon"
        default: "Good evening"
        }
    }
}

private struct EmptyStashView: View {
    var body: some View {
        ContentUnavailableView {
            Label("Your stash is empty.", systemImage: "tray")
                .symbolEffect(.breathe)
        } description: {
            Text("When you find something worth keeping,\nshare it to Stash.")
        }
    }
}

#Preview {
    HomeView()
        .modelContainer(.preview)
}
