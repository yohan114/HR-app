import Foundation
import GRDB
import OSLog

/// The read side of offline sync.
///
/// Implements docs/sync-protocol.md §3, mirroring `SyncEngine` on Android. Pulls changes by opaque
/// cursor and applies them to the local store, which is what the UI observes.
///
/// **The server endpoint does not exist yet** (P0-BE-33 — it is deliberately absent from the
/// OpenAPI spec until implemented, so the generated clients do not expose a method that 404s).
/// The engine is built now because the protocol, persistence and failure handling are the parts
/// that take time to get right; wiring it to the endpoint is the small remainder. ``SyncAPI`` is
/// the seam.
public actor SyncEngine {
    private let database: DatabaseQueue
    private let api: SyncAPI
    private let appliers: [String: any ChangeApplier]
    private let clock: @Sendable () -> Date
    private let log = Logger(subsystem: "io.hrapp", category: "SyncEngine")

    public init(
        database: DatabaseQueue,
        api: SyncAPI,
        appliers: [String: any ChangeApplier],
        clock: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.database = database
        self.api = api
        self.appliers = appliers
        self.clock = clock
    }

    /// Syncs one scope to completion.
    ///
    /// Loops until the server reports no more pages. Each page is applied in a single transaction
    /// together with its cursor — see ``apply(scope:page:)`` for why that matters.
    public func sync(scope: String) async -> SyncResult {
        var pages = 0
        var applied = 0

        do {
            while true {
                let since = try await currentCursor(for: scope)
                let page = try await api.fetch(scope: scope, since: since, limit: 500)

                try await apply(scope: scope, page: page)
                pages += 1
                applied += page.changes.count + page.deletes.count

                if !page.hasMore { break }
                if pages >= Self.maxPagesPerRun {
                    // Defensive: a server bug that always reports `hasMore` would otherwise spin
                    // here forever, draining the battery with nobody noticing.
                    log.warning("Stopping sync of '\(scope)' after \(Self.maxPagesPerRun) pages")
                    return .incomplete(recordsApplied: applied)
                }
            }
            return .success(recordsApplied: applied)
        } catch is SyncCursorExpired {
            // Expected for a device offline longer than the change-feed retention. Not an error to
            // show the user beyond a progress indicator.
            log.info("Cursor for '\(scope)' expired; performing a full resync")
            do {
                try await resetScope(scope)
                return await sync(scope: scope)
            } catch {
                return .failed(error)
            }
        } catch {
            try? await recordFailure(scope: scope, error: error)
            return .failed(error)
        }
    }

    /// Applies one page of changes.
    ///
    /// The cursor is persisted **inside the same transaction** as the data. Writing them
    /// separately means a crash between the two either loses changes (cursor advanced, data not
    /// written — silent and unrecoverable) or reprocesses them. The first is the reason this is a
    /// transaction and not two statements.
    private func apply(scope: String, page: SyncPage) async throws {
        try await database.write { [appliers, clock] db in
            for change in page.changes {
                guard let applier = appliers[change.entityType] else {
                    // An entity type we do not know about. Skipping is correct: an older client
                    // must not fail because the server added something new.
                    continue
                }
                try applier.upsert(db, entityId: change.entityId, payload: change.payload)
            }

            for deletion in page.deletes {
                try appliers[deletion.entityType]?.delete(db, entityId: deletion.entityId)
            }

            var cursor = SyncCursor(
                scope: scope,
                cursor: page.cursor,
                lastSyncedAt: clock(),
                lastAttemptAt: clock(),
                lastError: nil
            )
            try cursor.save(db)
        }
    }

    /// Discards local state for a scope so the next sync starts from scratch.
    public func resetScope(_ scope: String) async throws {
        try await database.write { [appliers] db in
            for applier in appliers.values {
                try applier.clear(db, scope: scope)
            }
            _ = try SyncCursor.deleteOne(db, key: scope)
        }
    }

    public func clearAll() async throws {
        try await database.write { [appliers] db in
            for applier in appliers.values {
                try applier.clearAll(db)
            }
            _ = try SyncCursor.deleteAll(db)
        }
    }

    private func currentCursor(for scope: String) async throws -> String? {
        try await database.read { db in
            try SyncCursor.fetchOne(db, key: scope)?.cursor
        }
    }

    private func recordFailure(scope: String, error: Error) async throws {
        try await database.write { [clock] db in
            var cursor = try SyncCursor.fetchOne(db, key: scope) ?? SyncCursor(scope: scope)
            cursor.lastAttemptAt = clock()
            cursor.lastError = error.localizedDescription
            try cursor.save(db)
        }
    }

    private static let maxPagesPerRun = 200
}

/// Applies changes for one entity type.
///
/// Each syncable entity registers one of these. The conflict strategy for the entity type
/// (docs/sync-protocol.md §5) is implemented here — there is deliberately no generic
/// last-write-wins, because LWW is wrong for every entity type in this product.
public protocol ChangeApplier: Sendable {
    func upsert(_ db: Database, entityId: String, payload: String) throws
    func delete(_ db: Database, entityId: String) throws
    func clear(_ db: Database, scope: String) throws
    func clearAll(_ db: Database) throws
}

/// The seam onto `GET /v1/sync`. Implemented against the generated client once P0-BE-33 lands.
public protocol SyncAPI: Sendable {
    func fetch(scope: String, since: String?, limit: Int) async throws -> SyncPage
}

public struct SyncPage: Sendable, Equatable {
    public let changes: [SyncChange]
    public let deletes: [SyncDeletion]
    public let cursor: String
    public let hasMore: Bool

    public init(changes: [SyncChange], deletes: [SyncDeletion], cursor: String, hasMore: Bool) {
        self.changes = changes
        self.deletes = deletes
        self.cursor = cursor
        self.hasMore = hasMore
    }
}

public struct SyncChange: Sendable, Equatable {
    public let entityType: String
    public let entityId: String
    public let payload: String

    public init(entityType: String, entityId: String, payload: String) {
        self.entityType = entityType
        self.entityId = entityId
        self.payload = payload
    }
}

public struct SyncDeletion: Sendable, Equatable {
    public let entityType: String
    public let entityId: String

    public init(entityType: String, entityId: String) {
        self.entityType = entityType
        self.entityId = entityId
    }
}

/// Signals HTTP 410 `SYNC_CURSOR_EXPIRED`: discard local state and resync from scratch.
public struct SyncCursorExpired: Error, Sendable {
    public let scope: String
    public init(scope: String) { self.scope = scope }
}

public enum SyncResult: Sendable {
    case success(recordsApplied: Int)
    case incomplete(recordsApplied: Int)
    case failed(Error)
}
