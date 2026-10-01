import Foundation

/// What a shared link is, judged from the URL alone. No network, so it works offline and in the Share Extension.
struct URLAnalysis: Equatable {
    /// The link as shared, only trimmed and given a scheme if it had none. Source of truth.
    let url: URL
    /// Dedupe key. Two links share it only when they point at the same resource.
    let canonicalURL: String
    let source: Source
    let contentType: ContentType
}

enum URLSourceDetector {
    /// Accepts any http(s) link, with or without a scheme ("youtu.be/abc"). Nil only for things that aren't web links.
    static func analyze(_ string: String) -> URLAnalysis? {
        let trimmed = string.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !trimmed.contains(" ") else { return nil }
        let withScheme: String
        switch URL(string: trimmed)?.scheme?.lowercased() {
        case nil where !trimmed.contains("@"): withScheme = "https://" + trimmed // "youtu.be/abc"; a bare "a@b.com" is an email
        case "http", "https": withScheme = trimmed
        default: return nil // mailto:, tel:, spotify:, emails
        }
        guard var components = URLComponents(string: withScheme),
              let scheme = components.scheme?.lowercased(), ["http", "https"].contains(scheme),
              let host = components.host?.lowercased(), host.contains("."), !host.hasPrefix("."), !host.hasSuffix(".")
        else { return nil }
        components.scheme = scheme
        components.host = host
        guard let url = components.url else { return nil }

        let bareHost = strip(host, prefixes: ["www.", "m.", "mobile."])
        let source = source(forHost: bareHost, path: components.path)
        return URLAnalysis(
            url: url,
            canonicalURL: canonicalKey(components, bareHost: bareHost, source: source),
            source: source,
            contentType: contentType(for: source, host: bareHost, path: components.path.lowercased())
        )
    }

    static func analyze(_ url: URL) -> URLAnalysis? {
        analyze(url.absoluteString)
    }

    /// First http(s) link inside shared text ("Look at this 😍 https://…"). Emails and phone numbers don't count.
    static func firstLink(in text: String) -> URL? {
        let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)
        return detector?
            .matches(in: text, range: NSRange(text.startIndex..., in: text))
            .lazy
            .compactMap(\.url)
            .first { ["http", "https"].contains($0.scheme?.lowercased()) }
    }

    // MARK: - Source

    private static func source(forHost host: String, path: String) -> Source {
        func matches(_ domains: String...) -> Bool {
            domains.contains { host == $0 || host.hasSuffix("." + $0) }
        }
        if matches("instagram.com", "instagr.am") { return .instagram }
        if matches("youtube.com", "youtu.be", "youtube-nocookie.com") { return .youtube }
        if matches("tiktok.com") { return .tiktok }
        if matches("reddit.com", "redd.it") { return .reddit }
        if matches("x.com", "twitter.com") { return .x }
        if matches("spotify.com", "spotify.link") { return .spotify }
        if matches("maps.app.goo.gl", "maps.apple.com", "maps.google.com")
            || (matches("google.com") && path.hasPrefix("/maps"))
            || (host == "goo.gl" && path.hasPrefix("/maps")) {
            return .maps
        }
        return .web
    }

    // MARK: - Content type

    private static func contentType(for source: Source, host: String, path: String) -> ContentType {
        switch source {
        case .youtube: host.hasPrefix("music.") ? .music : .video
        case .tiktok: .video
        case .instagram: ["/reel/", "/reels/", "/tv/"].contains { path.contains($0) } ? .video : .post
        case .reddit, .x: .post
        case .spotify: .music
        case .maps: .place
        case .web, .unknown:
            // Metadata refines this later (og:type); the URL only gives strong hints.
            if ["/product/", "/products/", "/dp/", "/gp/product/", "/item/", "/itm/"].contains(where: { path.contains($0) }) {
                .product
            } else if path.isEmpty || path == "/" {
                .other
            } else {
                .article
            }
        }
    }

    // MARK: - Canonical key

    /// Query items that never identify a resource.
    private static let trackingItems: Set<String> = [
        "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid", "igsh", "igshid", "_ga", "ref_src", "ref_url",
    ]

    private static func canonicalKey(_ components: URLComponents, bareHost: String, source: Source) -> String {
        var key = URLComponents()
        key.scheme = "https"
        key.host = bareHost
        key.port = components.port
        var path = components.path
        var query = components.queryItems ?? []

        switch source {
        case .youtube:
            // youtu.be/ID, /shorts/ID, /embed/ID, /live/ID and /watch?v=ID are the same video.
            let parts = path.split(separator: "/").map(String.init)
            let id: String? = if bareHost == "youtu.be" { parts.first }
                else if parts.count >= 2, ["shorts", "embed", "live", "v"].contains(parts[0]) { parts[1] }
                else if parts.first == "watch" { query.first { $0.name == "v" }?.value }
                else { nil }
            if let id {
                key.host = bareHost.hasPrefix("music.") ? bareHost : "youtube.com"
                path = "/watch"
                query = [URLQueryItem(name: "v", value: id)]
            } else {
                key.host = bareHost == "youtu.be" ? "youtube.com" : bareHost
                query = query.filter { $0.name == "list" } // Playlists are identified by list=.
            }
        case .x:
            key.host = "x.com"
            // x.com/<anyone>/status/ID resolves to the same post whatever the handle.
            let parts = path.split(separator: "/")
            if parts.count >= 3, parts[1] == "status" { path = "/i/status/\(parts[2])" }
            query = []
        case .reddit:
            if bareHost != "redd.it" { key.host = "reddit.com" } // old., new., np. are the same post
            query = []
        case .instagram, .tiktok, .spotify:
            // Their query strings are share and tracking tokens only.
            query = []
        case .maps, .web, .unknown:
            query = query.filter { !$0.name.lowercased().hasPrefix("utm_") && !trackingItems.contains($0.name.lowercased()) }
        }

        if path.count > 1, path.hasSuffix("/") { path.removeLast() }
        key.path = path
        key.queryItems = query.isEmpty ? nil : query.sorted { ($0.name, $0.value ?? "") < ($1.name, $1.value ?? "") }
        // Plain anchors (#section) are the same page; hash routes (#/page, #!/page) are not.
        if let fragment = components.fragment, fragment.hasPrefix("/") || fragment.hasPrefix("!") {
            key.fragment = fragment
        }
        return key.string ?? components.string ?? ""
    }

    private static func strip(_ host: String, prefixes: [String]) -> String {
        for prefix in prefixes where host.hasPrefix(prefix) {
            return String(host.dropFirst(prefix.count))
        }
        return host
    }
}
