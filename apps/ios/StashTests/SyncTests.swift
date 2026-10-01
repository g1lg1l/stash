import Foundation
import SwiftData
import Testing
@testable import Stash

@MainActor
struct SyncTests {
    let pushStart = Date(timeIntervalSince1970: 10_000)
    let lowID = UUID(uuidString: "0a000000-0000-0000-0000-000000000000")!
    let highID = UUID(uuidString: "f0000000-0000-0000-0000-000000000000")!

    private func row(_ id: UUID, url: String = "https://example.com/a", deleted: Bool = false) -> Sync.Row {
        Sync.Row(
            id: id.uuidString.lowercased(), url: url, canonical_url: url, source: "web", content_type: "article",
            category: "tech", status: "enriched", category_is_manual: false, title: "From the server", tags: [],
            created_at: "2026-10-01T10:00:00.123456+00:00", last_saved_at: "2026-10-01T10:00:00+00:00",
            updated_at: "2026-10-01T10:00:01.5+00:00", deleted_at: deleted ? "2026-10-01T10:00:01+00:00" : nil
        )
    }

    private func local(_ id: UUID, url: String = "https://example.com/a", modifiedAt: Date) -> Save {
        let save = Save(url: URL(string: url)!)
        save.id = id
        save.modifiedAt = modifiedAt
        return save
    }

    @Test func skipsASaveChangedDuringTheSync() {
        let save = local(lowID, modifiedAt: pushStart.addingTimeInterval(1))
        #expect(Sync.action(for: row(lowID), sameID: save, sameURL: nil, pushStart: pushStart) == .skip)
        #expect(Sync.action(for: row(lowID, deleted: true), sameID: save, sameURL: nil, pushStart: pushStart) == .skip)
    }

    @Test func deletesWithoutATombstone() throws {
        let container = try ModelContainer.stash(inMemory: true)
        container.mainContext.insert(local(lowID, modifiedAt: pushStart))

        Sync.apply([row(lowID, deleted: true), row(highID, url: "https://example.com/gone", deleted: true)], in: container.mainContext, pushStart: pushStart)

        #expect(try container.mainContext.fetchCount(FetchDescriptor<Save>()) == 0)
        #expect(try container.mainContext.fetchCount(FetchDescriptor<Tombstone>()) == 0)
    }

    @Test func updatesWithoutPushingBack() throws {
        let container = try ModelContainer.stash(inMemory: true)
        let save = local(lowID, modifiedAt: pushStart.addingTimeInterval(-1))
        save.openedAt = .now
        container.mainContext.insert(save)

        Sync.apply([row(lowID)], in: container.mainContext, pushStart: pushStart)

        #expect(save.title == "From the server")
        #expect(save.openedAt == nil) // The server's copy wins, cleared fields included.
        #expect(save.modifiedAt == pushStart.addingTimeInterval(-1))
    }

    @Test func mergesTheSameLinkIntoTheLowestID() throws {
        #expect(Sync.action(for: row(highID), sameID: nil, sameURL: local(lowID, modifiedAt: pushStart), pushStart: pushStart)
            == .merge(survivor: lowID, loser: highID))
        #expect(Sync.action(for: row(lowID), sameID: nil, sameURL: local(highID, modifiedAt: pushStart), pushStart: pushStart)
            == .merge(survivor: lowID, loser: highID))

        let container = try ModelContainer.stash(inMemory: true)
        let save = local(highID, modifiedAt: pushStart)
        save.setCategory(.food)
        save.openedAt = .now
        save.createdAt = Date(timeIntervalSince1970: 0)
        container.mainContext.insert(save)

        Sync.apply([row(lowID)], in: container.mainContext, pushStart: pushStart)

        let tombstone = try #require(try container.mainContext.fetch(FetchDescriptor<Tombstone>()).first)
        #expect(tombstone.id == highID)
        #expect(save.id == lowID)
        #expect(save.title == "From the server")
        #expect(save.category == .food) // Manual wins.
        #expect(save.openedAt != nil)
        #expect(save.createdAt == Date(timeIntervalSince1970: 0)) // Earliest share.
        #expect(save.modifiedAt > pushStart) // Goes back up.
        #expect(try container.mainContext.fetchCount(FetchDescriptor<Save>()) == 1)
    }

    @Test func aMergeKeepsTheLocalCategoryOverAnUnclassifiedOne() {
        let save = local(highID, modifiedAt: pushStart)
        save.category = .food
        var other = row(lowID)
        other.category = "other"
        save.merge(other)
        #expect(save.category == .food)
        #expect(save.status == .enriched)
    }

    @Test func insertsAsAlreadyPushed() throws {
        let container = try ModelContainer.stash(inMemory: true)

        Sync.apply([row(lowID)], in: container.mainContext, pushStart: pushStart)

        let save = try #require(try container.mainContext.fetch(FetchDescriptor<Save>()).first)
        #expect(save.id == lowID)
        #expect(save.category == .tech)
        #expect(abs(save.createdAt.timeIntervalSince1970 - 1_790_848_800.123456) < 0.000_01)
        #expect(save.modifiedAt == Date(timeIntervalSince1970: 0))
    }

    @Test func encodesThePlusInTheCursor() {
        #expect(Sync.pullQuery(after: "2026-10-01T10:00:00.123456+00:00")
            == "select=*&updated_at=gt.2026-10-01T10:00:00.123456%2B00:00&order=updated_at.asc,id.asc&limit=500")
        #expect(!Sync.pullQuery(after: nil).contains("updated_at=gt"))
    }

    @Test func everyLocalChangeBumpsModifiedAt() throws {
        let container = try ModelContainer.stash(inMemory: true)
        let save = try SaveStore.add(#require(URLSourceDetector.analyze("https://example.com/b")), in: container.mainContext)
        for change in [{ save.setSeen(true) }, { save.setCategory(.food) },
                       { MetadataEnricher.apply(.success(LinkMetadata(title: "B")), to: save) }] {
            save.modifiedAt = .distantPast
            change()
            #expect(save.modifiedAt > pushStart)
        }
    }

    @Test func deletingWhileSignedInLeavesATombstone() throws {
        let container = try ModelContainer.stash(inMemory: true)
        let save = local(lowID, modifiedAt: pushStart)
        container.mainContext.insert(save)
        SaveStore.delete(save, in: container.mainContext, tombstone: true)
        #expect(try container.mainContext.fetch(FetchDescriptor<Tombstone>()).map(\.id) == [lowID])
    }
}
