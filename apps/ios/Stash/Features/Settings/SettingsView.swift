import SwiftData
import SwiftUI

enum Theme: String, CaseIterable {
    case automatic, light, dark

    var colorScheme: ColorScheme? {
        switch self {
        case .automatic: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

struct SettingsView: View {
    @Query(sort: \Save.lastSavedAt, order: .reverse) private var saves: [Save]
    @AppStorage("theme") private var theme: Theme = .automatic
    @AppStorage("showRediscovery") private var showRediscovery = true
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @State private var confirmingDelete = false
    private let account = Account.shared

    var body: some View {
        NavigationStack {
            Form {
                accountSection

                Section("Appearance") {
                    Picker("Theme", selection: $theme) {
                        ForEach(Theme.allCases, id: \.self) { Text($0.rawValue.capitalized) }
                    }
                    .pickerStyle(.segmented)
                }

                Section {
                    Toggle("Worth Another Look", isOn: $showRediscovery)
                } header: {
                    Text("Explore")
                } footer: {
                    Text("Brings back saves you haven't opened in a few days.")
                }

                Section {
                    ShareLink(item: exportText, subject: Text("My Stash"), preview: SharePreview("\(saves.count) links")) {
                        Label("Export Links", systemImage: "square.and.arrow.up")
                    }
                    .disabled(saves.isEmpty)
                } header: {
                    Text("Your Data")
                } footer: {
                    Text("\(account.isSignedIn ? "" : "No account. ")Your \(saves.count == 1 ? "save stays" : "\(saves.count) saves stay") on this iPhone\(account.isSignedIn ? ", synced with your account" : ""). Export sends every title and link as plain text, to Notes, Files or anywhere else.")
                }

                Section("About") {
                    NavigationLink {
                        TipsView()
                    } label: {
                        Label("Get the Most Out of Stash", systemImage: "lightbulb")
                    }
                    LabeledContent("Version", value: version)
                    Link(destination: URL(string: "https://github.com/g1lg1l/stash")!) {
                        Label("Source Code on GitHub", systemImage: "chevron.left.forwardslash.chevron.right")
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", systemImage: "checkmark") { dismiss() }
                }
            }
            .sensoryFeedback(.selection, trigger: theme)
        }
        // A sheet is its own presentation: keep it in step with the app while the theme changes.
        .preferredColorScheme(theme.colorScheme)
    }

    private var accountSection: some View {
        Section {
            if let session = account.session {
                Text(session.email)
                LabeledContent("Last Synced") {
                    if account.isSyncing {
                        Text("Syncing…")
                    } else if let error = account.lastError {
                        Text(error)
                    } else if let date = account.lastSyncedAt {
                        Text(date, format: .relative(presentation: .named))
                    } else {
                        Text("Never")
                    }
                }
                Button("Sync Now", systemImage: "arrow.triangle.2.circlepath") {
                    Task { await account.sync(modelContext) }
                }
                .disabled(account.isSyncing)
                Button("Sign Out", systemImage: "rectangle.portrait.and.arrow.right") { account.signOut() }
                Button("Delete Account", systemImage: "person.crop.circle.badge.xmark", role: .destructive) { confirmingDelete = true }
            } else {
                NavigationLink { AuthView(creating: false) } label: { Label("Sign In", systemImage: "person.crop.circle") }
                NavigationLink { AuthView(creating: true) } label: { Label("Create Account", systemImage: "person.crop.circle.badge.plus") }
            }
        } header: {
            Text("Account")
        } footer: {
            if !account.isSignedIn {
                Text(account.lastError ?? "Sync your saves across your devices. Optional: Stash works the same without an account.")
            }
        }
        .confirmationDialog("Delete your account?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("Delete Account", role: .destructive) {
                Task { await account.deleteAccount() }
            }
        } message: {
            Text("This deletes your account and the copy of your saves on the server. Your saves stay on this iPhone.")
        }
    }

    private var exportText: String {
        saves.map { "\($0.displayTitle)\n\($0.url.absoluteString)" }.joined(separator: "\n\n")
    }

    private var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "?") (\(info?["CFBundleVersion"] as? String ?? "?"))"
    }
}

/// Setup that makes saving one tap away. iOS hides new share options, so most people never find them alone.
private struct TipsView: View {
    var body: some View {
        Form {
            tip("Put Stash First in the Share Sheet", symbol: "square.and.arrow.up",
                "In any share sheet, scroll the row of apps to More and tap Edit. Tap + next to Stash and drag it to the top.")
            tip("Add the Save to Stash Action", symbol: "bolt",
                "In the share sheet, tap View More, then Edit Actions at the bottom. Add Save to Stash to Favorites and drag it to the top. It saves in one tap, from the first row of actions.")
            tip("Add the Widget", symbol: "square.text.square",
                "Touch and hold the Home Screen, tap Edit, then Add Widget, and search for Stash. It brings back something you saved; tap it to open.")
            tip("Come Back to Explore", symbol: "square.grid.2x2",
                "Worth Another Look picks saves you haven't opened in a few days. Opening one retires it.")
        }
        .navigationTitle("Get the Most Out of Stash")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func tip(_ title: LocalizedStringKey, symbol: String, _ detail: LocalizedStringKey) -> some View {
        Label {
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(title).font(.headline)
                Text(detail).font(.subheadline).foregroundStyle(.secondary)
            }
            .padding(.vertical, Spacing.xxs)
        } icon: {
            Image(systemName: symbol).foregroundStyle(.tint)
        }
    }
}

#Preview {
    SettingsView()
        .modelContainer(.preview)
}
