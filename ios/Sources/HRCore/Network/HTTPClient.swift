import Foundation
import OSLog

/// The iOS HTTP layer.
///
/// Deliberately thin. It does four things the generated `HRClient` does not: attaches the bearer
/// token, refreshes once on a 401 and retries, maps the error envelope onto ``APIError``, and
/// carries the idempotency key for replayable writes.
///
/// It does **not** know about any endpoint. Typed calls go through the generated client; the
/// outbox goes through ``request(method:path:body:idempotencyKey:)`` because an outbox entry
/// already carries a method, a path and a serialised body — routing it through a typed method
/// would mean a switch over every mutation in the product, which grows with every feature and buys
/// nothing. Same reasoning as `OutboxHttpSender` on Android.
public actor HTTPClient {
    private let configuration: APIConfiguration
    private let session: URLSession
    private let tokens: TokenProvider?
    private let log = Logger(subsystem: "io.hrapp", category: "HTTPClient")

    public init(
        configuration: APIConfiguration,
        tokens: TokenProvider? = nil,
        session: URLSession? = nil
    ) {
        self.configuration = configuration
        self.tokens = tokens

        if let session {
            self.session = session
        } else {
            let config = URLSessionConfiguration.default
            config.timeoutIntervalForRequest = configuration.timeout
            // The app is offline-first: a request that cannot go out now is the outbox's problem,
            // not URLSession's. Waiting for connectivity here would hide failures from the retry
            // schedule that is designed to handle them.
            config.waitsForConnectivity = false
            config.requestCachePolicy = .reloadIgnoringLocalCacheData
            self.session = URLSession(configuration: config)
        }
    }

    public struct Response: Sendable {
        public let status: Int
        public let body: Data

        public var isSuccess: Bool { (200..<300).contains(status) }
    }

    /// Performs a request, refreshing the token once if the server rejects it.
    ///
    /// - Parameter authenticated: when false, no bearer token is attached and `X-Tenant-Code` is
    ///   sent instead. Used for sign-in and tenant resolution, which have no token yet.
    public func request(
        method: String,
        path: String,
        body: Data? = nil,
        idempotencyKey: String? = nil,
        authenticated: Bool = true
    ) async -> Result<Response, APIError> {
        guard let url = configuration.url(forPath: path) else {
            return .failure(.malformedResponse("Could not build a URL for path '\(path)'"))
        }

        var token: String?
        if authenticated {
            // Spelled out rather than `await tokens?.accessToken()`: optional-chaining a method
            // that already returns `String?` yields `String??`, and a single `guard let` would
            // unwrap only the outer layer — quietly treating "no token" as success and sending an
            // unauthenticated request the server answers with 401.
            guard let provider = tokens, let available = await provider.accessToken() else {
                return .failure(.unauthenticated)
            }
            token = available
        }

        let first = await send(
            url: url, method: method, body: body,
            idempotencyKey: idempotencyKey, token: token
        )

        // Retry exactly once, and only for a 401. A 403 means the caller is authenticated and not
        // permitted, which refreshing cannot change — retrying it would hammer the auth endpoint
        // for a request that is never going to succeed.
        guard authenticated,
              case .success(let response) = first,
              response.status == 401,
              let provider = tokens,
              let refreshed = await provider.refreshAfterUnauthorized()
        else {
            return first
        }

        log.debug("Retrying \(method) \(path) after token refresh")
        return await send(
            url: url, method: method, body: body,
            idempotencyKey: idempotencyKey, token: refreshed
        )
    }

    /// Decodes a successful response, or maps the failure.
    public func requestDecoding<T: Decodable>(
        _ type: T.Type,
        method: String,
        path: String,
        body: Data? = nil,
        authenticated: Bool = true
    ) async -> Result<T, APIError> {
        let outcome = await request(method: method, path: path, body: body, authenticated: authenticated)

        switch outcome {
        case .failure(let error):
            return .failure(error)
        case .success(let response) where response.isSuccess:
            do {
                return .success(try Self.decoder.decode(T.self, from: response.body))
            } catch {
                return .failure(.malformedResponse(String(describing: error)))
            }
        case .success(let response):
            let envelope = APIErrorEnvelope.decode(from: response.body)
            return .failure(.http(status: response.status, code: envelope?.code, message: envelope?.message))
        }
    }

    // ------------------------------------------------------------------------

    private func send(
        url: URL,
        method: String,
        body: Data?,
        idempotencyKey: String?,
        token: String?
    ) async -> Result<Response, APIError> {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if body != nil {
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        }
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else if let tenantCode = configuration.tenantCode {
            // Only on unauthenticated requests. Sending both a token and a tenant code is
            // rejected with TENANT_MISMATCH.
            request.setValue(tenantCode, forHTTPHeaderField: "X-Tenant-Code")
        }
        if let idempotencyKey {
            request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        }

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                return .failure(.malformedResponse("Response was not HTTP"))
            }
            return .success(Response(status: http.statusCode, body: data))
        } catch let error as URLError where error.code == .cancelled {
            // A cancelled request is a caller decision — a background task expiring, a screen
            // being dismissed. Reporting it as a transport failure would schedule a retry for
            // work nobody wants any more.
            return .failure(.transport(underlying: "cancelled"))
        } catch {
            return .failure(.transport(underlying: error.localizedDescription))
        }
    }

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}
