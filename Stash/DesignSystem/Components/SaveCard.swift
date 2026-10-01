import SwiftUI

/// Large visual save. The layout follows the content type instead of one generic container.
struct SaveCard: View {
    let save: Save
    @ScaledMetric(relativeTo: .title3) private var artworkSize: CGFloat = 88

    var body: some View {
        Group {
            if save.contentType == .music {
                musicCard
            } else if save.showsMedia {
                mediaCard
            } else {
                textCard
            }
        }
        .accessibilityElement(children: .combine)
    }

    // Video, product, place, and anything with an image.
    private var mediaCard: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            SaveThumbnail(save: save)
                .aspectRatio(save.mediaAspectRatio, contentMode: .fit)
                .clipShape(.rect(cornerRadius: Radius.card))
                .overlay(alignment: .topLeading) {
                    SourceBadge(source: save.source)
                        .padding(Spacing.s)
                }
                .overlay {
                    // Without a real image the placeholder symbol already says "video".
                    if save.contentType == .video, save.thumbnailURL != nil {
                        Image(systemName: "play.fill")
                            .font(.title2)
                            .padding(Spacing.m + 2)
                            .glassEffect(.regular, in: .circle)
                            .accessibilityHidden(true)
                    }
                }

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                if save.contentType == .place {
                    Label(save.displayTitle, systemImage: "mappin")
                        .font(.cardTitle)
                } else {
                    Text(save.displayTitle)
                        .font(save.contentType == .article ? .title2.weight(.semibold) : .cardTitle)
                        .fontDesign(save.contentType == .article ? .serif : nil)
                        .lineLimit(3)
                }
                if save.contentType == .article, let description = save.descriptionText {
                    Text(description)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
                SaveMetaLine(save: save)
            }
            .padding(.horizontal, Spacing.xxs)
        }
    }

    // Articles and posts without an image: editorial type on a soft category tint.
    private var textCard: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Label(save.source.displayName, systemImage: save.source.symbol)
                .font(.meta)
                .foregroundStyle(.secondary)
            Text(save.displayTitle)
                .font(.title2.weight(.semibold))
                .fontDesign(.serif)
            if let description = save.descriptionText {
                Text(description)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
            }
            SaveMetaLine(save: save)
                .padding(.top, Spacing.xxs)
        }
        .padding(Spacing.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(save.category.tint.opacity(0.12), in: .rect(cornerRadius: Radius.card))
    }

    private var musicCard: some View {
        HStack(spacing: Spacing.m) {
            SaveThumbnail(save: save)
                .frame(width: artworkSize, height: artworkSize)
                .clipShape(.rect(cornerRadius: Radius.thumbnail))
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(save.displayTitle)
                    .font(.cardTitle)
                    .lineLimit(2)
                Text(save.byline)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                Label(save.source.displayName, systemImage: save.source.symbol)
                    .font(.meta)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.s)
        .background(Color(.secondarySystemBackground), in: .rect(cornerRadius: Radius.card))
    }
}

/// Compact save for dense lists and search results.
struct SaveRow: View {
    let save: Save
    @ScaledMetric(relativeTo: .body) private var thumbnailSize: CGFloat = 56

    var body: some View {
        HStack(spacing: Spacing.s) {
            // Mail-style unseen dot; the row keeps its alignment either way.
            Circle()
                .fill(.tint)
                .frame(width: 8, height: 8)
                .opacity(save.openedAt == nil ? 1 : 0)
                .accessibilityLabel("Unseen")
                .accessibilityHidden(save.openedAt != nil)
            SaveThumbnail(save: save)
                .frame(width: thumbnailSize, height: thumbnailSize)
                .clipShape(.rect(cornerRadius: Radius.thumbnail))
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(save.displayTitle)
                    .font(.rowTitle)
                    .lineLimit(2)
                SaveMetaLine(save: save)
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview("Cards") {
    ScrollView {
        LazyVStack(spacing: Spacing.xl) {
            ForEach(SampleData.saves()) { SaveCard(save: $0) }
        }
        .padding()
    }
}

#Preview("Rows") {
    List(SampleData.saves()) { SaveRow(save: $0) }
}
