import SwiftUI

enum FeedLayout: String {
    case cards, compact
}

struct FeedSection: Identifiable {
    let title: String?
    var saves: [Save]
    var id: String { title ?? "" }

    /// Today / Yesterday / Last 7 Days / Earlier. Expects saves sorted newest first.
    static func byDay(_ saves: [Save], now: Date = .now, calendar: Calendar = .current) -> [FeedSection] {
        let yesterday = calendar.date(byAdding: .day, value: -1, to: now)!
        let weekAgo = calendar.date(byAdding: .day, value: -7, to: calendar.startOfDay(for: now))!
        var sections: [FeedSection] = []
        for save in saves {
            let date = save.lastSavedAt
            let title = if calendar.isDate(date, inSameDayAs: now) { "Today" }
                else if calendar.isDate(date, inSameDayAs: yesterday) { "Yesterday" }
                else if date >= weekAgo { "Last 7 Days" }
                else { "Earlier" }
            if sections.last?.title == title {
                sections[sections.count - 1].saves.append(save)
            } else {
                sections.append(FeedSection(title: title, saves: [save]))
            }
        }
        return sections
    }
}

/// Saves as large cards or compact rows, following the layout chosen on Home.
struct SaveFeed: View {
    let sections: [FeedSection]
    @AppStorage("feedLayout") private var layout: FeedLayout = .cards
    @Namespace private var zoom

    var body: some View {
        Group {
            switch layout {
            case .cards:
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: Spacing.xl) {
                        ForEach(sections) { section in
                            if let title = section.title {
                                Text(title)
                                    .font(.title2.bold())
                                    .accessibilityAddTraits(.isHeader)
                                    .padding(.bottom, -Spacing.s)
                            }
                            ForEach(section.saves) { save in
                                NavigationLink(value: save) {
                                    SaveCard(save: save)
                                }
                                .buttonStyle(PressableStyle())
                                .contentShape(.contextMenuPreview, .rect(cornerRadius: Radius.card))
                                .saveActions(save)
                                .matchedTransitionSource(id: save.id, in: zoom)
                            }
                        }
                    }
                    .padding(.horizontal, Spacing.m)
                    .padding(.bottom, Spacing.xl)
                }
            case .compact:
                List {
                    ForEach(sections) { section in
                        Section {
                            ForEach(section.saves) { save in
                                NavigationLink(value: save) {
                                    SaveRow(save: save)
                                }
                                .saveActions(save)
                                .matchedTransitionSource(id: save.id, in: zoom)
                            }
                        } header: {
                            if let title = section.title { Text(title) }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationDestination(for: Save.self) { save in
            SaveDetailView(save: save)
                .navigationTransition(.zoom(sourceID: save.id, in: zoom))
        }
    }
}
