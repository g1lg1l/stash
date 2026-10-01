import Foundation
import Testing
@testable import Stash

struct SearchServiceTests {
    let saves = SampleData.saves()

    private func titles(_ query: String) -> [String] {
        SearchService.results(for: query, in: saves).map(\.displayTitle)
    }

    @Test func ignoresCaseAndAccents() {
        #expect(titles("SENSO") == ["Sensō-ji"])
    }

    @Test func everyWordMustMatchSomeField() {
        // "tokyo" is a tag on both Tokyo places; "temple" only on Sensō-ji.
        #expect(Set(titles("tokyo")) == ["Sensō-ji", "Tsukiji Outer Market"])
        #expect(titles("tokyo temple") == ["Sensō-ji"])
    }

    @Test func searchesAuthorCategorySourceAndURL() {
        #expect(titles("karpathy") == ["Let's build GPT: from scratch, in code, spelled out."])
        #expect(titles("business") == ["How to Get Startup Ideas"])
        #expect(titles("spotify") == ["Random Access Memories"])
        #expect(titles("paulgraham.com") == ["How to Get Startup Ideas"])
    }

    @Test func blankQueryReturnsNothing() {
        #expect(titles("   ").isEmpty)
    }

    @Test func keepsInputOrder() {
        let results = SearchService.results(for: "youtube", in: saves)
        #expect(results.map(\.lastSavedAt) == results.map(\.lastSavedAt).sorted(by: >))
        #expect(results.count == 3)
    }
}

struct FeedSectionTests {
    @Test func groupsByDay() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: "UTC"))
        let now = try #require(calendar.date(from: DateComponents(year: 2026, month: 10, day: 1, hour: 15)))
        func save(hoursAgo: Double) -> Save {
            Save(url: URL(string: "https://example.com/\(hoursAgo)")!, createdAt: now.addingTimeInterval(-hoursAgo * 3600))
        }
        // 1h: today. 20h: yesterday. 3 days: this week. 30 days: earlier.
        let saves = [save(hoursAgo: 1), save(hoursAgo: 2), save(hoursAgo: 20), save(hoursAgo: 72), save(hoursAgo: 720)]

        let sections = FeedSection.byDay(saves, now: now, calendar: calendar)

        #expect(sections.map(\.title) == ["Today", "Yesterday", "Last 7 Days", "Earlier"])
        #expect(sections.map(\.saves.count) == [2, 1, 1, 1])
    }
}

struct RediscoveryTests {
    let now = Date(timeIntervalSince1970: 10_000_000)

    private func save(daysAgo: Double, seen: Bool = false) -> Save {
        let date = now.addingTimeInterval(-daysAgo * 86_400)
        return Save(url: URL(string: "https://example.com/\(daysAgo)")!, createdAt: date, openedAt: seen ? date : nil)
    }

    @Test func picksOldUnseenSavesOldestFirst() {
        let saves = [save(daysAgo: 1), save(daysAgo: 4), save(daysAgo: 30), save(daysAgo: 10, seen: true), save(daysAgo: 3)]
        let picks = Rediscovery.picks(from: saves, now: now)
        #expect(picks.map(\.url.lastPathComponent) == ["30.0", "4.0", "3.0"])
    }

    @Test func isCapped() {
        let saves = (4...20).map { save(daysAgo: Double($0)) }
        #expect(Rediscovery.picks(from: saves, now: now, limit: 6).count == 6)
    }
}

struct PerformanceTests {
    /// Evidence, not optimization: catches accidental O(n²) in the per-keystroke and per-render paths.
    @Test func searchAndSectioningStayFastAt2000Saves() {
        let samples = SampleData.saves()
        let saves = (0..<2000).map { i in
            let s = samples[i % samples.count]
            return Save(url: URL(string: s.url.absoluteString + "?n=\(i)")!, source: s.source, contentType: s.contentType,
                        category: s.category, title: s.title, descriptionText: s.descriptionText, author: s.author,
                        tags: s.tags, createdAt: Date(timeIntervalSinceNow: -Double(i) * 3600))
        }
        let elapsed = ContinuousClock().measure {
            for query in ["tokyo", "pasta recipe", "youtube", "nothing matches this"] {
                _ = SearchService.results(for: query, in: saves)
            }
            _ = FeedSection.byDay(saves)
            _ = Rediscovery.picks(from: saves)
        }
        Attachment.record("4 searches + sectioning + rediscovery over 2000 saves: \(elapsed)", named: "timing.txt")
        #expect(elapsed < .seconds(1))
    }
}
