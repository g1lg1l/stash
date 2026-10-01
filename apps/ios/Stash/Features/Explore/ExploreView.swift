import SwiftData
import SwiftUI

struct ExploreView: View {
    @Query(sort: \Save.lastSavedAt, order: .reverse) private var saves: [Save]
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @AppStorage("showRediscovery") private var showRediscovery = true

    var body: some View {
        NavigationStack {
            ScrollView {
                let picks = showRediscovery ? Rediscovery.picks(from: saves) : []
                if !picks.isEmpty {
                    RediscoveryRow(saves: picks)
                        .padding(.bottom, Spacing.l)
                    Text("Categories")
                        .font(.title2.bold())
                        .accessibilityAddTraits(.isHeader)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, Spacing.m)
                }
                LazyVGrid(columns: columns, spacing: Spacing.m) {
                    ForEach(categories, id: \.category) { group in
                        NavigationLink(value: group.category) {
                            CategoryTile(category: group.category, saves: group.saves)
                        }
                        .buttonStyle(PressableStyle())
                    }
                }
                .padding(.horizontal, Spacing.m)
                .padding(.bottom, Spacing.xl)
            }
            .overlay {
                if saves.isEmpty {
                    ContentUnavailableView(
                        "Nothing to explore yet",
                        systemImage: "square.grid.2x2",
                        description: Text("Categories appear here as your stash grows.")
                    )
                }
            }
            .navigationTitle("Explore")
            .navigationDestination(for: Category.self) { CategoryView(category: $0) }
        }
    }

    /// Only categories the user actually has, biggest first. "Other" is a leftover bucket, so it goes last.
    private var categories: [(category: Category, saves: [Save])] {
        Dictionary(grouping: saves, by: \.category)
            .map { (category: $0.key, saves: $0.value) }
            .sorted { a, b in
                (a.category == .other ? 1 : 0, b.saves.count, a.category.displayName)
                    < (b.category == .other ? 1 : 0, a.saves.count, b.category.displayName)
            }
    }

    private var columns: [GridItem] {
        let count = dynamicTypeSize.isAccessibilitySize ? 1 : 2
        return Array(repeating: GridItem(.flexible(), spacing: Spacing.m), count: count)
    }
}

private struct RediscoveryRow: View {
    let saves: [Save]
    @ScaledMetric(relativeTo: .headline) private var cardWidth: CGFloat = 240

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text("Worth another look")
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)
                Text("You saved these a while ago.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.m)
    
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: Spacing.m) {
                    ForEach(saves) { save in
                        // A plain destination link: Save values are already routed by the category feed.
                        NavigationLink {
                            SaveDetailView(save: save)
                        } label: {
                            VStack(alignment: .leading, spacing: Spacing.xs) {
                                SaveThumbnail(save: save)
                                    .aspectRatio(4 / 3, contentMode: .fit)
                                    .clipShape(.rect(cornerRadius: Radius.card))
                                Text(save.displayTitle)
                                    .font(.headline)
                                    .lineLimit(2, reservesSpace: true)
                                    .multilineTextAlignment(.leading)
                                Text("Saved \(save.lastSavedAt, format: .relative(presentation: .named))")
                                    .font(.meta)
                                    .foregroundStyle(.secondary)
                            }
                            .frame(width: min(cardWidth, 320))
                            .accessibilityElement(children: .combine)
                        }
                        .buttonStyle(PressableStyle())
                        .contentShape(.contextMenuPreview, .rect(cornerRadius: Radius.card))
                        .saveActions(save)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.viewAligned)
            .scrollIndicators(.hidden)
            .contentMargins(.horizontal, Spacing.m, for: .scrollContent)
        }
    }
}

/// Cover image from the newest save that has one; tinted placeholder otherwise.
private struct CategoryTile: View {
    let category: Category
    let saves: [Save]

    var body: some View {
        SaveThumbnail(save: saves.first { $0.thumbnailURL != nil } ?? saves[0])
            .aspectRatio(4 / 5, contentMode: .fit)
            .overlay(alignment: .bottomLeading) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(category.displayName)
                        .font(.title3.bold())
                    Text(saves.count == 1 ? "1 save" : "\(saves.count) saves")
                        .font(.subheadline.weight(.medium))
                        .opacity(0.85)
                }
                .foregroundStyle(.white)
                .padding(Spacing.m)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .top, endPoint: .bottom))
            }
            .clipShape(.rect(cornerRadius: Radius.card))
            .accessibilityElement(children: .combine)
    }
}

private struct CategoryView: View {
    let category: Category
    @Query private var saves: [Save]

    init(category: Category) {
        self.category = category
        let raw = category.rawValue
        _saves = Query(filter: #Predicate<Save> { $0.categoryRaw == raw }, sort: \.lastSavedAt, order: .reverse)
    }

    var body: some View {
        SaveFeed(sections: [FeedSection(title: nil, saves: saves)])
            .navigationTitle(category.displayName)
            .navigationSubtitle(saves.count == 1 ? "1 save" : "\(saves.count) saves")
    }
}

#Preview {
    ExploreView()
        .modelContainer(.preview)
}
