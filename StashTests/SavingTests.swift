import Foundation
import SwiftData
import Testing
@testable import Stash

struct URLSourceDetectorTests {
    private func analyze(_ string: String) throws -> URLAnalysis {
        try #require(URLSourceDetector.analyze(string))
    }

    @Test(arguments: [
        ("https://www.instagram.com/reel/C8x2kLmN4pQ/?igsh=abc", Source.instagram, ContentType.video),
        ("https://instagram.com/p/C8x2kLmN4pQ/", .instagram, .post),
        ("https://www.youtube.com/watch?v=kCc8FmEb1nY", .youtube, .video),
        ("https://youtu.be/kCc8FmEb1nY?si=xyz", .youtube, .video),
        ("https://youtube.com/shorts/Qm3vX8pLw2E", .youtube, .video),
        ("https://music.youtube.com/watch?v=abc", .youtube, .music),
        ("https://www.tiktok.com/@chef/video/7401928374650182913", .tiktok, .video),
        ("https://vm.tiktok.com/ZMabc123/", .tiktok, .video),
        ("https://old.reddit.com/r/swift/comments/abc/title/", .reddit, .post),
        ("https://x.com/jack/status/20", .x, .post),
        ("https://mobile.twitter.com/jack/status/20", .x, .post),
        ("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=1", .spotify, .music),
        ("https://maps.google.com/?q=Senso-ji", .maps, .place),
        ("https://www.google.com/maps/place/Senso-ji", .maps, .place),
        ("https://maps.app.goo.gl/AbC123", .maps, .place),
        ("https://maps.apple.com/?q=Kyoto", .maps, .place),
        ("https://paulgraham.com/startupideas.html", .web, .article),
        ("https://www.amazon.com/dp/B0CHX1W1XY", .web, .product),
        ("https://example.com", .web, .other),
    ])
    func detectsSourceAndType(url: String, source: Source, type: ContentType) throws {
        let analysis = try analyze(url)
        #expect(analysis.source == source)
        #expect(analysis.contentType == type)
    }

    @Test func acceptsLinksWithoutScheme() throws {
        let analysis = try analyze("  youtu.be/kCc8FmEb1nY\n")
        #expect(analysis.url.absoluteString == "https://youtu.be/kCc8FmEb1nY")
        #expect(analysis.source == .youtube)
    }

    @Test(arguments: ["", "not a url", "mailto:hi@example.com", "tel:+391234", "spotify:track:123",
                      "hi@example.com", "ftp://example.com/file", "localhost", "https://", "https://.com"])
    func rejectsNonWebLinks(input: String) {
        #expect(URLSourceDetector.analyze(input) == nil)
    }

    @Test func keepsTheSharedURLAsSourceOfTruth() throws {
        let shared = "https://www.youtube.com/watch?v=kCc8FmEb1nY&t=42s&si=abc"
        #expect(try analyze(shared).url.absoluteString == shared)
    }

    @Test(arguments: [
        // Same video however it's linked.
        ["https://www.youtube.com/watch?v=kCc8FmEb1nY", "https://youtu.be/kCc8FmEb1nY?si=abc",
         "https://m.youtube.com/watch?v=kCc8FmEb1nY&t=42s", "https://youtube.com/shorts/kCc8FmEb1nY",
         "http://youtube.com/embed/kCc8FmEb1nY"],
        // Same post, any handle, any host alias, share tokens dropped.
        ["https://x.com/jack/status/20", "https://twitter.com/someone/status/20?s=20&t=abc"],
        ["https://www.instagram.com/reel/C8x/?igsh=1", "https://instagram.com/reel/C8x"],
        ["https://www.reddit.com/r/swift/comments/abc/t/", "https://old.reddit.com/r/swift/comments/abc/t?share_id=9"],
        // Tracking parameters, anchors, trailing slashes, www and http don't make a new page.
        ["https://example.com/post?utm_source=x&utm_medium=y", "http://www.example.com/post/#comments",
         "https://example.com/post?fbclid=123"],
        ["https://shop.example.com/item?id=5&color=red", "https://shop.example.com/item?color=red&id=5&gclid=1"],
    ])
    func sameResourceSharesOneKey(urls: [String]) throws {
        let keys = try Set(urls.map { try analyze($0).canonicalURL })
        #expect(keys.count == 1, "\(keys)")
    }

