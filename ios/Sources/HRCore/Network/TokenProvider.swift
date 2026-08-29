import Foundation
import OSLog

/// Holds the current session and refreshes it exactly once at a time.
///
/// ## Why single-flight is a correctness requirement, not an optimisation
///
/// The server rotates refresh tokens on every exchange and treats a **reused** token as theft: it
/// revokes the entire token family and every device sharing that login has to sign in again
/// (`AuthenticationService`, RFC 9700 §4.14.2).
///
/// So consider the obvious implementation. Five requests are in flight when the access token
/// expires. All five get a 401. All five call refresh with the same refresh token. The first
/// rotates it; the other four present a token the server has just marked as used — and the server,
/// correctly, concludes the token has been stolen and revokes everything.
///
/// The user is signed out of all their devices because their phone loaded a screen with five
/// widgets on it. Nothing in the server is wrong; the client simply must not do that. This actor
/// is what prevents it: concurrent callers await the *same* refresh task rather than starting
/// their own.
///
/// ## Why the refresh token lives in memory
///
/// ``SecureTokenStore`` seals it behind Face ID. Unsealing prompts the user. Prompting on every
/// token refresh — roughly every fifteen minutes of active use — would be intolerable, and worse,
/// it is exactly the "biometrics that do not actually save you anything" failure the product is
/// built to beat.
///
/// So the sealed copy is for *cold start*: unseal once, hold the token in memory for the session,
/// reseal each rotation. Memory is cleared on sign-out and when the process dies.
///
/// ## The consequence for background work
///
/// A background launch after the process was killed has no in-memory session and cannot prompt for
/// biometrics — there is no UI to prompt in. ``accessToken()`` therefore returns `nil`, callers
/// get ``APIError/unauthenticated``, and background sync defers until the user next opens the app.
/// That is a real limitation and it is the correct trade: the alternative is storing the refresh
/// token unprotected so background tasks can use it, which discards the entire point of sealing it.
public actor TokenProvider {
    public struct TokenPair: Sendable, Equatable {
        public let accessToken: String
        public let refreshToken: String
        public let expiresAt: Date

        public init(accessToken: String, refreshToken: String, expiresAt: Date) {
            self.accessToken = accessToken
            self.refreshToken = refreshToken
            self.expiresAt = expiresAt
        }
    }

    private let service: any TokenRefreshService
    private let secureStore: SecureTokenStore
    private let clock: @Sendable () -> Date
    private let log = Logger(subsystem: "io.hrapp", category: "TokenProvider")

    private var current: TokenPair?

    /// The in-progress refresh, if any. This single field is the whole mechanism.
    private var refreshTask: Task<TokenPair, Error>?

    public init(
        service: any TokenRefreshService,
        secureStore: SecureTokenStore,
        clock: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.service = service
        self.secureStore = secureStore
        self.clock = clock
    }

    /// Adopts a freshly issued pair, e.g. after a password sign-in.
    public func adopt(_ pair: TokenPair) async {
        current = pair
        try? await secureStore.seal(refreshToken: pair.refreshToken)
    }

    /// Restores a session from the sealed refresh token, prompting for biometrics once.
    ///
    /// Returns `false` when there is nothing to restore or the user declined — both of which mean
    /// "show the password form", not "something went wrong".
    public func restoreFromSealedToken(reason: String) async -> Bool {
        switch await secureStore.unseal(reason: reason) {
        case .success(let refreshToken):
            do {
                let pair = try await service.refresh(refreshToken: refreshToken)
                await adopt(pair)
                return true
            } catch {
                log.warning("Sealed token was rejected: \(String(describing: error))")
                return false
            }
        case .noSealedToken, .cancelled:
            return false
        case .biometryChanged:
            log.info("Sealed token invalidated by a biometric change")
            await secureStore.clear()
            return false
        case .failed(let reason):
            log.error("Could not unseal refresh token: \(reason)")
            return false
        }
    }

    /// A usable access token, refreshing first if it is expired or nearly so.
    ///
    /// Returns `nil` when there is no session at all. Callers translate that to
    /// ``APIError/unauthenticated`` rather than treating it as a transport failure — retrying an
    /// unauthenticated request forever achieves nothing and drains the battery.
    public func accessToken() async -> String? {
        guard let pair = current else { return nil }
        if clock().addingTimeInterval(Self.refreshMargin) < pair.expiresAt {
            return pair.accessToken
        }
        return try? await performRefresh().accessToken
    }

    /// Forces a refresh after a 401, and returns the new access token.
    ///
    /// Called by ``HTTPClient`` when a request is rejected despite a token that looked valid — the
    /// server may have revoked it, or the device clock may be wrong.
    public func refreshAfterUnauthorized() async -> String? {
        try? await performRefresh().accessToken
    }

    /// Drops the session. Called on sign-out and on `TOKEN_REUSE_DETECTED`.
    public func clear() async {
        current = nil
        refreshTask?.cancel()
        refreshTask = nil
        await secureStore.clear()
    }

    public var hasSession: Bool { current != nil }

    // ------------------------------------------------------------------------

    /// The single-flight gate.
    ///
    /// Because this is an actor, only one caller is inside this method at a time. The first
    /// creates the task; everyone arriving while it runs awaits the same one. Awaiting a `Task`
    /// suspends without holding the actor, so the rest of the app is not blocked.
    private func performRefresh() async throws -> TokenPair {
        if let existing = refreshTask {
            return try await existing.value
        }

        guard let refreshToken = current?.refreshToken else {
            throw APIError.unauthenticated
        }

        let task = Task<TokenPair, Error> { [service] in
            try await service.refresh(refreshToken: refreshToken)
        }
        refreshTask = task

        defer { refreshTask = nil }

        do {
            let pair = try await task.value
            current = pair
            // Reseal on every rotation. Skipping this leaves the Keychain holding a token the
            // server has already invalidated — so the next cold start would present a used token
            // and trigger the family revocation this whole design exists to avoid.
            try? await secureStore.seal(refreshToken: pair.refreshToken)
            return pair
        } catch {
            if case APIError.http(_, let code, _) = error, code == "TOKEN_REUSE_DETECTED" {
                // The family is already gone server-side. Holding the local copy would make every
                // subsequent request fail in a way the UI cannot explain.
                log.error("Refresh token reuse reported by the server; clearing the session")
                current = nil
                await secureStore.clear()
            }
            throw error
        }
    }

    /// Refresh this long before expiry rather than exactly at it.
    ///
    /// Covers request latency and modest device clock skew. Without a margin, a token that is
    /// valid when checked can be expired by the time it reaches the server, producing a 401 that
    /// looks like a bug and costs an extra round trip.
    private static let refreshMargin: TimeInterval = 60
}

/// Exchanges a refresh token for a new pair. The seam onto `POST /v1/auth/token/refresh`.
///
/// Declared at file scope because Swift does not permit a protocol nested inside a type. Kept in
/// this file regardless, since it exists only to be implemented against ``TokenProvider``.
public protocol TokenRefreshService: Sendable {
    func refresh(refreshToken: String) async throws -> TokenProvider.TokenPair
}
