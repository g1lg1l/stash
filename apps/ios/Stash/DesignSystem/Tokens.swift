import SwiftUI

enum Spacing {
    static let xxs: CGFloat = 4
    static let xs: CGFloat = 8
    static let s: CGFloat = 12
    static let m: CGFloat = 16
    static let l: CGFloat = 24
    static let xl: CGFloat = 32
}

enum Radius {
    static let card: CGFloat = 22
    static let thumbnail: CGFloat = 12
}

// Roles on top of Dynamic Type text styles, so every size still scales.
extension Font {
    static let cardTitle = Font.title3.weight(.semibold)
    static let rowTitle = Font.body.weight(.semibold)
    static let meta = Font.footnote.weight(.medium)
}

extension Animation {
    static let stash = Animation.smooth(duration: 0.3)
}

/// Tactile press for tappable cards and tiles. Falls back to a dim instead of a scale under Reduce Motion.
struct PressableStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.97 : 1)
            .opacity(configuration.isPressed && reduceMotion ? 0.7 : 1)
            .animation(.stash, value: configuration.isPressed)
    }
}