    @Test(arguments: [
        ("https://www.youtube.com/watch?v=aaa", "https://www.youtube.com/watch?v=bbb"),
        ("https://www.youtube.com/playlist?list=PL1", "https://www.youtube.com/playlist?list=PL2"),
        ("https://shop.example.com/item?id=5", "https://shop.example.com/item?id=6"),
        ("https://example.com/Page", "https://example.com/page"),
        ("https://app.example.com/#/inbox", "https://app.example.com/#/settings"),
        ("https://example.com/post", "https://blog.example.com/post"),
        ("https://instagram.com/p/C8x", "https://instagram.com/reel/C8x"),
    ])
    func differentResourcesNeverMerge(a: String, b: String) throws {
        #expect(try analyze(a).canonicalURL != analyze(b).canonicalURL)
    }
}

@MainActor
struct SaveStoreTests {
    let container: ModelContainer
    var context: ModelContext { container.mainContext }

    init() throws {
        container = try .stash(inMemory: true)
    }

    private func add(_ string: String, at date: Date) throws -> Save {
        try SaveStore.add(#require(URLSourceDetector.analyze(string)), in: context, at: date)
    }

    @Test func savingTheSameResourceAgainUpdatesLastSavedAt() throws {
        let first = Date(timeIntervalSince1970: 1_000)
        let again = Date(timeIntervalSince1970: 2_000)
        let original = try add("https://www.youtube.com/watch?v=kCc8FmEb1nY", at: first)
        let duplicate = try add("https://youtu.be/kCc8FmEb1nY?si=share", at: again)

        #expect(duplicate.id == original.id)
        #expect(try context.fetchCount(FetchDescriptor<Save>()) == 1)
        #expect(original.createdAt == first)
        #expect(original.lastSavedAt == again)
        #expect(original.url.absoluteString == "https://www.youtube.com/watch?v=kCc8FmEb1nY")
    }

    @Test func anOlderDuplicateKeepsTheEarliestCreatedAt() throws {
        let later = Date(timeIntervalSince1970: 2_000)
        let earlier = Date(timeIntervalSince1970: 1_000)
        let save = try add("https://x.com/jack/status/20", at: later)
        _ = try add("https://twitter.com/jack/status/20", at: earlier)
        #expect(save.createdAt == earlier)
        #expect(save.lastSavedAt == later)
    }

    @Test func differentResourcesAreSeparateSaves() throws {
        _ = try add("https://www.youtube.com/watch?v=aaa", at: .now)
        _ = try add("https://www.youtube.com/watch?v=bbb", at: .now)
        #expect(try context.fetchCount(FetchDescriptor<Save>()) == 2)
    }

    @Test func newSavesStartUnseenAndPendingWithDetectedSource() throws {
        let save = try add("https://open.spotify.com/album/4m2880jivSbbyEGAKfITCa", at: .now)
        #expect(save.source == .spotify)
        #expect(save.contentType == .music)
        #expect(save.status == .pending)
        #expect(save.openedAt == nil)
    }

    @Test func deleteRemovesTheSaveAndItsCachedThumbnail() async throws {
        let save = try add("https://example.com/with-image", at: .now)
        let thumbnail = URL(string: "https://cdn.example.com/\(UUID().uuidString).jpg")!
        save.thumbnailURL = thumbnail
        let request = URLRequest(url: thumbnail)
        let response = HTTPURLResponse(url: thumbnail, statusCode: 200, httpVersion: nil, headerFields: ["Cache-Control": "max-age=3600"])!
        URLCache.shared.storeCachedResponse(CachedURLResponse(response: response, data: Data([1, 2, 3])), for: request)
        #expect(URLCache.shared.cachedResponse(for: request) != nil)

        SaveStore.delete(save, in: context)

        #expect(try context.fetchCount(FetchDescriptor<Save>()) == 0)
        // URLCache removes from disk in the background: an entry already written stays readable for a moment.
        var waited = 0
        while URLCache.shared.cachedResponse(for: request) != nil, waited < 40 {
            try await Task.sleep(for: .milliseconds(50))
            waited += 1
        }
        #expect(URLCache.shared.cachedResponse(for: request) == nil)
    }

    @Test func aManualCategorySticksThroughEnrichment() throws {
        let save = try add("https://example.com/posts/42", at: .now)
        save.setCategory(.other) // Deliberately "Other": the case classification would otherwise touch.
        MetadataEnricher.apply(.success(LinkMetadata(title: "Weeknight pasta recipe")), to: save)
        #expect(save.category == .other)
        #expect(save.title == "Weeknight pasta recipe")
    }

    @Test func deleteRemovesTheSave() throws {
        let save = try add("https://example.com/a", at: .now)
        context.delete(save)
        try context.save()
        #expect(try context.fetchCount(FetchDescriptor<Save>()) == 0)
    }
}

@MainActor
struct ShareInboxTests {
    let container: ModelContainer
    let directory = FileManager.default.temporaryDirectory.appending(path: "inbox-\(UUID().uuidString)")

