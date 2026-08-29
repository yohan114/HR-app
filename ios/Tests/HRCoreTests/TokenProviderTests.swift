import Foundation
import XCTest
@testable import HRCore

/// The single-flight refresh guarantee.
///
/// This is the most consequential property in the networking layer, and the one whose absence
/// would be hardest to diagnose in the field. The server rotates refresh tokens and treats reuse
/// as theft by revoking the whole family — so a client that refreshes concurrently signs the user
/// out of every device they own, and the crash report says nothing because nothing crashed.
///
/// The failure needs concurrency to reproduce, which means it will not appear in manual testing
/// and will appear under real network conditions. That is exactly the shape of bug worth an
/// explicit test.
final class TokenProviderTests: XCTestCase {
    /// Counts refresh calls and can be held open to force overlap.
    private actor RecordingRefreshService: TokenProvider.RefreshService {
        private(set) var callCount = 0
        private var gate: CheckedContinuation<Void, Never>?
        private var shouldFail: Error?
        var issued = 0

        func setFailure(_ error: Error?) { shouldFail = error }

        /// Suspends the next call until ``release()``.
        func hold() async {
            await withCheckedContinuation { continuation in
                gate = continuation
            }
        }

        func release() {
            gate?.resume()
            gate = nil
        }

        var isHolding: Bool { gate != nil }

        func refresh(refreshToken: String) async throws -> TokenProvider.TokenPair {
            callCount += 1
            if gate != nil || callCount == 1 {
                // First caller parks here so later callers arrive while it is still in flight.
                await withCheckedContinuation { continuation in
                    gate = continuation
                }
            }
            if let shouldFail { throw shouldFail }
            issued += 1
            return TokenProvider.TokenPair(
                accessToken: "access-\(issued)",
                refreshToken: "refresh-\(issued)",
                expiresAt: Date(timeIntervalSince1970: 3_000_000)
            )
        }
    }

    /// A store that records without touching the Keychain, which is unavailable in tests.
    private final class StubStore: @unchecked Sendable {
        var sealed: [String] = []
        var cleared = 0
    }

    private var service: RecordingRefreshService!
    private let expired = Date(timeIntervalSince1970: 1_000)
    private let now = Date(timeIntervalSince1970: 2_000_000)

    override func setUp() async throws {
        try await super.setUp()
        service = RecordingRefreshService()
    }

    private func makeProvider() -> TokenProvider {
        let fixedNow = now
        return TokenProvider(
            service: service,
            secureStore: SecureTokenStore(service: "io.hrapp.tests.\(UUID().uuidString)"),
            clock: { fixedNow }
        )
    }

    /// Ten simultaneous callers, one refresh.
    ///
    /// Without the single-flight gate this is ten refreshes: one succeeds and nine present a token
    /// the server has already rotated, which it correctly reads as theft.
    func testConcurrentCallersTriggerExactlyOneRefresh() async throws {
        let provider = makeProvider()
        await provider.adopt(
            TokenProvider.TokenPair(accessToken: "stale", refreshToken: "r0", expiresAt: expired)
        )

        async let results: [String?] = withTaskGroup(of: String?.self) { group in
            for _ in 0..<10 {
                group.addTask { await provider.accessToken() }
            }
            var collected: [String?] = []
            for await value in group { collected.append(value) }
            return collected
        }

        // Let all ten arrive and queue behind the first, then let the refresh complete.
        try await Task.sleep(nanoseconds: 50_000_000)
        await service.release()

        let tokens = await results

        let calls = await service.callCount
        XCTAssertEqual(calls, 1, "each concurrent caller started its own refresh")
        XCTAssertEqual(Set(tokens.compactMap { $0 }).count, 1, "callers saw different tokens")
        XCTAssertEqual(tokens.compactMap { $0 }.count, 10, "some callers got no token")
    }

    /// A token still comfortably valid must not trigger a network call at all.
    func testValidTokenIsReturnedWithoutRefreshing() async throws {
        let provider = makeProvider()
        await provider.adopt(
            TokenProvider.TokenPair(
                accessToken: "good",
                refreshToken: "r0",
                expiresAt: now.addingTimeInterval(600)
            )
        )

        let token = await provider.accessToken()

        XCTAssertEqual(token, "good")
        let calls = await service.callCount
        XCTAssertEqual(calls, 0)
    }

    /// Refreshing a minute early covers request latency and modest clock skew. A token that is
    /// valid when checked but expired when it lands produces a 401 that looks like a bug.
    func testTokenWithinTheMarginIsRefreshed() async throws {
        let provider = makeProvider()
        await provider.adopt(
            TokenProvider.TokenPair(
                accessToken: "nearly-expired",
                refreshToken: "r0",
                expiresAt: now.addingTimeInterval(30)
            )
        )

        async let token = provider.accessToken()
        try await Task.sleep(nanoseconds: 20_000_000)
        await service.release()

        let resolved = await token
        XCTAssertEqual(resolved, "access-1")
    }

    /// No session at all is not a transport failure. Retrying it forever achieves nothing.
    func testNoSessionReturnsNilRatherThanRefreshing() async throws {
        let provider = makeProvider()

        let token = await provider.accessToken()

        XCTAssertNil(token)
        let calls = await service.callCount
        XCTAssertEqual(calls, 0)
    }

    /// The gate must reset. If the in-flight task were left in place after completing, every
    /// later refresh would return the first one's result — and the session would expire and never
    /// recover.
    func testASecondRefreshIsPossibleAfterTheFirstCompletes() async throws {
        let provider = makeProvider()
        await provider.adopt(
            TokenProvider.TokenPair(accessToken: "stale", refreshToken: "r0", expiresAt: expired)
        )

        async let first = provider.accessToken()
        try await Task.sleep(nanoseconds: 20_000_000)
        await service.release()
        _ = await first

        async let second = provider.refreshAfterUnauthorized()
        try await Task.sleep(nanoseconds: 20_000_000)
        await service.release()
        let secondToken = await second

        let calls = await service.callCount
        XCTAssertEqual(calls, 2, "the single-flight gate did not reset")
        XCTAssertEqual(secondToken, "access-2")
    }

    /// The server has already revoked the family; holding the local copy would make every
    /// subsequent request fail in a way the UI cannot explain.
    func testReuseDetectionClearsTheSession() async throws {
        let provider = makeProvider()
        await provider.adopt(
            TokenProvider.TokenPair(accessToken: "stale", refreshToken: "r0", expiresAt: expired)
        )
        await service.setFailure(
            APIError.http(status: 401, code: "TOKEN_REUSE_DETECTED", message: "Reuse detected")
        )

        async let token = provider.accessToken()
        try await Task.sleep(nanoseconds: 20_000_000)
        await service.release()
        let resolved = await token

        XCTAssertNil(resolved)
        let hasSession = await provider.hasSession
        XCTAssertFalse(hasSession, "the session survived a reuse report")
    }

    /// An ordinary refresh failure — the server was down — must not sign the user out. Their
    /// session is still valid; the network was not.
    func testATransportFailureDoesNotClearTheSession() async throws {
        let provider = makeProvider()
        await provider.adopt(
            TokenProvider.TokenPair(accessToken: "stale", refreshToken: "r0", expiresAt: expired)
        )
        await service.setFailure(APIError.transport(underlying: "offline"))

        async let token = provider.accessToken()
        try await Task.sleep(nanoseconds: 20_000_000)
        await service.release()
        _ = await token

        let hasSession = await provider.hasSession
        XCTAssertTrue(hasSession, "a network blip signed the user out")
    }
}
