import SwiftUI

/// Fills whatever frame it's given. Callers set the aspect ratio and clip shape.
/// Missing or failed images fall back to a category-tinted gradient, so layouts never collapse.
struct SaveThumbnail: View {
    let save: Save

    var body: some View {
        Rectangle()
            .fill(save.category.tint.gradient.opacity(0.35))
            .overlay {
                Image(systemName: save.category == .other ? save.source.symbol : save.category.symbol)
                    .font(.title)
                    .foregroundStyle(save.category.tint)
            }
            .overlay {
                // ponytail: AsyncImage + URLCache.shared; add a decoded-image cache if scrolling hitches at 1k saves.
                AsyncImage(url: save.thumbnailURL, transaction: Transaction(animation: .stash)) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    }
                }
            }
            .clipped()
            .accessibilityHidden(true)
    }
}

/// Source name on a glass capsule, for placing over media.
struct SourceBadge: View {
    let source: Source

    var body: some View {
        Label(source.displayName, systemImage: source.symbol)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, Spacing.xs + 2)
            .padding(.vertical, Spacing.xxs + 2)
            .glassEffect(.regular, in: .capsule)
    }
}

struct CategoryLabel: View {
    let category: Category

    var body: some View {
        HStack(spacing: Spacing.xxs + 2) {
            Circle()
                .fill(category.tint)
                .frame(width: 7, height: 7)
            Text(category.displayName)
        }
    }
}

/// "Food · @weeknight.pasta"
struct SaveMetaLine: View {
    let save: Save

    var body: some View {
        HStack(spacing: Spacing.xxs + 2) {
            CategoryLabel(category: save.category)
            Text("·")
                .accessibilityHidden(true)
            Text(save.byline)
                .lineLimit(1)
        }
        .font(.meta)
        .foregroundStyle(.secondary)
    }
}
