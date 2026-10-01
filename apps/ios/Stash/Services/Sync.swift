import Foundation
import OSLog
import Security
import SwiftData

/// The optional account: email and password on Supabase, over plain HTTPS. Signing in only turns on sync;
/// this iPhone's store stays the source of truth, and nothing here ever loses a local save on failure.
@MainActor @Observable
final class Account {
    static let shared = Account()

    // Public by design: row level security protects the data.
    private nonisolated static let baseURL = "https://kzgmhvqbvrylwydqskcm.supabase.co"
    private nonisolated static let publishableKey = "sb_publishable_dCk3njz-F4vws7Y5ky6XNA__pOe081Z"
    private nonisolated static let http = URLSession(configuration: .ephemeral) // Never a cached pull.

    struct Session: Codable {
        var accessToken: String
        var refreshToken: String
        var expiresAt: Date
        var email: String
    }

    private(set) var session = Keychain.load() {
        didSet { Keychain.store(session) }
    }
    private(set) var isSyncing = false
    /// Kept across launches, so a forced sign-out still explains itself in Settings.
    private(set) var lastError = UserDefaults.standard.string(forKey: "lastSyncError") {
        didSet { UserDefaults.standard.set(lastError, forKey: "lastSyncError") }
    }
    private(set) var lastSyncedAt = UserDefaults.standard.object(forKey: "lastSyncedAt") as? Date {
        didSet { UserDefaults.standard.set(lastSyncedAt, forKey: "lastSyncedAt") }
    }

    var isSignedIn: Bool { session != nil }

    // Cursors describe this store, so they live in UserDefaults (removed with the app) next to the Keychain session.
    private var lastPushedAt: Date? {
        get { UserDefaults.standard.object(forKey: "lastPushedAt") as? Date }
        set { UserDefaults.standard.set(newValue, forKey: "lastPushedAt") }
    }
    private var pullCursor: String? {
        get { UserDefaults.standard.string(forKey: "pullCursor") }
        set { UserDefaults.standard.set(newValue, forKey: "pullCursor") }
    }

    // MARK: - Account

    /// Signs in, or creates the account. Returns a notice instead when the server wants the email confirmed first.
    func signIn(email: String, password: String, creating: Bool) async throws -> String? {
        let body = try JSONEncoder().encode(["email": email, "password": password])
        let data = creating
            ? try await send("POST", "auth/v1/signup", body: body, signedIn: false)
            : try await send("POST", "auth/v1/token", query: "grant_type=password", body: body, signedIn: false)
        guard let session = try Self.session(from: data, email: email) else { return "Check your email to confirm, then sign in." }
        // A fresh start, as after signing out: everything goes up and comes down again.
        lastPushedAt = nil
        pullCursor = nil
        lastError = nil
        self.session = session
        return nil
    }

    /// Keeps every save on this iPhone. Forgets the session and both cursors, so the next sign-in uploads everything again.
    func signOut(notice: String? = nil) {
        if let token = session?.accessToken {
            Task { _ = try? await Self.request("POST", "auth/v1/logout", query: nil, body: nil, prefer: nil, token: token) } // Best effort.
        }
        session = nil
        lastPushedAt = nil
        pullCursor = nil
        lastSyncedAt = nil
        lastError = notice
    }

    /// Deletes the account and the server's copy. The saves stay on this iPhone.
    func deleteAccount() async {
        do {
            _ = try await send("POST", "rest/v1/rpc/delete_account", body: Data("{}".utf8))
            signOut()
        } catch {
            lastError = error.localizedDescription
        }
    }

    // MARK: - Sync

    /// Pushes local changes, then pulls the server's. One at a time: a call while one runs is dropped.
    /// Offline or failing, it stops where it is and the next call picks up from the cursors.
    func sync(_ context: ModelContext) async {
        guard let email = session?.email, !isSyncing, !context.container.isInMemory else { return }
        isSyncing = true
        defer { isSyncing = false }
        do {
            let pushStart = Date.now
            try await push(context, since: lastPushedAt ?? .distantPast)
            guard session?.email == email else { return } // Signed out meanwhile.
            lastPushedAt = pushStart
            try await pull(context, pushStart: pushStart, email: email)
            lastSyncedAt = .now
            lastError = nil
        } catch {
            Logger(subsystem: "com.g1lg1l.stash", category: "sync").error("Sync stopped: \(error)")
            if isSignedIn { lastError = error.localizedDescription }
        }
    }

