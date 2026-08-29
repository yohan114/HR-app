import Foundation
import OSLog

/// Drains the outbox until it is empty or the caller asks it to stop.
///
/// The counterpart to `OutboxWorker.doWork()` on Android. It is a plain async function rather than
/// a framework type because iOS has no WorkManager equivalent that owns the work — `BGTaskScheduler`
/// hands you a task and a deadline, and what runs inside it is yours.
///
/// **The retry contract matches Android's, deliberately.** This never reports failure upwards for
/// an entry that simply needs retrying, because ``Outbox/scheduleRetry(_:)`` already owns the
/// backoff schedule. Letting the caller re-run us on failure would layer a second schedule on top
/// of ours and produce a compound, unpredictable delay.
public struct OutboxDrain: Sendable {
    private let outbox: Outbox
    private let sender: any OutboxSender
    private let log = Logger(subsystem: "io.hrapp", category: "OutboxDrain")

    public init(outbox: Outbox, sender: any OutboxSender) {
        self.outbox = outbox
        self.sender = sender
    }

    public struct Summary: Sendable, Equatable {
        public var confirmed = 0
        public var rejected = 0
        public var retrying = 0
        public var recovered = 0
        /// True when the drain stopped early — expired deadline, cancellation, or a 401.
        public var stoppedEarly = false
    }

    /// Sends everything currently due.
    ///
    /// - Parameter isCancelled: consulted between entries. `BGTaskScheduler` gives roughly thirty
    ///   seconds and then kills the app if it has not returned — and an app killed that way is
    ///   penalised in future scheduling, so a task that overruns makes the *next* one less likely
    ///   to be granted. Checking between entries rather than mid-request keeps every send atomic:
    ///   a request abandoned in flight may still have been applied server-side, which is safe only
    ///   because of the idempotency key but is still wasted work.
    @discardableResult
    public func drain(isCancelled: @escaping @Sendable () -> Bool = { false }) async -> Summary {
        var summary = Summary()

        // Anything left in-flight was interrupted by a process death. Requeueing is safe precisely
        // because every entry carries an idempotency key.
        summary.recovered = (try? await outbox.recoverStranded()) ?? 0
        if summary.recovered > 0 {
            log.info("Recovered \(summary.recovered) stranded outbox entries")
        }

        while !isCancelled() {
            guard let batch = try? await outbox.nextBatch(), !batch.isEmpty else { break }

            for entry in batch {
                if isCancelled() {
                    summary.stoppedEarly = true
                    return summary
                }

                try? await outbox.markInFlight(entry)

                switch await sender.send(entry) {
                case .confirmed:
                    try? await outbox.markConfirmed(entry)
                    summary.confirmed += 1

                case .rejected(let code, let message):
                    try? await outbox.markRejected(entry, code: code, message: message)
                    summary.rejected += 1

                case .retryable:
                    try? await outbox.scheduleRetry(entry)
                    summary.retrying += 1

                case .authenticationRequired:
                    // Pause the whole drain. Every subsequent request would fail the same way, and
                    // hammering the auth endpoint helps nobody — least of all a user whose session
                    // has genuinely expired.
                    try? await outbox.scheduleRetry(entry)
                    summary.retrying += 1
                    summary.stoppedEarly = true
                    log.warning("Outbox paused pending re-authentication")
                    return summary
                }
            }
        }

        if isCancelled() { summary.stoppedEarly = true }
        log.info(
            "Outbox drain finished: \(summary.confirmed) confirmed, \(summary.rejected) rejected, \(summary.retrying) retrying"
        )
        return summary
    }
}

/// Syncs every subscribed scope.
///
/// The read-side counterpart to ``OutboxDrain``, matching `SyncWorker` on Android.
public struct SyncRun: Sendable {
    private let engine: SyncEngine
    private let scopes: @Sendable () async -> [String]
    private let log = Logger(subsystem: "io.hrapp", category: "SyncRun")

    public init(engine: SyncEngine, scopes: @escaping @Sendable () async -> [String]) {
        self.engine = engine
        self.scopes = scopes
    }

    /// - Returns: false if any scope failed, so the caller can report it to `BGTaskScheduler`.
    ///   Unlike the outbox, sync has no per-item schedule of its own, so here the system's own
    ///   retry is the right mechanism.
    @discardableResult
    public func run(isCancelled: @escaping @Sendable () -> Bool = { false }) async -> Bool {
        var allSucceeded = true

        for scope in await scopes() {
            if isCancelled() { return false }

            switch await engine.sync(scope: scope) {
            case .success(let applied):
                log.debug("Synced '\(scope)': \(applied) records")
            case .incomplete(let applied):
                log.warning("Sync of '\(scope)' incomplete after \(applied) records")
                allSucceeded = false
            case .failed(let error):
                log.warning("Sync of '\(scope)' failed: \(String(describing: error))")
                allSucceeded = false
            }
        }

        return allSucceeded
    }
}
