import Foundation
import SwiftData

/// Realistic saves for previews and the `-sampleData` launch argument.
enum SampleData {
    static func insert(into context: ModelContext) {
        saves().forEach(context.insert)
    }

    static func saves(now: Date = .now) -> [Save] {
        func ago(hours: Double) -> Date { now.addingTimeInterval(-hours * 3600) }
        func wiki(_ path: String) -> URL { URL(string: "https://upload.wikimedia.org/wikipedia/commons/thumb/\(path)")! }

        return [
            Save(
                url: URL(string: "https://www.tiktok.com/@weeknightkitchen/video/7401928374650182913")!,
                source: .tiktok, contentType: .video,
                createdAt: ago(hours: 0.2)
            ),
            Save(
                url: URL(string: "https://www.youtube.com/watch?v=kCc8FmEb1nY")!,
                source: .youtube, contentType: .video, category: .tech, status: .enriched,
                title: "Let's build GPT: from scratch, in code, spelled out.",
                descriptionText: "We build a Generatively Pretrained Transformer (GPT), following the paper \"Attention is All You Need\".",
                thumbnailURL: URL(string: "https://i.ytimg.com/vi/kCc8FmEb1nY/hqdefault.jpg"),
                author: "Andrej Karpathy",
                tags: ["ai", "transformers", "python"],
                createdAt: ago(hours: 2)
            ),
            Save(
                url: URL(string: "https://www.instagram.com/reel/C8x2kLmN4pQ/")!,
                source: .instagram, contentType: .post, category: .food, status: .enriched,
                title: "Creamy cacio e pepe in 10 minutes",
                descriptionText: "Three ingredients, one pan, no cream.",
                thumbnailURL: wiki("9/99/Cacio_e_pepe.jpg/960px-Cacio_e_pepe.jpg"),
                author: "@weeknight.pasta",
                tags: ["pasta", "recipe", "quick"],
                createdAt: ago(hours: 5)
            ),
            Save(
                url: URL(string: "https://maps.google.com/?q=Senso-ji+Temple,+Asakusa,+Tokyo")!,
                source: .maps, contentType: .place, category: .places, status: .enriched,
                title: "Sensō-ji",
                descriptionText: "Tokyo's oldest temple, in Asakusa.",
                thumbnailURL: wiki("4/43/Sensoji_2023.jpg/960px-Sensoji_2023.jpg"),
                tags: ["tokyo", "japan", "temple"],
                createdAt: ago(hours: 26)
            ),
            Save(
                url: URL(string: "https://paulgraham.com/startupideas.html")!,
                source: .web, contentType: .article, category: .business, status: .enriched,
                title: "How to Get Startup Ideas",
                descriptionText: "The way to get startup ideas is not to try to think of startup ideas. It's to look for problems.",
                author: "Paul Graham",
                tags: ["startups", "essay"],
                createdAt: ago(hours: 30)
            ),
            Save(
                url: URL(string: "https://x.com/indiehacker_jo/status/1839201928374650182")!,
                source: .x, contentType: .post, category: .ideas, status: .enriched,
                title: "12 lessons from bootstrapping to $1M ARR",
                descriptionText: "1/ Charge from day one. Free users tell you what they like; paying users tell you what they need.",
                author: "@indiehacker_jo",
                tags: ["bootstrapping", "saas"],
                createdAt: ago(hours: 50)
            ),
            Save(
                url: URL(string: "https://open.spotify.com/album/4m2880jivSbbyEGAKfITCa")!,
                source: .spotify, contentType: .music, category: .music, status: .enriched,
                title: "Random Access Memories",
                thumbnailURL: URL(string: "https://image-cdn-fa.spotifycdn.com/image/ab67616d00001e029b9b36b0e22870b9f542d937"),
                author: "Daft Punk",
                tags: ["album", "electronic"],
                createdAt: ago(hours: 70)
            ),
            Save(
                url: URL(string: "https://www.youtube.com/shorts/Qm3vX8pLw2E")!,
                source: .youtube, contentType: .video, category: .travel, status: .enriched,
                title: "Ice Lakes trail in 60 seconds",
                thumbnailURL: wiki("a/a9/Hiking_to_the_Ice_Lakes._San_Juan_National_Forest%2C_Colorado.jpg/960px-Hiking_to_the_Ice_Lakes._San_Juan_National_Forest%2C_Colorado.jpg"),
                author: "Trail Notes",
                tags: ["hiking", "colorado"],
                createdAt: ago(hours: 75)
            ),
            Save(
                url: URL(string: "https://www.reddit.com/r/bodyweightfitness/comments/1f2k9xq/the_recommended_routine/")!,
                source: .reddit, contentType: .post, category: .fitness, status: .enriched,
                title: "The Recommended Routine: a full beginner program",
                descriptionText: "Warm-up, strength work in pairs, and how to progress each week.",
                author: "r/bodyweightfitness",
                tags: ["calisthenics", "program"],
                createdAt: ago(hours: 100)
            ),
            Save(
                url: URL(string: "https://www.youtube.com/watch?v=UF8uR6Z6KLc")!,
                source: .youtube, contentType: .video, category: .ideas, status: .enriched,
                title: "Steve Jobs' 2005 Stanford Commencement Address",
                thumbnailURL: URL(string: "https://i.ytimg.com/vi/UF8uR6Z6KLc/hqdefault.jpg"),
                author: "Stanford",
                tags: ["speech", "career"],
                createdAt: ago(hours: 150)
            ),
            Save(
                url: URL(string: "https://www.roguefitness.com/rogue-kettlebells")!,
                source: .web, contentType: .product, category: .shopping, status: .enriched,
                title: "Rogue Kettlebells",
                descriptionText: "Single-piece cast iron, 9–203 lb.",
                thumbnailURL: wiki("1/14/Competition_kettlebell_16_kilo.jpg/960px-Competition_kettlebell_16_kilo.jpg"),
                tags: ["kettlebell", "home gym"],
                createdAt: ago(hours: 190)
            ),
            Save(
                url: URL(string: "https://maps.google.com/?q=Tsukiji+Outer+Market,+Tokyo")!,
                source: .maps, contentType: .place, category: .places, status: .enriched,
                title: "Tsukiji Outer Market",
                descriptionText: "Street food, knives and fresh seafood. Go early.",
                thumbnailURL: wiki("f/fd/2018_Tsukiji_fish_market.jpg/960px-2018_Tsukiji_fish_market.jpg"),
                tags: ["tokyo", "japan", "market"],
                createdAt: ago(hours: 220)
            ),
            Save(
                url: URL(string: "https://newsletter.example.com/p/the-quiet-power-of-slow-mornings")!,
                source: .web, contentType: .article, status: .failed,
                createdAt: ago(hours: 260)
            ),
            Save(
                url: URL(string: "https://chemexcoffeemaker.com/products/six-cup-classic-series-coffeemaker")!,
                source: .web, contentType: .product, category: .shopping, status: .enriched,
                title: "Six Cup Classic Chemex",
                descriptionText: "Hand-blown glass pour-over, designed in 1941.",
                thumbnailURL: wiki("a/a9/Peter_Schlumbohm._Coffee_Maker%2C_Designed_1941.jpg/960px-Peter_Schlumbohm._Coffee_Maker%2C_Designed_1941.jpg"),
                tags: ["coffee", "kitchen"],
                createdAt: ago(hours: 290)
            ),
            Save(
                url: URL(string: "https://www.japan-guide.com/e/e2158.html")!,
                source: .web, contentType: .article, category: .travel, status: .enriched,
                title: "Kyoto Travel Guide",
                descriptionText: "Temples, gardens and where to stay in Japan's former capital.",
                thumbnailURL: wiki("6/6b/Kyoto%2C_Japan_%2849667780482%29.jpg/960px-Kyoto%2C_Japan_%2849667780482%29.jpg"),
                tags: ["kyoto", "japan", "guide"],
                createdAt: ago(hours: 480),
                openedAt: ago(hours: 400)
            ),
        ]
    }
}

extension ModelContainer {
    /// In-memory container seeded with `SampleData`, for SwiftUI previews.
    @MainActor static let preview: ModelContainer = {
        let container = try! ModelContainer.stash(inMemory: true)
        SampleData.insert(into: container.mainContext)
        return container
    }()
}
