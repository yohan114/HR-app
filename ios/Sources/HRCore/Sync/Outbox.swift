import Foundation
import GRDB

/// The write side of offline sync.
///
/// Implements docs/sync-protocol.md §4, mirroring `Outbox` on Android. Callers enqueue a mutation
/// and return immediately; the UI has already been updated from the local write that accompanied
/// it.
public actor Outbox {
    private let database: DatabaseQueue
    private let clock: @Sendable () -> Date

    public init(database: DatabaseQueue, clock: @escaping @Sendable () -> Date = { Date() }) {
        self.database = database
        self.clock = clock
    }

    /// Queues a mutation.
    ///
    /// The idempotency key is generated here, once. It must never be regenerated on retry — that
    /// is precisely what makes unlimited retries safe, and regenerating it would turn a lost
    /// response into a duplicate record.
    @discardableResult
    public func enqueue(
        aggregateType: String,
        aggregateId: String,
        httpMethod: String,
        path: String,
        payload: String
    ) async throws -> String {
        var entry = OutboxEntry(
            aggregateType: aggregateType,
            aggregateId: aggregateId,
            httpMethod: httpMethod,
            path: path,
            payload: payload,
            createdAt: clock()
        )
        try await database.write { db in
            try entry.insert(db)
        }
        return entry.id
    }

    /// The next entry to send for each aggregate.
    ///
    /// One per aggregate is what gives ordering within an aggregate and concurrency across them
    /// (docs/sync-protocol.md §4.2).
    public func nextBatch(limit: Int = 20) async throws -> [OutboxEntry] {
        let now = clock()
        return try await database.read { db in
            try OutboxEntry
                .filter(Column("state") == OutboxState.pending.rawValue)
                .filter(Column("nextAttemptAt") <= now)
                .order(Column("createdAt").asc)
                .fetchAll(db)
                .reduce(into: [String: OutboxEntry]()) { earliest, entry in
                    let key = "\(entry.aggregateType)|\(entry.aggregateId)"
                    if earliest[key] == nil { earliest[key] = entry }
                }
                .values
                .sorted { $0.createdAt < $1.createdAt }
                .prefix(limit)
                .map { $0 }
        }
    }

    public func markInFlight(_ entry: OutboxEntry) async throws {
        var updated = entry
        updated.state = .inFlight
        updated.lastAttemptAt = clock()
        try await database.write { db in try updated.update(db) }
    }

    public func markConfirmed(_ entry: OutboxEntry) async throws {
        _ = try await database.write { db in
            try OutboxEntry.deleteOne(db, key: entry.id)
        }
    }

    /// Records a terminal business rejection.
    ///
    /// The payload is deliberately retained. Discarding what someone typed because the server said
    /// no is hostile — the UI offers it back for editing (docs/sync-protocol.md §4.1).
    public func markRejected(_ entry: OutboxEntry, code: String?, message: String?) async throws {
        var updated = entry
        updated.state = .rejected
        updated.failureCode = code
        updated.failureMessage = message
        updated.lastAttemptAt = clock()
        try await database.write { db in try updated.update(db) }
    }

    /// Schedules a retry, or gives up if the entry has been trying for too long.
    public func scheduleRetry(_ entry: OutboxEntry) async throws {
        let now = clock()
        var updated = entry
        updated.attemptCount = entry.attemptCount + 1
        updated.lastAttemptAt = now

        if now.timeIntervalSince(entry.createdAt) > Self.retryDeadline {
            updated.state = .failed
            updated.failureCode = "RETRY_DEADLINE_EXCEEDED"
            updated.failureMessage = "Could not reach the server for 7 days"
        } else {
            updated.state = .pending
            updated.nextAttemptAt = now.addingTimeInterval(Self.backoff(attempt: updated.attemptCount))
        }

        try await database.write { db in try updated.update(db) }
    }

    /// Requeues entries stranded `inFlight` by a process termination. Safe: idempotency keys.
    @discardableResult
    public func recoverStranded() async throws -> Int {
        try await database.write { db in
            try OutboxEntry
                .filter(Column("state") == OutboxState.inFlight.rawValue)
                .updateAll(db, Column("state").set(to: OutboxState.pending.rawValue))
        }
    }

    /// Called on sign-out. Queued mutations are discarded after the user is warned.
    public func clear() async throws {
        _ = try await database.write { db in
            try OutboxEntry.deleteAll(db)
        }
    }

    public func pendingCount() async throws -> Int {
        try await database.read { db in
            try OutboxEntry
                .filter([OutboxState.pending.rawValue, OutboxState.inFlight.rawValue].contains(Column("state")))
                .fetchCount(db)
        }
    }

    /// Exponential backoff with **full jitter**.
    ///
    /// Jitter is not decoration: without it every device in a company retries in lockstep after an
    /// outage, and the recovering server is immediately knocked over again by its own users.
    /// Sampling uniformly from `[0, bound]` spreads the herd far better than a narrow band around
    /// the target.
    ///
    /// Kept identical to `Outbox.backoffMillis` on Android.
    static func backoff(attempt: Int) -> TimeInterval {
        let exponential = baseBackoff * pow(2, Double(min(attempt - 1, maxShift)))
        let capped = min(exponential, maxBackoff)
        return TimeInterval.random(in: 0...capped)
    }

    private static let baseBackoff: Double = 1
    private static let maxBackoff: Double = 5 * 60
    private static let maxShift = 20
    private static let retryDeadline: TimeInterval = 7 * 24 * 60 * 60
}

/// Sends one outbox entry. Implemented against the generated `HRClient`.
public protocol OutboxSender: Sendable {
    func send(_ entry: OutboxEntry) async -> SendOutcome
}

public enum SendOutcome: Sendable, Equatable {
    /// 2xx, or 409 `ALREADY_DECIDED` — someone else acted first, which is still a settled outcome.
    case confirmed
    /// Terminal 4xx. Will never succeed; the user's input is kept for editing.
    case rejected(code: String?, message: String?)
    /// 5xx or network. Retry with backoff.
    case retryable
    /// 401 that survived a token refresh. Pause the drain.
    case authenticationRequired
}
