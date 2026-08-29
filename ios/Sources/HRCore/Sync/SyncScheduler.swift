#if canImport(BackgroundTasks)
import BackgroundTasks
#endif
import Foundation
import OSLog

/// Schedules background sync and outbox drains.
///
/// The counterpart to `SyncScheduler` on Android, and the place where the two platforms differ
/// most — because `BGTaskScheduler` and `WorkManager` make genuinely different promises.
///
/// ## What iOS actually guarantees
///
/// Nothing. `BGAppRefreshTask` is granted on a schedule iOS decides from how often the user opens
/// the app, battery state, Low Power Mode and time of day. A rarely-used app may get a window once
/// a day; an app the user has force-quit gets none at all until they launch it again. There is no
/// setting that changes this and no entitlement that buys more.
///
/// This is not a limitation to design around — it is the reason the app is offline-first in the
/// first place (ADR 0003). Background execution is an optimisation that makes the app feel fresh
/// when it works. Every screen must be correct and complete without it.
///
/// ## Registration must happen before launch returns
///
/// `register(forTaskWithIdentifier:)` has to be called during
/// `application(_:didFinishLaunchingWithOptions:)`, before it returns. Registering later throws.
/// That is why ``registerHandlers()`` is separate from ``schedule()``: the app delegate calls the
/// first synchronously at launch, and the second whenever there is reason to.
///
/// Both identifiers must also appear in `Info.plist` under `BGTaskSchedulerPermittedIdentifiers`,
/// or registration fails at runtime with a message that does not say so.
public actor SyncScheduler {
    /// Read-side sync. Short, frequent, network-dependent.
    public static let syncTaskIdentifier = "io.hrapp.sync.refresh"

    /// Outbox drain. Longer, and worth requesting even on battery, because a queued leave request
    /// the user believes they submitted is the worst thing this app can get wrong.
    public static let outboxTaskIdentifier = "io.hrapp.sync.outbox"

    private let drain: OutboxDrain
    private let syncRun: SyncRun
    private let log = Logger(subsystem: "io.hrapp", category: "SyncScheduler")

    /// Guards against a foreground sync and a background sync overlapping.
    ///
    /// Two concurrent runs of the same scope would each fetch from the same cursor and apply the
    /// same page — harmless, because appliers are upserts, but it doubles the work and the
    /// battery cost at exactly the moment iOS is measuring whether to grant us another window.
    private var inFlight: Task<Void, Never>?

    public init(drain: OutboxDrain, syncRun: SyncRun) {
        self.drain = drain
        self.syncRun = syncRun
    }

    // ------------------------------------------------------------------------
    // Foreground triggers
    // ------------------------------------------------------------------------

    /// Foreground, pull-to-refresh, or a push telling us something changed.
    ///
    /// Coalescing rather than queueing: if a sync is already running, the caller joins it instead
    /// of starting a second. A user who pulls to refresh three times gets one sync, not three.
    public func syncNow() async {
        if let existing = inFlight {
            await existing.value
            return
        }

        let task = Task { [syncRun] in
            _ = await syncRun.run()
        }
        inFlight = task
        await task.value
        inFlight = nil
    }

    /// Called immediately after every outbox enqueue, and on returning to foreground.
    public func drainOutboxNow() async {
        _ = await drain.drain()
    }

    // ------------------------------------------------------------------------
    // Background tasks
    // ------------------------------------------------------------------------

    #if canImport(BackgroundTasks) && os(iOS)

    /// Registers the handlers. **Must** be called from `didFinishLaunchingWithOptions`.
    public nonisolated func registerHandlers() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.syncTaskIdentifier,
            using: nil
        ) { task in
            Task { await self.handleSync(task) }
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.outboxTaskIdentifier,
            using: nil
        ) { task in
            Task { await self.handleOutbox(task) }
        }
    }

    /// Asks iOS for future windows. Safe to call repeatedly; submitting replaces any pending
    /// request with the same identifier.
    public nonisolated func schedule() {
        let refresh = BGAppRefreshTaskRequest(identifier: Self.syncTaskIdentifier)
        // A floor, not a promise. iOS treats it as "no earlier than" and routinely waits far
        // longer. Fifteen minutes mirrors WorkManager's periodic floor on Android so the two
        // platforms at least ask for the same thing.
        refresh.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)

        let outbox = BGProcessingTaskRequest(identifier: Self.outboxTaskIdentifier)
        outbox.requiresNetworkConnectivity = true
        // Deliberately false. Requiring external power would mean a leave request submitted on the
        // commute sits unsent until the user gets home and plugs in — and the whole promise of the
        // outbox is that submitting works whether or not the network did.
        outbox.requiresExternalPower = false
        outbox.earliestBeginDate = Date(timeIntervalSinceNow: 60)

        do {
            try BGTaskScheduler.shared.submit(refresh)
            try BGTaskScheduler.shared.submit(outbox)
        } catch {
            // Expected on the simulator, which has no BGTaskScheduler, and when the app is in a
            // state iOS will not schedule for. Not worth surfacing to the user: the foreground
            // triggers cover the same work.
            log.debug("Could not submit background task: \(String(describing: error))")
        }
    }

    private func handleSync(_ task: BGTask) async {
        // Request the next window first. Doing it at the end means an early return or a crash
        // silently ends all future background sync, and nothing would ever report that.
        schedule()

        let cancelled = CancellationFlag()
        task.expirationHandler = { cancelled.set() }

        let succeeded = await syncRun.run(isCancelled: { cancelled.isSet })
        task.setTaskCompleted(success: succeeded && !cancelled.isSet)
    }

    private func handleOutbox(_ task: BGTask) async {
        schedule()

        let cancelled = CancellationFlag()
        task.expirationHandler = { cancelled.set() }

        let summary = await drain.drain(isCancelled: { cancelled.isSet })
        task.setTaskCompleted(success: !summary.stoppedEarly)
    }

    #else

    /// No-ops off iOS so `swift test` runs on macOS and in CI without an iOS toolchain.
    public nonisolated func registerHandlers() {}
    public nonisolated func schedule() {}

    #endif
}

/// A thread-safe boolean for the expiration handler.
///
/// `BGTask.expirationHandler` fires on an arbitrary queue while the work is running, so this is
/// read and written concurrently by definition. A plain `var` captured in both closures would be a
/// data race — and one that would almost never reproduce in testing, because the handler only
/// fires when a task overruns.
final class CancellationFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var value = false

    var isSet: Bool {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func set() {
        lock.lock()
        value = true
        lock.unlock()
    }
}
