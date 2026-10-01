import SwiftUI

extension Source {
    var displayName: String {
        switch self {
        case .instagram: "Instagram"
        case .youtube: "YouTube"
        case .tiktok: "TikTok"
        case .reddit: "Reddit"
        case .x: "X"
        case .spotify: "Spotify"
        case .maps: "Maps"
        case .web: "Web"
        case .unknown: "Link"
        }
    }

    var symbol: String {
        switch self {
        case .instagram: "camera"
        case .youtube: "play.rectangle.fill"
        case .tiktok: "play.square.stack"
        case .reddit: "bubble.left.and.bubble.right"
        case .x: "text.bubble"
        case .spotify: "music.note"
        case .maps: "mappin"
        case .web: "globe"
        case .unknown: "link"
        }
    }
}

extension Category {
    var displayName: String { rawValue.capitalized }

    /// System colors, so they adapt to light and dark mode.
    var tint: Color {
        switch self {
        case .food: .orange
        case .travel: .teal
        case .fitness: .green
        case .ideas: .yellow
        case .business: .indigo
        case .tech: .blue
        case .shopping: .pink
        case .music: .purple
        case .learning: .mint
        case .places: .red
        case .other: .gray
        }
    }

    var symbol: String {
        switch self {
        case .food: "fork.knife"
        case .travel: "airplane"
        case .fitness: "figure.run"
        case .ideas: "lightbulb"
        case .business: "briefcase"
        case .tech: "cpu"
        case .shopping: "bag"
        case .music: "music.note"
        case .learning: "book"
        case .places: "mappin.and.ellipse"
        case .other: "link"
        }
    }
}

extension Save {
    /// Never empty: falls back to the host, then the full URL.
    var displayTitle: String {
        if let title, !title.isEmpty { return title }
        guard let host = url.host() else { return url.absoluteString }
        return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
    }

    /// Author when known, otherwise where it came from.
    var byline: String { author ?? source.displayName }

    /// Text-first saves without an image get a typographic treatment instead of a placeholder picture.
    var showsMedia: Bool {
        thumbnailURL != nil || ![.article, .post, .other].contains(contentType)
    }

    var mediaAspectRatio: CGFloat {
        switch contentType {
        case .video: 16 / 9
        case .article: 16 / 10
        case .product, .music: 1
        default: 4 / 3
        }
    }
}

extension ContentType {
    /// Plural, for filters: "Videos", "Places".
    var displayName: String {
        switch self {
        case .video: "Videos"
        case .article: "Articles"
        case .post: "Posts"
        case .product: "Products"
        case .place: "Places"
        case .music: "Music"
        case .other: "Other"
        }
    }

    var symbol: String {
        switch self {
        case .video: "play.rectangle"
        case .article: "doc.text"
        case .post: "text.bubble"
        case .product: "bag"
        case .place: "mappin.and.ellipse"
        case .music: "music.note"
        case .other: "link"
        }
    }
}
