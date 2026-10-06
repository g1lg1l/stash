import Foundation
import SwiftData
import Testing
@testable import Stash

@MainActor
struct PersistenceTests {
    let container: ModelContainer

    init() throws {
        container = try .stash(inMemory: true)
    }

    @Test func saveRoundTrips() throws {
        let url = URL(string: "https://www.youtube.com/watch?v=kCc8FmEb1nY")!
        container.mainContext.insert(Save(url: url, source: .youtube, contentType: .video, category: .tech, tags: ["ai"]))
        try container.mainContext.save()

        let save = try #require(try container.mainContext.fetch(FetchDescriptor<Save>()).first)
        #expect(save.url == url)
        #expect(save.canonicalURL == url.absoluteString)
        #expect(save.source == .youtube)
        #expect(save.contentType == .video)
        #expect(save.category == .tech)
        #expect(save.status == .pending)
        #expect(save.tags == ["ai"])
        #expect(save.lastSavedAt == save.createdAt)
        #expect(save.openedAt == nil)
    }

    @Test func rawEnumColumnsWorkInPredicates() throws {
        SampleData.insert(into: container.mainContext)
        let places = Category.places.rawValue
        let fetched = try container.mainContext.fetch(FetchDescriptor<Save>(predicate: #Predicate { $0.categoryRaw == places }))

        #expect(!fetched.isEmpty)
        #expect(fetched.count == SampleData.saves().filter { $0.category == .places }.count)
    }

    @Test func unknownRawValuesFallBack() {
        let save = Save(url: URL(string: "https://example.com")!)
        save.sourceRaw = "myspace"
        save.contentTypeRaw = "hologram"
        save.categoryRaw = "gardening"
        save.statusRaw = "exploded"

        #expect(save.source == .unknown)
        #expect(save.contentType == .other)
        #expect(save.category == .other)
        #expect(save.status == .pending)
    }

    @Test func sampleDataHasNoDuplicateURLs() {
        let urls = SampleData.saves().map(\.canonicalURL)
        #expect(Set(urls).count == urls.count)
    }

    @Test func appGroupComesFromTheProfile() {
        let plist = """
            <?xml version="1.0" encoding="UTF-8"?><plist version="1.0"><dict><key>Entitlements</key><dict>\
            <key>com.apple.security.application-groups</key><array><string>group.com.g1lg1l.stash.ABCDE12345</string></array>\
            </dict></dict></plist>
            """
        let profile = Data([0x30, 0x82, 0x00]) + Data(plist.utf8) + Data([0xA0, 0x00])
        #expect(ModelContainer.provisionedAppGroup(profile) == "group.com.g1lg1l.stash.ABCDE12345")
        #expect(ModelContainer.provisionedAppGroup(Data("not a profile".utf8)) == nil)
        #expect(ModelContainer.provisionedAppGroup(nil) == nil)
    }

    @Test func appGroupContainerIsReachable() {
        #expect(FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: ModelContainer.appGroupID) != nil)
    }
}
