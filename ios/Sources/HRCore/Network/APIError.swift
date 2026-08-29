import Foundation

/// The server's error envelope.
///
/// Every non-2xx response uses this one shape (docs/03-architecture.md §9). Clients localise from
/// the machine-readable `code`, never from `message` — the server cannot reliably know the
/// caller's locale, and the product ships in six languages.
public struct APIErrorEnvelope: Decodable, Sendable, Equatable {
    public let error: APIErrorBody

    public struct APIErrorBody: Decodable, Sendable, Equatable {
        public let code: String
        public let message: String?
        public let field: String?
    }
}

/// A failed request, classified by what the caller can do about it.
public enum APIError: Error, Sendable {
    /// No network, DNS failure, timeout. Always worth retrying.
    case transport(underlying: String)

    /// The response was not HTTP, or the body did not decode. Retrying will not help.
    case malformedResponse(String)

    /// A 4xx or 5xx carrying the standard envelope.
    case http(status: Int, code: String?, message: String?)

    /// No usable session. On iOS this is not always a sign-out — see ``TokenProvider``.
    case unauthenticated

    public var isRetryable: Bool {
        switch self {
        case .transport:
            return true
        case .http(let status, _, _):
            return status == 429 || status >= 500
        case .malformedResponse, .unauthenticated:
            return false
        }
    }

    /// `error.code` from the envelope, when the server sent one.
    public var serverCode: String? {
        if case .http(_, let code, _) = self { return code }
        return nil
    }
}

extension APIErrorEnvelope {
    /// Decodes an envelope, tolerating a body that is not one.
    ///
    /// A 502 from a load balancer is an HTML page, and a proxy timeout may be empty. Neither is a
    /// protocol violation worth surfacing as a decode failure — the status code already says
    /// everything the caller can act on.
    static func decode(from data: Data) -> APIErrorBody? {
        guard !data.isEmpty else { return nil }
        return try? JSONDecoder().decode(APIErrorEnvelope.self, from: data).error
    }
}