    init() throws {
        container = try .stash(inMemory: true)
    }

    private var pendingFiles: [URL] {
        (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
    }

    @Test func drainImportsSharesAndEmptiesTheInbox() throws {
        let sharedAt = Date(timeIntervalSince1970: 5_000)
        try ShareInbox.add(URL(string: "https://youtu.be/kCc8FmEb1nY")!, at: sharedAt, to: directory)
        try ShareInbox.add(URL(string: "https://www.youtube.com/watch?v=kCc8FmEb1nY")!, at: sharedAt.addingTimeInterval(60), to: directory)
        try ShareInbox.add(URL(string: "https://paulgraham.com/startupideas.html")!, at: sharedAt, to: directory)
        #expect(pendingFiles.count == 3)

        ShareInbox.drain(from: directory, into: container.mainContext)

        let saves = try container.mainContext.fetch(FetchDescriptor<Save>())
        #expect(saves.count == 2) // The two YouTube links are one video.
        #expect(Set(saves.map(\.source)) == [.youtube, .web])
        #expect(saves.first { $0.source == .youtube }?.createdAt == sharedAt)
        #expect(pendingFiles.isEmpty)
    }

    @Test func unreadableFilesAreDroppedWithoutBlockingOthers() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("garbage".utf8).write(to: directory.appending(path: "broken.json"))
        try ShareInbox.add(URL(string: "https://example.com/ok")!, to: directory)

        ShareInbox.drain(from: directory, into: container.mainContext)

        #expect(try container.mainContext.fetchCount(FetchDescriptor<Save>()) == 1)
        #expect(pendingFiles.isEmpty)
    }

    @Test func missingInboxIsANoOp() {
        #expect(ShareInbox.drain(from: directory, into: container.mainContext).isEmpty)
    }
}

struct SharedTextLinkTests {
    @Test func findsTheLinkInsideSharedText() {
        let text = "Check this out 😍 https://www.instagram.com/reel/C8x2kLmN4pQ/?igsh=abc via @friend"
        #expect(URLSourceDetector.firstLink(in: text)?.absoluteString == "https://www.instagram.com/reel/C8x2kLmN4pQ/?igsh=abc")
    }

    @Test func ignoresTextWithoutWebLinks() {
        #expect(URLSourceDetector.firstLink(in: "call me at +39 333 1234567 or hi@example.com") == nil)
    }
}
