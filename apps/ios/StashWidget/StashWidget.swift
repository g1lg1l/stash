import ImageIO
import SwiftData
import SwiftUI
import WidgetKit

/// One save on the Home Screen: something worth another look, else the newest unseen one. Tapping opens it in Stash.
@main
struct StashWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "StashWidget", provider: Provider()) { entry in
            StashWidgetView(entry: entry)
        }
        .configurationDisplayName("Worth Another Look")
        .description("Brings back something you saved.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct Entry: TimelineEntry {
    struct Pick: Sendable {
        let id: UUID
        let title: String
        let byline: String
        let category: Category
        let savedAt: Date
        var image: Data?
    }

    var date = Date.now
    var headline = "Worth another look"
    var pick: Pick?

    static let sample = Entry(pick: Pick(id: UUID(), title: "Creamy cacio e pepe in 10 minutes", byline: "@weeknight.pasta",
                                         category: .food, savedAt: .now.addingTimeInterval(-5 * 24 * 3600)))
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry { .sample }

    func getSnapshot(in context: Context, completion: @escaping @Sendable (Entry) -> Void) {
        if context.isPreview { return completion(.sample) }
        Task { completion(await Self.entry()) }
    }

    func getTimeline(in context: Context, completion: @escaping @Sendable (Timeline<Entry>) -> Void) {
        // The app reloads this whenever it goes to the background; the date policy catches saves aging into rediscovery.
        Task { completion(Timeline(entries: [await Self.entry()], policy: .after(.now.addingTimeInterval(4 * 3600)))) }
    }

    /// Reads the app's store. Read-only by convention: the app is the only writer.
    static func entry() async -> Entry {
        guard let container = try? ModelContainer.stash() else { return Entry(pick: nil) }
        let saves = (try? ModelContext(container).fetch(FetchDescriptor<Save>(sortBy: [SortDescriptor(\.lastSavedAt, order: .reverse)]))) ?? []
        let rediscovered = Rediscovery.picks(from: saves, limit: 1).first
        guard let save = rediscovered ?? saves.first(where: { $0.openedAt == nil }) ?? saves.first else { return Entry(pick: nil) }

        var pick = Entry.Pick(id: save.id, title: save.displayTitle, byline: save.byline, category: save.category, savedAt: save.lastSavedAt)
        if let url = save.thumbnailURL { pick.image = await thumbnail(from: url) }
        let headline = rediscovered != nil ? "Worth another look" : save.openedAt == nil ? "Waiting for you" : "Latest save"
        return Entry(headline: headline, pick: pick)
    }

    /// Widgets can't load images themselves, and big ones are dropped: fetch and shrink here.
    private static func thumbnail(from url: URL) async -> Data? {
        guard let (data, _) = try? await URLSession.shared.data(from: url),
              let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  kCGImageSourceThumbnailMaxPixelSize: 600,
              ] as CFDictionary)
        else { return nil }
        return UIImage(cgImage: image).jpegData(compressionQuality: 0.8)
    }
}

struct StashWidgetView: View {
    let entry: Entry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if let pick = entry.pick {
            Group {
                if family == .systemSmall { small(pick) } else { medium(pick) }
            }
            .widgetURL(URL(string: "stash://save/\(pick.id.uuidString)"))
        } else {
            VStack(alignment: .leading, spacing: 2) {
                Image(systemName: "tray")
                    .font(.title2)
                    .foregroundStyle(Color(.accent))
                Spacer()
                Text("Your stash is empty")
                    .font(.headline)
                Text("Share a link to Stash.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .containerBackground(.background, for: .widget)
        }
    }

    private func small(_ pick: Entry.Pick) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            if pick.image == nil {
                Image(systemName: pick.category.symbol)
                    .font(.title2)
            }
            Spacer()
            Text(entry.headline.uppercased())
                .font(.caption2.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .opacity(0.85)
            Text(pick.title)
                .font(.headline)
                .lineLimit(3)
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, alignment: .leading)
        .containerBackground(for: .widget) {
            artwork(pick, symbol: false)
                .overlay(LinearGradient(colors: [.clear, .black.opacity(pick.image == nil ? 0.25 : 0.7)], startPoint: .center, endPoint: .bottom))
        }
    }

    private func medium(_ pick: Entry.Pick) -> some View {
        HStack(spacing: 12) {
            artwork(pick)
                .frame(width: 120)
                .frame(maxHeight: .infinity)
                .clipShape(.rect(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 4) {
                Text(entry.headline)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color(.accent))
                Text(pick.title)
                    .font(.headline)
                    .lineLimit(3)
                Spacer(minLength: 0)
                Text("\(pick.byline) · \(pick.savedAt, format: .relative(presentation: .named))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .containerBackground(.background, for: .widget)
    }

    /// The thumbnail, or the category's tint and symbol when there isn't one (same fallback as the app).
    private func artwork(_ pick: Entry.Pick, symbol: Bool = true) -> some View {
        Rectangle()
            .fill(pick.category.tint.gradient)
            .overlay {
                if let data = pick.image, let image = UIImage(data: data) {
                    Image(uiImage: image).resizable().scaledToFill()
                } else if symbol {
                    Image(systemName: pick.category.symbol)
                        .font(.title)
                        .foregroundStyle(.white)
                }
            }
            .clipped()
    }
}

#Preview(as: .systemMedium) {
    StashWidget()
} timeline: {
    Entry.sample
    Entry(pick: nil)
}
