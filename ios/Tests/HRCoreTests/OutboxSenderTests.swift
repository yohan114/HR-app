import Foundation
import GRDB
import XCTest
@testable import HRCore

/// The outbox status mapping, which decides whether a user's submission survives.
///
/// Deliberately named in step with the Android suite: docs/sync-protocol.md §4.4 lists the
/// mapping both clients must implement, and matching test names make a divergence obvious when
/// the two reports are read side by side. A difference here is not an inconsistency — it is
/// "leave requests silently vanish on iPhones".
final class OutboxSenderTests: XCTestCase {
    /// Drives the drain without a network, by returning a fixed outcome.
    private struct StubSender: OutboxSender {
        let outcome: SendOutcome
        func send(_ entry: OutboxEntry) async -> SendOutcome { outcome }
    }

    private func entry() -> OutboxEntry {
        OutboxEntry(
            aggregateType: "leaveApplication",
            aggregateId: "a1",
            httpMethod: "POST",
            path: "/v1/leave/applications",
            payload: "{}",
            createdAt: Date(timeIntervalSince1970: 1_000_000)
        )
    }

    // MARK: - Classification

    func testSuccessIsConfirmed() {
        XCTAssertEqual(classify(status: 200), .confirmed)
        XCTAssertEqual(classify(status: 201), .confirmed)
        XCTAssertEqual(classify(status: 204), .confirmed)
    }

    /// Someone else approved it first. That is a settled outcome, not a failure — retrying would
    /// never change it, and showing an error would be misleading.
    func testAlreadyDecidedIsConfirmedRatherThanRejected() {
        let outcome = classify(status: 409, body: #"{"error":{"code":"ALREADY_DECIDED"}}"#)
        XCTAssertEqual(outcome, .confirmed)
    }

    /// A 409 that is *not* ALREADY_DECIDED is a genuine conflict and must not be swallowed.
    func testOtherConflictsAreRejected() {
        let outcome = classify(status: 409, body: #"{"error":{"code":"STALE_VERSION"}}"#)
        XCTAssertEqual(outcome, .rejected(code: "STALE_VERSION", message: nil))
    }

    func testServerErrorsAreRetryable() {
        XCTAssertEqual(classify(status: 500), .retryable)
        XCTAssertEqual(classify(status: 503), .retryable)
        XCTAssertEqual(classify(status: 429), .retryable)
    }

    /// A terminal 4xx will never succeed, so retrying it forever would drain the battery for
    /// nothing — and the payload is kept so the UI can offer it back for editing.
    func testClientErrorsAreRejectedWithTheServerCode() {
        let outcome = classify(
            status: 422,
            body: #"{"error":{"code":"INSUFFICIENT_BALANCE","message":"Not enough leave"}}"#
        )
        XCTAssertEqual(outcome, .rejected(code: "INSUFFICIENT_BALANCE", message: "Not enough leave"))
    }

    func testUnauthorizedPausesTheDrain() {
        XCTAssertEqual(classify(status: 401), .authenticationRequired)
    }

    /// A 502 from a load balancer is an HTML page; a proxy timeout may be empty. Neither should
    /// become a decode failure — the status already says everything actionable.
    func testANonJsonErrorBodyStillClassifiesByStatus() {
        XCTAssertEqual(classify(status: 503, body: "<html>Bad Gateway</html>"), .retryable)
        XCTAssertEqual(classify(status: 400, body: ""), .rejected(code: nil, message: nil))
    }

    // MARK: - Drain behaviour

    func testDrainStopsOnAuthenticationRequired() async throws {
        let outbox = Outbox(database: try TestDatabase.make())
        for index in 0..<3 {
            _ = try await outbox.enqueue(
                aggregateType: "leaveApplication", aggregateId: "a\(index)",
                httpMethod: "POST", path: "/v1/leave/applications", payload: "{}"
            )
        }

        let summary = await OutboxDrain(
            outbox: outbox,
            sender: StubSender(outcome: .authenticationRequired)
        ).drain()

        XCTAssertTrue(summary.stoppedEarly)
        XCTAssertEqual(summary.confirmed, 0)
        // Exactly one attempt, not three: hammering the auth endpoint helps nobody.
        XCTAssertEqual(summary.retrying, 1)
    }

    func testDrainConfirmsAndRemovesEntries() async throws {
        let outbox = Outbox(database: try TestDatabase.make())
        _ = try await outbox.enqueue(
            aggregateType: "leaveApplication", aggregateId: "a1",
            httpMethod: "POST", path: "/v1/leave/applications", payload: "{}"
        )

        let summary = await OutboxDrain(
            outbox: outbox,
            sender: StubSender(outcome: .confirmed)
        ).drain()

        XCTAssertEqual(summary.confirmed, 1)
        let remaining = try await outbox.pendingCount()
        XCTAssertEqual(remaining, 0)
    }

    /// `BGTaskScheduler` gives roughly thirty seconds and then kills the app — and an app killed
    /// that way is penalised in future scheduling, so overrunning makes the *next* window less
    /// likely to be granted.
    func testDrainStopsWhenCancelled() async throws {
        let outbox = Outbox(database: try TestDatabase.make())
        for index in 0..<5 {
            _ = try await outbox.enqueue(
                aggregateType: "leaveApplication", aggregateId: "a\(index)",
                httpMethod: "POST", path: "/v1/leave/applications", payload: "{}"
            )
        }

        let summary = await OutboxDrain(
            outbox: outbox,
            sender: StubSender(outcome: .confirmed)
        ).drain(isCancelled: { true })

        XCTAssertTrue(summary.stoppedEarly)
        XCTAssertEqual(summary.confirmed, 0, "work continued after cancellation")
    }

    // MARK: - Helpers

    private func classify(status: Int, body: String = "") -> SendOutcome {
        let sender = OutboxHTTPSender(
            client: HTTPClient(configuration: APIConfiguration(baseURL: URL(string: "https://example.invalid")!))
        )
        return sender.classify(
            HTTPClient.Response(status: status, body: Data(body.utf8)),
            entry: entry()
        )
    }
}
