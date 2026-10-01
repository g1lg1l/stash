import Foundation
import OSLog
import SwiftData

/// What a page says about itself. Every field is optional; a save is complete without any of them.
struct LinkMetadata: Equatable, Sendable {
    var title: String?
    var description: String?
    var imageURL: URL?
    var author: String?
    var contentType: ContentType?

    var isEmpty: Bool { title == nil && description == nil && imageURL == nil && author == nil }
}

enum MetadataError: Error {
    /// No connection or it timed out: worth retrying soon.
    case offline
    /// The page answered but had nothing usable.
    case unavailable
}

enum MetadataService {
    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 15
        configuration.httpAdditionalHeaders = [
            // A normal mobile Safari identity, marked as Stash, like in-app browsers do.
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 26_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1 Stash/1.0",
            "Accept-Language": Locale.preferredLanguages.prefix(3).joined(separator: ","),
        ]
        return URLSession(configuration: configuration)
    }()

    static func fetch(_ url: URL, source: Source) async throws(MetadataError) -> LinkMetadata {
        var metadata = LinkMetadata()
        if let endpoint = oEmbedEndpoint(for: url, source: source) {
            metadata = (try? parseOEmbed(await get(endpoint))) ?? LinkMetadata()
        }
        if metadata.imageURL == nil || metadata.title == nil {
            let page = try await get(url)
            metadata.merge(parseHTML(String(decoding: page.prefix(1_000_000), as: UTF8.self), baseURL: url))
        }
        if source == .maps, metadata.title == nil || metadata.title?.localizedCaseInsensitiveContains("google maps") == true {
            metadata.title = placeName(in: url)
        }
        guard !metadata.isEmpty else { throw .unavailable }
        return metadata
    }

    private static func get(_ url: URL) async throws(MetadataError) -> Data {
        do {
            let (data, response) = try await session.data(from: url)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw MetadataError.unavailable }
            return data
        } catch let error as URLError where [.notConnectedToInternet, .networkConnectionLost, .timedOut, .dataNotAllowed].contains(error.code) {
            throw .offline
        } catch {
            throw .unavailable
        }
    }

    // MARK: - oEmbed (official, keyless endpoints)

    private static func oEmbedEndpoint(for url: URL, source: Source) -> URL? {
        let base: String? = switch source {
        case .youtube: "https://www.youtube.com/oembed?format=json&url="
        case .tiktok: "https://www.tiktok.com/oembed?url="
        case .x: "https://publish.twitter.com/oembed?omit_script=1&url="
        default: nil
        }
        guard let base, let encoded = url.absoluteString.addingPercentEncoding(withAllowedCharacters: .alphanumerics) else { return nil }
        return URL(string: base + encoded)
    }

    static func parseOEmbed(_ data: Data) throws -> LinkMetadata {
        struct OEmbed: Decodable {
            let title: String?
            let author_name: String?
            let thumbnail_url: URL?
            let html: String?
        }
        let oEmbed = try JSONDecoder().decode(OEmbed.self, from: data)
        // X returns no title, only the post as HTML; its text is the best title there is.
        let title = oEmbed.title?.nilIfBlank ?? oEmbed.html.flatMap { firstParagraph(in: $0) }
        return LinkMetadata(title: title, imageURL: oEmbed.thumbnail_url, author: oEmbed.author_name?.nilIfBlank)
    }

    // MARK: - HTML (Open Graph, Twitter Cards, plain tags)

    static func parseHTML(_ html: String, baseURL: URL) -> LinkMetadata {
        let head = html.range(of: "</head>", options: .caseInsensitive).map { String(html[..<$0.lowerBound]) } ?? html
        var tags: [String: String] = [:]
        for match in head.matches(of: /<meta\s[^>]*>/.ignoresCase()) {
            let attributes = attributes(in: String(match.output))
            guard let key = (attributes["property"] ?? attributes["name"] ?? attributes["itemprop"])?.lowercased(),
                  let content = attributes["content"].map(decodeEntities)?.nilIfBlank,
                  tags[key] == nil
            else { continue }
            tags[key] = content
        }
        let title = tags["og:title"] ?? tags["twitter:title"]
            ?? head.firstMatch(of: /<title[^>]*>([^<]*)<\/title>/.ignoresCase()).map { decodeEntities(String($0.output.1)) }?.nilIfBlank
        let image = (tags["og:image:secure_url"] ?? tags["og:image"] ?? tags["og:image:url"] ?? tags["twitter:image"] ?? tags["twitter:image:src"])
            .flatMap { URL(string: $0, relativeTo: baseURL)?.absoluteURL }
        // "Album by Daft Punk | Spotify", "Cacio e pepe - Wikipedia": the site name adds nothing.
        let siteNames = [tags["og:site_name"], baseURL.host()?.split(separator: ".").dropLast().last.map(String.init)].compactMap { $0 }
        return LinkMetadata(
            title: title.flatMap { cleanTitle($0, siteNames: siteNames) },
            description: tags["og:description"] ?? tags["twitter:description"] ?? tags["description"],
            imageURL: image,
            author: tags["author"] ?? tags["article:author"].flatMap { $0.hasPrefix("http") ? nil : $0 } ?? tags["music:musician_description"],
            contentType: tags["og:type"].flatMap(contentType(forOpenGraphType:))
        )
    }

    /// Strips a trailing " | Site" / " - Site"; a title that is only the site name is no title.
    static func cleanTitle(_ title: String, siteNames: [String]) -> String? {
        var title = title
        for name in siteNames where !name.isEmpty {
            if title.caseInsensitiveCompare(name) == .orderedSame { return nil }
            for separator in [" | ", " - ", " – ", " — ", " · "] {
                if title.lowercased().hasSuffix((separator + name).lowercased()) {
                    title = String(title.dropLast(separator.count + name.count))
                }
            }
        }
        return title.nilIfBlank
    }

    private static func contentType(forOpenGraphType type: String) -> ContentType? {
        let type = type.lowercased()
        if type.hasPrefix("video") { return .video }
        if type.hasPrefix("music") { return .music }
        if type.hasPrefix("article") || type == "blog" { return .article }
        if type.contains("product") { return .product }
        if type.contains("place") || type.hasPrefix("business") || type.contains("restaurant") { return .place }
        return nil
    }

    private static func attributes(in tag: String) -> [String: String] {
        var result: [String: String] = [:]
        for match in tag.matches(of: /([a-zA-Z:_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/) {
            result[String(match.output.1).lowercased()] = String(match.output.2 ?? match.output.3 ?? "")
        }
        return result
    }

    private static func firstParagraph(in html: String) -> String? {
        guard let paragraph = html.firstMatch(of: /<p[^>]*>(.*?)<\/p>/.dotMatchesNewlines()) else { return nil }
        let text = String(paragraph.output.1).replacing(/<[^>]+>/, with: " ").replacing(/\s+/, with: " ")
        return decodeEntities(text).nilIfBlank.map { $0.count > 140 ? String($0.prefix(139)) + "…" : $0 }
    }

    static func decodeEntities(_ text: String) -> String {
        guard text.contains("&") else { return text }
        let named = ["&amp;": "&", "&quot;": "\"", "&#39;": "'", "&apos;": "'", "&lt;": "<", "&gt;": ">", "&nbsp;": " "]
        var result = text
        for (entity, character) in named where entity != "&amp;" {
            result = result.replacingOccurrences(of: entity, with: character)
        }
        result = result.replacing(/&#(x[0-9a-fA-F]+|[0-9]+);/) { match in
            let value = String(match.output.1)
            let scalar = value.hasPrefix("x") ? UInt32(value.dropFirst(), radix: 16) : UInt32(value)
            return scalar.flatMap(Unicode.Scalar.init).map { String($0) } ?? String(match.output.0)
        }
        return result.replacingOccurrences(of: "&amp;", with: "&") // Last, so "&amp;lt;" stays "&lt;".
    }

    /// "maps.google.com/?q=Senso-ji" or ".../maps/place/Senso-ji/@35.7,139.7" → "Senso-ji". Works offline.
    static func placeName(in url: URL) -> String? {
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        if let query = components?.queryItems?.first(where: { ["q", "query"].contains($0.name) })?.value?.nilIfBlank {
            return query.replacingOccurrences(of: "+", with: " ")
        }
        let parts = url.pathComponents
        if let index = parts.firstIndex(of: "place"), index + 1 < parts.count {
            return parts[index + 1].replacingOccurrences(of: "+", with: " ").removingPercentEncoding
        }
        return nil
    }
}

private extension LinkMetadata {
    mutating func merge(_ other: LinkMetadata) {
        title = title ?? other.title
        description = description ?? other.description
        imageURL = imageURL ?? other.imageURL
        author = author ?? other.author
        contentType = contentType ?? other.contentType
    }
}

extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

/// Fills in pending saves in the background. Network happens off the main actor; models are only touched on it.
@MainActor
enum MetadataEnricher {
    private static var isRunning = false

    /// `retryFailed` once per launch, so pages that had nothing get another chance without hammering them.
    static func enrichPending(in context: ModelContext, retryFailed: Bool = false) async {
        guard !isRunning else { return }
        isRunning = true
        defer { isRunning = false }

        let statuses = (retryFailed ? [SaveStatus.pending, .failed] : [.pending]).map(\.rawValue)
        let descriptor = FetchDescriptor<Save>(
            predicate: #Predicate { statuses.contains($0.statusRaw) },
            sortBy: [SortDescriptor(\.lastSavedAt, order: .reverse)]
        )
        guard let saves = try? context.fetch(descriptor), !saves.isEmpty else { return }
        let jobs = saves.map { (id: $0.persistentModelID, url: $0.url, source: $0.source) }

        await withTaskGroup(of: (PersistentIdentifier, Result<LinkMetadata, MetadataError>).self) { group in
            var next = jobs.makeIterator()
            func start() {
                guard let job = next.next() else { return }
                group.addTask {
                    do throws(MetadataError) {
                        return (job.id, .success(try await MetadataService.fetch(job.url, source: job.source)))
                    } catch {
                        return (job.id, .failure(error))
                    }
                }
            }
            for _ in 0..<4 { start() } // A few at a time is plenty and polite.
            for await (id, result) in group {
                if let save = context.model(for: id) as? Save { apply(result, to: save) }
                start()
            }
        }
        try? context.save()
    }

    static func apply(_ result: Result<LinkMetadata, MetadataError>, to save: Save) {
        switch result {
        case .success(let metadata):
            // Fill gaps only: never overwrite what's already there.
            save.title = save.title ?? metadata.title
            save.descriptionText = save.descriptionText ?? metadata.description
            save.thumbnailURL = save.thumbnailURL ?? metadata.imageURL
            save.author = save.author ?? metadata.author
            if save.source == .web, let type = metadata.contentType { save.contentType = type }
            save.status = .enriched
        case .failure(.offline):
            break // Still pending; the next foreground retries.
        case .failure(.unavailable):
            save.status = .failed
        }
        // Classify with whatever we now know. Only unclassified saves, so a category is never taken away.
        if save.category == .other, !save.categoryIsManual { save.category = ClassificationService.category(for: save) }
        Logger(subsystem: "com.g1lg1l.stash", category: "metadata").debug("Enriched \(save.url, privacy: .private): \(save.statusRaw)")
    }
}
