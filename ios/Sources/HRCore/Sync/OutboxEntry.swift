import Foundation
import GRDB

/// A queued user mutation awaiting confirmation from the server.
///
/// Mirrors `OutboxEntry` on Android. See docs/sync-protocol.md §4.
///
/// The contract that makes indefinite retry safe is ``idempotencyKey``: generated once when the
/// entry is created, never regenerated on retry, and sent as the `Idempotency-Key` header. The
/// server records it and returns the original outcome for a replay, so a response lost on the
/// return path cannot produce a duplicate leave application or a double attendance punch.
public struct OutboxEntry: Codable, FetchableRecord, MutablePersistableRecord, Identifiable, Sendable, Equatable {
    public static let databaseTableName = "outbox"

    public var id: String
    /// UUID. Generated once, at creation. Never regenerated.
    public var idempotencyKey: String
    /// Entries drain in order within an aggregate, and concurrently across aggregates.
    public var aggregateType: String
    public var aggregateId: String
    public var httpMethod: String
    public var path: String
    /// Serialised JSON request body. May contain personal data — the store is encrypted.
    public var payload: String
    public var state: OutboxState
    public var attemptCount: Int
    public var createdAt: Date
    public var lastAttemptAt: Date?
    /// Earliest time the next attempt may run. Drives exponential backoff with jitter.
    public var nextAttemptAt: Date
    public var failureCode: String?
    public var failureMessage: String?

    public init(
        id: String = UUID().uuidString,
        idempotencyKey: String = UUID().uuidString,
        aggregateType: String,
        aggregateId: String,
        httpMethod: String,
        path: String,
        payload: String,
        state: OutboxState = .pending,
        attemptCount: Int = 0,
        createdAt: Date,
        lastAttemptAt: Date? = nil,
        nextAttemptAt: Date = .distantPast,
        failureCode: String? = nil,
        failureMessage: String? = nil
    ) {
        self.id = id
        self.idempotencyKey = idempotencyKey
        self.aggregateType = aggregateType
        self.aggregateId = aggregateId
        self.httpMethod = httpMethod
        self.path = path
        self.payload = payload
        self.state = state
        self.attemptCount = attemptCount
        self.createdAt = createdAt
        self.lastAttemptAt = lastAttemptAt
        self.nextAttemptAt = nextAttemptAt
        self.failureCode = failureCode
        self.failureMessage = failureMessage
    }
}

public enum OutboxState: String, Codable, DatabaseValueConvertible, Sendable {
    /// Written locally, not yet accepted by the server. The UI shows a queued badge.
    case pending = "PENDING"
    /// Currently being sent. Guards against two drains picking up the same entry.
    case inFlight = "IN_FLIGHT"
    /// Server rejected it on business grounds. Terminal; the user's input is kept for editing.
    case rejected = "REJECTED"
    /// Retried past the deadline. Terminal until the user retries manually.
    case failed = "FAILED"
}

/// The sync position for one scope.
///
/// The cursor is opaque and must not be parsed, compared or sorted — see
/// docs/sync-protocol.md §3.4.
public struct SyncCursor: Codable, FetchableRecord, MutablePersistableRecord, Sendable, Equatable {
    public static let databaseTableName = "sync_cursor"

    public var scope: String
    public var cursor: String?
    public var lastSyncedAt: Date?
    public var lastAttemptAt: Date?
    public var lastError: String?

    public init(
        scope: String,
        cursor: String? = nil,
        lastSyncedAt: Date? = nil,
        lastAttemptAt: Date? = nil,
        lastError: String? = nil
    ) {
        self.scope = scope
        self.cursor = cursor
        self.lastSyncedAt = lastSyncedAt
        self.lastAttemptAt = lastAttemptAt
        self.lastError = lastError
    }
}

/// Local sync state for a business record. See the state machine in docs/sync-protocol.md §6.
public enum SyncState: String, Codable, DatabaseValueConvertible, Sendable {
    case pending = "PENDING"
    case confirmed = "CONFIRMED"
    case rejected = "REJECTED"
    case failed = "FAILED"
}
