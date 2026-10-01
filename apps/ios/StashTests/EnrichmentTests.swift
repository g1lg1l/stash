import Foundation
import SwiftData
import Testing
@testable import Stash

struct MetadataParsingTests {
    let base = URL(string: "https://blog.example.com/posts/pasta")!

    @Test func readsOpenGraphInAnyAttributeOrder() {
        let html = """
        <html><head>
        <title>Fallback title</title>
        <meta content="Creamy cacio e pepe" property="og:title">
        <meta property='og:description' content='Three ingredients &amp; one pan.'>
        <meta property="og:image" content="/img/cacio.jpg" />
        <meta name="author" content="Marta Rossi">
        <meta property="og:type" content="article">
        </head><body><meta property="og:title" content="Not in head"></body></html>
        """
        let metadata = MetadataService.parseHTML(html, baseURL: base)
        #expect(metadata.title == "Creamy cacio e pepe")
        #expect(metadata.description == "Three ingredients & one pan.")
        #expect(metadata.imageURL?.absoluteString == "https://blog.example.com/img/cacio.jpg")
        #expect(metadata.author == "Marta Rossi")
        #expect(metadata.contentType == .article)
    }

    @Test func fallsBackToTwitterCardsAndTitleTag() {
        let html = """
        <head><TITLE>Plain &#8220;title&#8221; &#x2014; site</TITLE>
        <meta name="twitter:image" content="https://cdn.example.com/card.png">
        <meta name="description" content="Meta description">
        <meta property="article:author" content="https://facebook.com/someone"></head>
        """
        let metadata = MetadataService.parseHTML(html, baseURL: base)
        #expect(metadata.title == "Plain \u{201C}title\u{201D} \u{2014} site")
        #expect(metadata.imageURL?.absoluteString == "https://cdn.example.com/card.png")
        #expect(metadata.description == "Meta description")
        #expect(metadata.author == nil) // A profile link isn't a name.
    }

    @Test(arguments: [("video.other", .video), ("music.album", .music), ("product", .product),
                      ("og:product", .product), ("restaurant.restaurant", .place), ("website", nil)] as [(String, ContentType?)])
    func mapsOpenGraphTypes(type: String, expected: ContentType?) {
        let html = "<head><meta property=\"og:type\" content=\"\(type)\"></head>"
        #expect(MetadataService.parseHTML(html, baseURL: base).contentType == expected)
    }

    @Test func emptyPageHasNoMetadata() {
        #expect(MetadataService.parseHTML("<html><head></head></html>", baseURL: base).isEmpty)
    }

    @Test func decodesEntitiesOnce() {
        #expect(MetadataService.decodeEntities("Tom &amp; Jerry &#39;s &lt;3 &amp;lt;") == "Tom & Jerry 's <3 &lt;")
    }