    private func push(_ context: ModelContext, since: Date) async throws {
        let rows = try context.fetch(FetchDescriptor<Save>(predicate: #Predicate { $0.modifiedAt > since })).map(Sync.Row.init)
        for start in stride(from: 0, to: rows.count, by: Sync.pageSize) {
            let page = try JSONEncoder().encode(Array(rows[start..<min(start + Sync.pageSize, rows.count)]))
            // `columns` makes every missing optional a null: clearing a field (unseen again) reaches the server,
            // and `deleted_at` null revives a save deleted elsewhere. Last write wins.
            _ = try await send("POST", "rest/v1/saves", query: "columns=\(Sync.Row.columns)", body: page,
                               prefer: "resolution=merge-duplicates,return=minimal")
        }
        let tombstones = try context.fetch(FetchDescriptor<Tombstone>())
        let ids = Dictionary(grouping: tombstones) { Sync.string($0.deletedAt) }.mapValues { $0.map { $0.id.uuidString.lowercased() } }
        for (deletedAt, ids) in ids {
            // Ids the server never had are simply not matched.
            _ = try await send("PATCH", "rest/v1/saves", query: "id=in.(\(ids.joined(separator: ",")))",
                               body: JSONEncoder().encode(["deleted_at": deletedAt]), prefer: "return=minimal")
        }
        // Only the ones pushed: a delete during this push waits for the next one.
        for tombstone in tombstones { context.delete(tombstone) }
        try context.save()
    }

    private func pull(_ context: ModelContext, pushStart: Date, email: String) async throws {
        while true {
            let rows = try JSONDecoder().decode([Sync.Row].self, from: await send("GET", "rest/v1/saves", query: Sync.pullQuery(after: pullCursor)))
            guard session?.email == email else { return }
            Sync.apply(rows, in: context, pushStart: pushStart)
            try context.save() // The page is one transaction: the cursor only moves once it's stored.
            if let last = rows.last?.updated_at { pullCursor = last }
            if rows.count < Sync.pageSize { return }
        }
    }

    // MARK: - HTTP

    struct ServerError: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    /// Signed-in requests refresh the session when it's about to expire, and once more after a 401.
    private func send(_ method: String, _ path: String, query: String? = nil, body: Data? = nil, prefer: String? = nil,
                      signedIn: Bool = true) async throws -> Data {
        if signedIn {
            guard let session else { throw ServerError(message: "Not signed in.") }
            if session.expiresAt < .now.addingTimeInterval(60) { try await refresh() }
        }
        var (data, status) = try await Self.request(method, path, query: query, body: body, prefer: prefer, token: signedIn ? session?.accessToken : nil)
        if signedIn, status == 401 {
            try await refresh()
            (data, status) = try await Self.request(method, path, query: query, body: body, prefer: prefer, token: session?.accessToken)
        }
        guard (200..<300).contains(status) else { throw ServerError(message: Self.message(in: data, status: status)) }
        return data
    }

    private func refresh() async throws {
        guard let session else { throw ServerError(message: "Not signed in.") }
        let body = try JSONEncoder().encode(["refresh_token": session.refreshToken])
        let (data, status) = try await Self.request("POST", "auth/v1/token", query: "grant_type=refresh_token", body: body, prefer: nil, token: nil)
        if (400..<500).contains(status) {
            let notice = "Signed out. Sign in again to keep syncing."
            signOut(notice: notice)
            throw ServerError(message: notice)
        }
        guard (200..<300).contains(status), let fresh = try Self.session(from: data, email: session.email) else {
            throw ServerError(message: Self.message(in: data, status: status))
        }
        self.session = fresh
    }

    private nonisolated static func request(_ method: String, _ path: String, query: String?, body: Data?, prefer: String?,
                                            token: String?) async throws -> (Data, Int) {
        var request = URLRequest(url: URL(string: "\(baseURL)/\(path)" + (query.map { "?\($0)" } ?? ""))!)
        request.httpMethod = method
        request.httpBody = body
        request.timeoutInterval = 20
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let prefer { request.setValue(prefer, forHTTPHeaderField: "Prefer") }
        let data: Data, response: URLResponse
        do {
            (data, response) = try await http.data(for: request)
        } catch let error as URLError where error.code != .cancelled {
            throw ServerError(message: "Couldn't connect. Check your connection and try again.")
        }
        return (data, (response as? HTTPURLResponse)?.statusCode ?? 0)
    }

    /// Auth answers `msg` (or `error_description`), the REST API `message`.
    private nonisolated static func message(in data: Data, status: Int) -> String {
        struct Body: Decodable { let msg, error_description, message: String? }
        let body = try? JSONDecoder().decode(Body.self, from: data)
        return body?.msg ?? body?.error_description ?? body?.message ?? "The server answered \(status)."
    }

    /// nil when the answer has no session: sign-up while email confirmation is on.
    private nonisolated static func session(from data: Data, email: String) throws -> Session? {
        struct Body: Decodable {
            struct User: Decodable { let email: String? }
            let access_token, refresh_token: String?
            let expires_at: TimeInterval?
            let user: User?
        }
        let body = try JSONDecoder().decode(Body.self, from: data)
        guard let access = body.access_token, let refresh = body.refresh_token else { return nil }
        return Session(accessToken: access, refreshToken: refresh, expiresAt: Date(timeIntervalSince1970: body.expires_at ?? 0),
                       email: body.user?.email ?? email)
    }
}

/// The session as one Keychain item, readable after the first unlock so a sync on the way to the background works.
private enum Keychain {
    static var item: [CFString: Any] {
        [kSecClass: kSecClassGenericPassword, kSecAttrService: "com.g1lg1l.stash", kSecAttrAccount: "session"]
    }

    static func load() -> Account.Session? {
        var result: CFTypeRef?
        guard SecItemCopyMatching(item.merging([kSecReturnData: true]) { $1 } as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data
        else { return nil }
        return try? JSONDecoder().decode(Account.Session.self, from: data)
    }

    static func store(_ session: Account.Session?) {
        SecItemDelete(item as CFDictionary)
        guard let session, let data = try? JSONEncoder().encode(session) else { return }
        SecItemAdd(item.merging([kSecValueData: data, kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]) { $1 } as CFDictionary, nil)
    }
}

/// The `saves` table on the wire, and the pull rules. Pure where it can be, so tests cover it without a server.
enum Sync {
    static let pageSize = 500

    /// One `saves` row, in the server's snake_case. `user_id` and `updated_at` are the server's to set.
    struct Row: Codable {
        static let columns = "id,url,canonical_url,source,content_type,category,status,category_is_manual,title,description_text,thumbnail_url,author,tags,summary,created_at,last_saved_at,opened_at,deleted_at"

        var id: String
        var url: String
        var canonical_url: String
        var source: String
        var content_type: String
        var category: String
        var status: String
        var category_is_manual: Bool
        var title: String?
        var description_text: String?
        var thumbnail_url: String?
        var author: String?
        var tags: [String]
        var summary: String?
        var created_at: String
        var last_saved_at: String
        var opened_at: String?
        var updated_at: String?
        var deleted_at: String?
    }

    enum Action: Equatable {
        case skip, delete, update, insert
        /// The same link saved on two devices: every device keeps `survivor` and tombstones `loser`.
        case merge(survivor: UUID, loser: UUID)
    }

    /// What one pulled row does to the local store, given the local save with its id and the one with its canonical URL.
    static func action(for row: Row, sameID: Save?, sameURL: Save?, pushStart: Date) -> Action {
        if let sameID, sameID.modifiedAt > pushStart { return .skip } // Changed during this sync: the next push wins.
        if row.deleted_at != nil { return .delete }
        if sameID != nil { return .update }
        if let sameURL, let id = UUID(uuidString: row.id) {
            // Lowest id as a lowercase string, so both devices pick the same one. Keeping "the server's" would lose it.
            let ids = [id, sameURL.id].sorted { $0.uuidString.lowercased() < $1.uuidString.lowercased() }
            return .merge(survivor: ids[0], loser: ids[1])
        }
        return .insert
    }

    /// Applies one page of pulled rows, without saving. Never bumps `modifiedAt`, except on a merge, which must go back up.
    static func apply(_ rows: [Row], in context: ModelContext, pushStart: Date) {
        for row in rows {
            guard let id = UUID(uuidString: row.id), let url = URL(string: row.url) else { continue }
            // Fetches see this page's unsaved inserts, so a link twice in one page still merges.
            let sameID = try? context.fetch(FetchDescriptor<Save>(predicate: #Predicate { $0.id == id })).first
            let key = row.canonical_url
            let sameURL = sameID != nil ? nil : try? context.fetch(FetchDescriptor<Save>(predicate: #Predicate { $0.canonicalURL == key })).first
            switch action(for: row, sameID: sameID, sameURL: sameURL, pushStart: pushStart) {
            case .skip:
                break
            case .delete:
                if let sameID { context.delete(sameID) }
            case .update:
                sameID?.update(from: row)
            case .merge(let survivor, let loser):
                guard let save = sameURL else { break }
                save.merge(row)
                save.id = survivor
                context.insert(Tombstone(id: loser))
                save.touch()
            case .insert:
                let save = Save(url: url)
                save.id = id
                save.update(from: row)
                save.modifiedAt = Date(timeIntervalSince1970: 0)
                context.insert(save)
            }
        }
    }

    /// The cursor is the server's exact `updated_at`, `+00:00` included: a bare `+` in a query reads as a space.
    static func pullQuery(after cursor: String?) -> String {
        let filter = cursor.map { "&updated_at=gt." + $0.addingPercentEncoding(withAllowedCharacters: .alphanumerics.union(.init(charactersIn: "-._:")))! }
        return "select=*\(filter ?? "")&order=updated_at.asc,id.asc&limit=\(pageSize)"
    }

    // Milliseconds in UTC going out; the server answers microseconds and +00:00, which this parses too.
    private static let iso = Date.ISO8601FormatStyle(includingFractionalSeconds: true)
    static func string(_ date: Date) -> String { date.formatted(iso) }
    static func date(_ string: String?) -> Date? { string.flatMap { try? iso.parse($0) } }
}

extension Sync.Row {
    init(_ save: Save) {
        self.init(
            id: save.id.uuidString.lowercased(), url: save.url.absoluteString, canonical_url: save.canonicalURL,
            source: save.sourceRaw, content_type: save.contentTypeRaw, category: save.categoryRaw, status: save.statusRaw,
            category_is_manual: save.categoryIsManual, title: save.title, description_text: save.descriptionText,
            thumbnail_url: save.thumbnailURL?.absoluteString, author: save.author, tags: save.tags, summary: save.summary,
            created_at: Sync.string(save.createdAt), last_saved_at: Sync.string(save.lastSavedAt), opened_at: save.openedAt.map(Sync.string)
        )
    }
}

extension Save {
    /// The server's copy replaces every field. Raw strings as they are: unknown values fall back on read.
    func update(from row: Sync.Row) {
        url = URL(string: row.url) ?? url
        canonicalURL = row.canonical_url
        sourceRaw = row.source
        contentTypeRaw = row.content_type
        categoryRaw = row.category
        statusRaw = row.status
        categoryIsManual = row.category_is_manual
        title = row.title
        descriptionText = row.description_text
        thumbnailURL = row.thumbnail_url.flatMap(URL.init(string:))
        author = row.author
        tags = row.tags
        summary = row.summary
        createdAt = Sync.date(row.created_at) ?? createdAt
        lastSavedAt = Sync.date(row.last_saved_at) ?? lastSavedAt
        openedAt = Sync.date(row.opened_at)
    }

    /// The same link from another device, merged as `SaveStore.add` merges a re-share: the server's metadata,
    /// with this copy filling its gaps, the earliest share and the latest re-share.
    func merge(_ row: Sync.Row) {
        url = URL(string: row.url) ?? url
        sourceRaw = row.source
        contentTypeRaw = row.content_type
        // A manual category beats both; otherwise the server's, unless it's an unclassified "other".
        if row.category_is_manual || (!categoryIsManual && row.category != Category.other.rawValue) {
            categoryRaw = row.category
            categoryIsManual = row.category_is_manual
        }
        if status != .enriched { statusRaw = row.status } // Enriched if either copy is.
        title = row.title ?? title
        descriptionText = row.description_text ?? descriptionText
        thumbnailURL = row.thumbnail_url.flatMap(URL.init(string:)) ?? thumbnailURL
        author = row.author ?? author
        if !row.tags.isEmpty { tags = row.tags }
        summary = row.summary ?? summary
        createdAt = min(createdAt, Sync.date(row.created_at) ?? createdAt)
        lastSavedAt = max(lastSavedAt, Sync.date(row.last_saved_at) ?? lastSavedAt)
        openedAt = Sync.date(row.opened_at) ?? openedAt
    }
}
