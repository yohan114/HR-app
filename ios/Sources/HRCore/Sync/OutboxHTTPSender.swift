import Foundation
import OSLog

/// Sends outbox entries over HTTP.
///
/// The status mapping is the whole substance of this type, and it follows
/// docs/sync-protocol.md §4.4. It is kept deliberately identical to `OutboxHttpSender` on Android:
/// the two platforms implement one written protocol, and a divergence here would show up as
/// "leave requests silently vanish on iPhones" long after the change that caused it.
public struct OutboxHTTPSender: OutboxSender {
    private let client: HTTPClient
    private let log = Logger(subsystem: "io.hrapp", category: "OutboxSender")

    public init(client: HTTPClient) {
        self.client = client
    }

    public func send(_ entry: OutboxEntry) async -> SendOutcome {
        let outcome = await client.request(
            method: entry.httpMethod,
            path: entry.path,
            body: Data(entry.payload.utf8),
            // `idempotencyKey`, not `id`. They are distinct fields on an entry, and using the row
            // id here would still be *a* stable key — but a different one from Android's, so the
            // same logical retry would look like a new request to the server. The contract only
            // works if both clients send the field the protocol names.
            //
            // Generated once when the entry was enqueued and never regenerated here: regenerating
            // on retry is precisely what turns a lost response into a duplicate record.
            idempotencyKey: entry.idempotencyKey
        )

        switch outcome {
        case .failure(.unauthenticated):
            return .authenticationRequired

        case .failure(let error) where error.isRetryable:
            log.debug("Retryable failure sending \(entry.id): \(String(describing: error))")
            return .retryable

        case .failure(let error):
            // A malformed entry would fail identically forever, so retrying is pointless.
            log.warning("Unrecoverable failure sending \(entry.id): \(String(describing: error))")
            return .rejected(code: "CLIENT_ERROR", message: String(describing: error))

        case .success(let response):
            return classify(response, entry: entry)
        }
    }

    /// Internal rather than private so the mapping can be tested directly.
    ///
    /// It is the substance of this type and every branch matters, but reaching all of them through
    /// a real ``HTTPClient`` would mean stubbing `URLSession` to return eight different status
    /// codes — machinery that tests the stub more than the mapping.
    func classify(_ response: HTTPClient.Response, entry: OutboxEntry) -> SendOutcome {
        if response.isSuccess { return .confirmed }

        let envelope = APIErrorEnvelope.decode(from: response.body)

        switch response.status {
        // Someone else acted first. That is a settled outcome, not a failure — retrying would
        // never change it, and surfacing it as an error would be misleading to the user.
        case 409 where envelope?.code == "ALREADY_DECIDED":
            return .confirmed

        case 401:
            return .authenticationRequired

        case 429, 500...:
            return .retryable

        case 400...499:
            return .rejected(code: envelope?.code, message: envelope?.message)

        default:
            return .retryable
        }
    }
}
