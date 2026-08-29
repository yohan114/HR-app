import Foundation
import GRDB
import XCTest
@testable import HRCore

/// Mirrors `OutboxTest` on Android.
///
/// The test names are deliberately kept in step across the two platforms: docs/sync-protocol.md §9
/// lists the scenarios both clients must cover, and matching names make a gap on one platform
/// obvious when the two reports are read side by side.
final class OutboxTests: XCTestCase {
    private var database: DatabaseQueue!
    private var outbox: Outbox!
    private var now = Date(timeIntervalSince1970: 1_000_000)

    override func setUp() async throws {
        try await super.setUp()
        database = try TestDatabase.make()
        let fixedNow = now
        outbox = Outbox(database: database, clock: { fixedNow })
    }

    override func tearDown() async throws {
        database = nil
        outbox = nil
        try await super.tearDown()
    }

    func testEnqueueAssignsDistinctIdempotencyKeys() async throws {
        _ = try await outbox.enqueue(
            aggregateType: "leaveApplication", aggregateId: "a",
            httpMethod: "POST", path: "/v1/leave/applications", payload: "{}"
        )
        _ = try await outbox.enqueue(
            aggregateType: "leaveApplication", aggregateId: "b",
            httpMethod: "POST", path: "/v1/leave/applications", payload: "{}"
        )

        let entries = try await database.read { try OutboxEntry.fetchAll($0) }
        XCTAssertEqual(entries.count, 2)
        XCTAssertNotEqual(entries[0].idempotencyKey, entries[1].idempotencyKey)
        XCTAssertTrue(entries.allSatisfy { $0.state == .pending })
    }

    /// The property that makes indefinite retry safe. If a retry regenerated the key, a response
    /// lost on the return path would produce a duplicate leave application or a double punch.
    func testRetryPreservesIdempotencyKey() async throws {
        let entry = try await insert(createdAt: now)

        try await outbox.scheduleRetry(entry)

        let reloaded = try await database.read { try OutboxEntry.fetchOne($0, key: entry.id) }
        XCTAssertEqual(reloaded?.idempotencyKey, entry.idempotencyKey)
        XCTAssertEqual(reloaded?.attemptCount, 1)
        XCTAssertEqual(reloaded?.state, .pending)
    }

    func testBackoffGrowsExponentiallyAndIsCapped() {
        // Full jitter means each value is a sample from [0, bound], so assert the bound rather
        // than an exact figure — pinning the exact value would be asserting the RNG.
        let firstAttempt = (0..<200).map { _ in Outbox.backoff(attempt: 1) }.max() ?? 0
        let fourthAttempt = (0..<200).map { _ in Outbox.backoff(attempt: 4) }.max() ?? 0
        let veryLate = (0..<200).map { _ in Outbox.backoff(attempt: 12) }.max() ?? 0

        XCTAssertLessThanOrEqual(firstAttempt, 1)
        XCTAssertLessThanOrEqual(fourthAttempt, 8)
        XCTAssertLessThanOrEqual(veryLate, 5 * 60)
    }

    func testBackoffIsJittered() {
        // Without jitter every device in a company retries in lockstep after an outage and knocks
        // the recovering server over again.
        let values = Set((0..<200).map { _ in Outbox.backoff(attempt: 6) })

        XCTAssertGreaterThan(values.count, 1)
    }

    func testEntryPastDeadlineIsMarkedFailed() async throws {
        let eightDaysAgo = now.addingTimeInterval(-8 * 24 * 60 * 60)
        let entry = try await insert(createdAt: eightDaysAgo)

        try await outbox.scheduleRetry(entry)

        let reloaded = try await database.read { try OutboxEntry.fetchOne($0, key: entry.id) }
        XCTAssertEqual(reloaded?.state, .failed)
        XCTAssertEqual(reloaded?.failureCode, "RETRY_DEADLINE_EXCEEDED")
    }

    func testEntryInsideDeadlineStaysPending() async throws {
        let sixDaysAgo = now.addingTimeInterval(-6 * 24 * 60 * 60)
        let entry = try await insert(createdAt: sixDaysAgo)

        try await outbox.scheduleRetry(entry)

        let reloaded = try await database.read { try OutboxEntry.fetchOne($0, key: entry.id) }
        XCTAssertEqual(reloaded?.state, .pending)
    }

    /// Discarding what someone typed because the server said no is hostile. The UI offers it back
    /// for editing, which it can only do if the payload survives.
    func testRejectionRetainsPayload() async throws {
        let entry = try await insert(createdAt: now)

        try await outbox.markRejected(entry, code: "LEAVE_BALANCE_INSUFFICIENT", message: "Not enough balance")

        let reloaded = try await database.read { try OutboxEntry.fetchOne($0, key: entry.id) }
        XCTAssertEqual(reloaded?.state, .rejected)
        XCTAssertEqual(reloaded?.payload, entry.payload)
        XCTAssertEqual(reloaded?.failureCode, "LEAVE_BALANCE_INSUFFICIENT")
    }

    func testConfirmationRemovesEntry() async throws {
        let entry = try await insert(createdAt: now)

        try await outbox.markConfirmed(entry)

        let count = try await database.read { try OutboxEntry.fetchCount($0) }
        XCTAssertEqual(count, 0)
    }

    func testStrandedInFlightEntriesAreRequeued() async throws {
        var entry = OutboxEntry(
            aggregateType: "leaveApplication", aggregateId: "agg-1",
            httpMethod: "POST", path: "/v1/leave/applications",
            payload: "{}", state: .inFlight, createdAt: now
        )
        try await database.write { try entry.insert($0) }

        let recovered = try await outbox.recoverStranded()

        XCTAssertEqual(recovered, 1)
        let reloaded = try await database.read { try OutboxEntry.fetchOne($0, key: entry.id) }
        XCTAssertEqual(reloaded?.state, .pending)
    }

    /// Ordering within an aggregate, concurrency across aggregates (docs/sync-protocol.md §4.2).
    func testNextBatchReturnsOldestPerAggregate() async throws {
        _ = try await insert(createdAt: now.addingTimeInterval(-30), aggregateId: "a", id: "a-old")
        _ = try await insert(createdAt: now.addingTimeInterval(-10), aggregateId: "a", id: "a-new")
        _ = try await insert(createdAt: now.addingTimeInterval(-20), aggregateId: "b", id: "b-only")

        let batch = try await outbox.nextBatch()

        XCTAssertEqual(Set(batch.map(\.id)), ["a-old", "b-only"])
    }

    // MARK: - Helpers

    @discardableResult
    private func insert(
        createdAt: Date,
        aggregateId: String = "agg-1",
        id: String = UUID().uuidString
    ) async throws -> OutboxEntry {
        var entry = OutboxEntry(
            id: id,
            aggregateType: "leaveApplication",
            aggregateId: aggregateId,
            httpMethod: "POST",
            path: "/v1/leave/applications",
            payload: #"{"days":3}"#,
            state: .pending,
            createdAt: createdAt
        )
        try await database.write { try entry.insert($0) }
        return entry
    }

}
