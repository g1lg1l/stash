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

    var body: some View {
        NavigationStack {
            Form {
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
                    Text("No account. Your \(saves.count == 1 ? "save stays" : "\(saves.count) saves stay") on this iPhone. Export sends every title and link as plain text, to Notes, Files or anywhere else.")
                }

                Section {
                    LabeledContent("Version", value: version)
                    Link(destination: URL(string: "https://github.com/g1lg1l/stash")!) {
                        Label("Source Code on GitHub", systemImage: "chevron.left.forwardslash.chevron.right")
                    }
                } header: {
                    Text("About")
                } footer: {
                    Text("Don't see Stash when sharing? In the share sheet, scroll the row of apps to More, tap Edit and turn on Stash.")
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

    private var exportText: String {
        saves.map { "\($0.displayTitle)\n\($0.url.absoluteString)" }.joined(separator: "\n\n")
    }

    private var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "?") (\(info?["CFBundleVersion"] as? String ?? "?"))"
    }
}

#Preview {
    SettingsView()
        .modelContainer(.preview)
}
