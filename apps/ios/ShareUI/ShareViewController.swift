import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Share → Stash → saved → gone. No form, no choices; organizing happens later in the app.
final class ShareViewController: UIViewController {
    private let model = ShareModel()
    private var finished = false

    override func viewDidLoad() {
        super.viewDidLoad()
        let host = UIHostingController(rootView: ShareView(model: model) { [weak self] in
            guard let self, !finished else { return } // Done can race the auto-dismiss.
            finished = true
            extensionContext?.completeRequest(returningItems: nil)
        })
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)

        let items = extensionContext?.inputItems as? [NSExtensionItem] ?? []
        Task { await model.save(from: items) }
    }
}

@MainActor @Observable
final class ShareModel {
    enum State { case saving, saved(Source), noLink, failed }
    var state = State.saving

    func save(from items: [NSExtensionItem]) async {
        guard let url = await SharedLink.find(in: items), let analysis = URLSourceDetector.analyze(url) else {
            state = .noLink
            return
        }
        do {
            try ShareInbox.add(analysis.url)
            state = .saved(analysis.source)
        } catch {
            state = .failed
        }
    }
}

/// Apps share links in different shapes: a URL item, plain text containing a link, or the post's text.
enum SharedLink {
    static func find(in items: [NSExtensionItem]) async -> URL? {
        let providers = items.flatMap { $0.attachments ?? [] }
        for provider in providers where provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
            if let url = await load(URL.self, from: provider), !url.isFileURL { return url }
        }
        for provider in providers where provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
            if let text = await load(String.self, from: provider), let url = URLSourceDetector.firstLink(in: text) { return url }
        }
        return items.lazy.compactMap { $0.attributedContentText?.string }.compactMap(URLSourceDetector.firstLink).first
    }

    private static func load<T: _ObjectiveCBridgeable & Sendable>(_ type: T.Type, from provider: NSItemProvider) async -> T?
    where T._ObjectiveCType: NSItemProviderReading {
        await withCheckedContinuation { continuation in
            _ = provider.loadObject(ofClass: type) { value, _ in continuation.resume(returning: value) }
        }
    }
}

private struct ShareView: View {
    let model: ShareModel
    let done: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            switch model.state {
            case .saving:
                ProgressView()
            case .saved(let source):
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(.tint)
                Text("Saved to Stash")
                    .font(.title2.bold())
                Text(source == .web ? "Stash will organize it for you." : "From \(source.displayName). Stash will organize it for you.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            case .noLink:
                message("No link to save", "Stash saves links. Try sharing the page or post itself.")
            case .failed:
                message("Couldn't save", "Open Stash once, then share again.")
            }
            Spacer()
            Button(action: done) {
                Text("Done")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
        }
        .padding(24)
        .tint(Color(.accent)) // Extensions don't pick up the app's accent color on their own.
        .sensoryFeedback(trigger: model.isSaved) { _, saved in saved ? .success : nil }
        .task(id: model.isSaved) {
            // Save and disappear: leave on our own shortly after success.
            guard model.isSaved else { return }
            try? await Task.sleep(for: .seconds(1.2))
            done()
        }
    }

    private func message(_ title: String, _ detail: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: "link.badge.plus")
                .font(.system(size: 44))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.title2.bold())
            Text(detail)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
    }
}

private extension ShareModel {
    var isSaved: Bool {
        if case .saved = state { true } else { false }
    }
}