    @Test func readsOEmbedIncludingXPostText() throws {
        let youtube = Data(#"{"title":"Let's build GPT","author_name":"Andrej Karpathy","thumbnail_url":"https://i.ytimg.com/vi/x/hqdefault.jpg"}"#.utf8)
        let yt = try MetadataService.parseOEmbed(youtube)
        #expect(yt.title == "Let's build GPT")
        #expect(yt.author == "Andrej Karpathy")
        #expect(yt.imageURL?.host() == "i.ytimg.com")

        let x = Data(##"{"author_name":"Jo","html":"<blockquote><p lang=\"en\">Charge from <a href=\"#\">day one</a>.</p>&mdash; Jo</blockquote>"}"##.utf8)
        #expect(try MetadataService.parseOEmbed(x).title == "Charge from day one .")
    }

    @Test func namesMapPlacesFromTheLinkItself() {
        #expect(MetadataService.placeName(in: URL(string: "https://maps.google.com/?q=Senso-ji+Temple")!) == "Senso-ji Temple")
        #expect(MetadataService.placeName(in: URL(string: "https://www.google.com/maps/place/Tsukiji+Outer+Market/@35.66,139.77,17z")!) == "Tsukiji Outer Market")
        #expect(MetadataService.placeName(in: URL(string: "https://maps.app.goo.gl/AbC123")!) == nil)
    }
}

@MainActor
struct EnrichmentApplyTests {
    let container: ModelContainer
    init() throws { container = try .stash(inMemory: true) }

    private func newSave(_ string: String) throws -> Save {
        try SaveStore.add(#require(URLSourceDetector.analyze(string)), in: container.mainContext)
    }

    @Test func successFillsGapsClassifiesAndNeverOverwrites() throws {
        let save = try newSave("https://blog.example.com/posts/42")
        #expect(save.category == .other) // Nothing to go on from the link alone.
        save.author = "Kept"
        let metadata = LinkMetadata(title: "Weeknight pasta recipe", description: "Dinner in 10 minutes",
                                    imageURL: URL(string: "https://blog.example.com/a.jpg"), author: "Replaced?", contentType: .article)
        MetadataEnricher.apply(.success(metadata), to: save)

        #expect(save.title == "Weeknight pasta recipe")
        #expect(save.author == "Kept")
        #expect(save.status == .enriched)
        #expect(save.category == .food)
    }

    @Test func offlineStaysPendingAndUnavailableFailsButKeepsTheSave() throws {
        let offline = try newSave("https://example.com/a")
        MetadataEnricher.apply(.failure(.offline), to: offline)
        #expect(offline.status == .pending)

        let empty = try newSave("https://example.com/b")
        MetadataEnricher.apply(.failure(.unavailable), to: empty)
        #expect(empty.status == .failed)
        #expect(try container.mainContext.fetchCount(FetchDescriptor<Save>()) == 2)
    }
}

struct ClassificationTests {
    private func classify(_ title: String?, description: String? = nil, tags: [String] = [],
                          url: String = "https://example.com/post", source: Source = .web, type: ContentType = .article) -> Stash.Category {
        ClassificationService.category(title: title, description: description, tags: tags,
                                       url: URL(string: url)!, source: source, contentType: type)
    }

    @Test func structureWinsOverWords() {
        #expect(classify("Best pasta in town", url: "https://maps.google.com/?q=x", source: .maps, type: .place) == .places)
        #expect(classify("Workout mix", url: "https://open.spotify.com/playlist/1", source: .spotify, type: .music) == .music)
        #expect(classify("Kettlebell", type: .product) == .shopping)
    }

    @Test(arguments: [
        ("Creamy cacio e pepe pasta in 10 minutes", Stash.Category.food),
        ("Ricetta della carbonara", .food),
        ("The Recommended Routine: a full beginner program", .fitness),
        ("Ice Lakes trail in 60 seconds", .travel),
        ("How to Get Startup Ideas", .business),
        ("Let's build GPT: from scratch, in code", .tech),
        ("Steve Jobs' 2005 Stanford Commencement Address", .ideas),
        ("Quantum physics explained", .learning),
        ("The quiet power of slow mornings", .other),
    ])
    func titles(title: String, expected: Stash.Category) {
        #expect(classify(title) == expected)
    }

    @Test func titleOutweighsBodyAndIgnoresCaseAndAccents() {
        #expect(classify("TRÈS BON RECIPE", description: "a founder cooks") == .food)
    }

    @Test func urlWordsHelpWhenThereIsNoTitle() {
        #expect(classify(nil, url: "https://example.com/recipes/lemon-pasta") == .food)
    }
}

struct TitleCleaningTests {
    @Test(arguments: [
        ("Random Access Memories - Album by Daft Punk | Spotify", ["Spotify"], "Random Access Memories - Album by Daft Punk"),
        ("Cacio e pepe - Wikipedia", ["wikipedia"], "Cacio e pepe"),
        ("Reddit", ["reddit"], nil),
        ("How to Get Startup Ideas", ["paulgraham"], "How to Get Startup Ideas"),
        ("Pipe | dream", [], "Pipe | dream"),
    ] as [(String, [String], String?)])
    func stripsSiteNames(title: String, siteNames: [String], expected: String?) {
        #expect(MetadataService.cleanTitle(title, siteNames: siteNames) == expected)
    }
}
