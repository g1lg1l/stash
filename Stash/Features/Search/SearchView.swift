import SwiftData
import SwiftUI

struct SearchView: View {
    @Query(sort: \Save.lastSavedAt, order: .reverse) private var saves: [Save]
    @State private var query = ""

    var body: some View {
        NavigationStack {
            let results = SearchService.results(for: query, in: saves)
            List {
                if query.isEmpty {
                    Section("Recently Saved") {
                        ForEach(saves.prefix(8)) { row($0) }
                    }
                } else {
                    ForEach(results) { row($0) }
                }
            }
            .listStyle(.plain)
            .overlay {
                if !query.isEmpty, results.isEmpty {
                    ContentUnavailableView.search(text: query)
                } else if saves.isEmpty {
                    ContentUnavailableView(
                        "Nothing to search yet",
                        systemImage: "magnifyingglass",
                        description: Text("Everything you share to Stash becomes searchable here.")
                    )
                }
            }
            .navigationTitle("Search")
            .navigationDestination(for: Save.self) { SaveDetailView(save: $0) }
            .searchable(text: $query, prompt: "Titles, tags, sources")
        }
    }

    private func row(_ save: Save) -> some View {
        NavigationLink(value: save) {
            SaveRow(save: save)
        }
        .saveActions(save)
    }
}

#Preview {
    SearchView()
        .modelContainer(.preview)
}
