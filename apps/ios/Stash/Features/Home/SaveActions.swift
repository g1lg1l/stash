import SwiftData
import SwiftUI

extension View {
    /// Context menu everywhere, plus swipe actions where the container is a List (ignored elsewhere).
    func saveActions(_ save: Save) -> some View {
        modifier(SaveActions(save: save))
    }
}

private struct SaveActions: ViewModifier {
    let save: Save
    @Environment(\.modelContext) private var modelContext
    @Environment(\.openURL) private var openURL

    func body(content: Content) -> some View {
        content
            .contextMenu {
                Button("Open Original", systemImage: "arrow.up.right") {
                    save.openedAt = save.openedAt ?? .now
                    openURL(save.url)
                }
                ShareLink(item: save.url)
                seenToggle
                CategoryPicker(save: save)
                    .pickerStyle(.menu)
                Divider()
                deleteButton
            }
            .swipeActions(edge: .trailing) { deleteButton }
            .swipeActions(edge: .leading) { seenToggle.tint(.accentColor) }
    }

    private var seenToggle: some View {
        let seen = save.openedAt != nil
        return Button(seen ? "Mark as Unseen" : "Mark as Seen", systemImage: seen ? "eye.slash" : "eye") {
            save.openedAt = seen ? nil : .now
        }
    }

    private var deleteButton: some View {
        Button("Delete", systemImage: "trash", role: .destructive) {
            SaveStore.delete(save, in: modelContext)
        }
    }
}

/// Pick a category by hand. Used in context menus and the detail screen.
struct CategoryPicker: View {
    let save: Save

    var body: some View {
        Picker(selection: Binding(get: { save.category }, set: { save.setCategory($0) })) {
            ForEach(Category.allCases, id: \.self) { category in
                Label(category.displayName, systemImage: category.symbol).tag(category)
            }
        } label: {
            Label("Category", systemImage: "tag")
        }
    }
}
