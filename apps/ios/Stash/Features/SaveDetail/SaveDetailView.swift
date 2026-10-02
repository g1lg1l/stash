import SwiftData
import SwiftUI

struct SaveDetailView: View {
    let save: Save
    @Environment(\.openURL) private var openURL
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var confirmingDelete = false
    // X posts arrive with the whole post as their title: three lines, and the rest on a tap.
    @State private var titleExpanded = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                if save.showsMedia {
                    SaveThumbnail(save: save)
                        .aspectRatio(save.mediaAspectRatio, contentMode: .fit)
                        .overlay(alignment: .top) {
                            // Keeps the status bar and floating glass buttons legible over bright images.
                            LinearGradient(colors: [.black.opacity(0.45), .clear], startPoint: .top, endPoint: .bottom)
                                .frame(height: 140)
                                .allowsHitTesting(false)
                        }
                }

                VStack(alignment: .leading, spacing: Spacing.l) {
                    header

                    if let summary = save.summary {
                        VStack(alignment: .leading, spacing: Spacing.xs) {
                            Text("Summary")
                                .font(.headline)
                                .accessibilityAddTraits(.isHeader)
                            Text(summary)
                        }
                    }

                    if let description = save.descriptionText {
                        Text(description)
                            .font(.body)
                            .fontDesign(save.contentType == .article ? .serif : nil)
                    }

                    if !save.tags.isEmpty {
                        tags
                    }

                    footer
                }
                .padding(.horizontal, Spacing.l)
            }
            .padding(.bottom, Spacing.xl)
        }
        // What a save is for: pinned at the bottom, never pushed out of sight by a long title.
        // Universal links hand YouTube, Spotify, Maps etc. to their apps; everything else opens in Safari.
        .safeAreaBar(edge: .bottom) {
            Button {
                openURL(save.url)
            } label: {
                Label("Open Original", systemImage: "arrow.up.right")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .padding(.horizontal, Spacing.l)
            .padding(.bottom, Spacing.xs)
        }
        // Media runs edge to edge under the floating glass bar; text-only saves start below it.
        .ignoresSafeArea(edges: save.showsMedia ? .top : [])
        .navigationBarTitleDisplayMode(.inline)
        .toolbarColorScheme(save.showsMedia ? .dark : nil, for: .navigationBar)
        .toolbar {
            ToolbarItem {
                ShareLink(item: save.url)
            }
            ToolbarItem {
                Button("Delete", systemImage: "trash", role: .destructive) { confirmingDelete = true }
            }
        }
        .confirmationDialog("Delete this save?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                SaveStore.delete(save, in: modelContext, tombstone: Account.shared.isSignedIn)
                dismiss()
            }
        } message: {
            Text("It will be removed from your stash for good.")
        }
        .onAppear {
            save.setSeen(true)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            HStack {
                Label(save.source.displayName, systemImage: save.source.symbol)
                Spacer()
                // Tap to recategorize; the choice sticks.
                Menu {
                    CategoryPicker(save: save)
                        .pickerStyle(.inline)
                } label: {
                    HStack(spacing: Spacing.xxs) {
                        CategoryLabel(category: save.category)
                        Image(systemName: "chevron.up.chevron.down")
                            .imageScale(.small)
                    }
                }
                .accessibilityLabel("Category: \(save.category.displayName)")
                .accessibilityHint("Changes the category")
                .sensoryFeedback(.selection, trigger: save.category)
            }
            .font(.meta)
            .foregroundStyle(.secondary)

            Text(save.displayTitle)
                .font(.title.bold())
                .fontDesign(save.contentType == .article ? .serif : nil)
                .lineLimit(titleExpanded ? nil : 3)
                .onTapGesture { withAnimation(.stash) { titleExpanded.toggle() } }
                .accessibilityAddTraits(.isHeader)

            if let author = save.author {
                Text(author)
                    .font(.headline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var tags: some View {
        ScrollView(.horizontal) {
            HStack(spacing: Spacing.xs) {
                ForEach(save.tags, id: \.self) { tag in
                    Text("#\(tag)")
                        .font(.subheadline.weight(.medium))
                        .padding(.horizontal, Spacing.s)
                        .padding(.vertical, Spacing.xs - 2)
                        .background(.fill.tertiary, in: .capsule)
                }
            }
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            if save.status == .failed {
                Label("Details couldn't be loaded. The link is saved.", systemImage: "info.circle")
            } else if save.status == .pending {
                Label {
                    Text("Getting details…")
                } icon: {
                    ProgressView().controlSize(.mini)
                }
            }
            Text("Saved \(save.lastSavedAt, format: .relative(presentation: .named))")
            Text(save.url.absoluteString)
                .lineLimit(2)
                .textSelection(.enabled)
        }
        .font(.footnote)
        .foregroundStyle(.secondary)
        .padding(.top, Spacing.xs)
    }
}

#Preview("Video") {
    NavigationStack { SaveDetailView(save: SampleData.saves()[1]) }
}

#Preview("Text only") {
    NavigationStack { SaveDetailView(save: SampleData.saves()[4]) }
}
