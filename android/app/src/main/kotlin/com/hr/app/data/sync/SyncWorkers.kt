package com.hr.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains the outbox.
 *
 * Note the retry contract: this worker returns `Result.success()` even when entries failed,
 * because [Outbox.scheduleRetry] already owns the backoff schedule. Returning `Result.retry()`
 * would layer WorkManager's backoff on top of ours and produce a compound, unpredictable delay.
 * The worker's job is to run the drain; the outbox decides when each entry is next eligible.
 */
@HiltWorker
class OutboxWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val outbox: Outbox,
        private val sender: OutboxSender,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            // Anything left IN_FLIGHT was interrupted by a process death. Requeueing is safe
            // because every entry carries an idempotency key.
            val recovered = outbox.recoverStranded()
            if (recovered > 0) Log.i(TAG, "Recovered $recovered stranded outbox entries")

            var sent = 0
            while (true) {
                val batch = outbox.nextBatch()
                if (batch.isEmpty()) break

                batch.forEach { entry ->
                    outbox.markInFlight(entry)
                    when (val outcome = sender.send(entry)) {
                        is SendOutcome.Confirmed -> {
                            outbox.markConfirmed(entry)
                            sent++
                        }
                        is SendOutcome.Rejected -> outbox.markRejected(entry, outcome.code, outcome.message)
                        is SendOutcome.Retryable -> outbox.scheduleRetry(entry)
                        is SendOutcome.AuthenticationRequired -> {
                            // Pause the whole drain: every subsequent request would fail the
                            // same way, and hammering the auth endpoint helps nobody.
                            outbox.scheduleRetry(entry)
                            Log.w(TAG, "Outbox paused pending re-authentication")
                            return Result.success()
                        }
                    }
                }
            }

            Log.i(TAG, "Outbox drain complete: $sent confirmed")
            return Result.success()
        }

        companion object {
            const val TAG = "OutboxWorker"
            const val UNIQUE_NAME = "outbox-drain"
        }
    }

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val syncEngine: SyncEngine,
        private val scopes: SyncScopeProvider,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            var anyFailed = false
            scopes.subscribedScopes().forEach { scope ->
                when (val result = syncEngine.sync(scope)) {
                    is SyncResult.Success -> Log.d(TAG, "Synced '$scope': ${result.recordsApplied} records")
                    is SyncResult.Incomplete -> Log.w(TAG, "Sync of '$scope' incomplete")
                    is SyncResult.Failed -> {
                        Log.w(TAG, "Sync of '$scope' failed", result.cause)
                        anyFailed = true
                    }
                }
            }
            // Here WorkManager's backoff IS the right mechanism — unlike the outbox, sync has no
            // per-item schedule of its own.
            return if (anyFailed) Result.retry() else Result.success()
        }

        companion object {
            const val TAG = "SyncWorker"
            const val UNIQUE_PERIODIC = "sync-periodic"
            const val UNIQUE_IMMEDIATE = "sync-now"
        }
    }

/**
 * Schedules the background workers.
 *
 * Both schedulers are best-effort: doze, app standby buckets and battery optimisation can all
 * defer them, sometimes by hours. That is exactly why the app must be useful offline rather than
 * depending on timely background execution.
 */
@Singleton
class SyncScheduler
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) {
        /** Called after login and on every app start. */
        fun schedulePeriodicSync() {
            workManager.enqueueUniquePeriodicWork(
                SyncWorker.UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(networkRequired)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
        }

        /** Foreground, pull-to-refresh, or a push telling us something changed. */
        fun syncNow() {
            workManager.enqueueUniqueWork(
                SyncWorker.UNIQUE_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(networkRequired)
                    .build(),
            )
        }

        /**
         * Called immediately after every outbox enqueue.
         *
         * `APPEND_OR_REPLACE` rather than `REPLACE`: replacing a running drain would abandon it
         * mid-batch. Appending lets the current run finish and queues another behind it.
         */
        fun drainOutbox() {
            workManager.enqueueUniqueWork(
                OutboxWorker.UNIQUE_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<OutboxWorker>()
                    .setConstraints(networkRequired)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                    .build(),
            )
        }

        fun cancelAll() {
            workManager.cancelUniqueWork(SyncWorker.UNIQUE_PERIODIC)
            workManager.cancelUniqueWork(SyncWorker.UNIQUE_IMMEDIATE)
            workManager.cancelUniqueWork(OutboxWorker.UNIQUE_NAME)
        }

        private val networkRequired =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        private companion object {
            // WorkManager's floor for periodic work is 15 minutes; asking for less is silently
            // rounded up.
            const val PERIODIC_MINUTES = 15L
        }
    }

/** Sends one outbox entry. Implemented against the generated API client. */
interface OutboxSender {
    suspend fun send(entry: com.hr.app.data.local.OutboxEntry): SendOutcome
}

sealed interface SendOutcome {
    /** 2xx, or 409 ALREADY_DECIDED — someone else acted first, which is still a settled outcome. */
    data object Confirmed : SendOutcome

    /** Terminal 4xx. Will never succeed; the user's input is kept for editing. */
    data class Rejected(val code: String?, val message: String?) : SendOutcome

    /** 5xx or network. Retry with backoff. */
    data object Retryable : SendOutcome

    /** 401 that survived a token refresh. Pause the drain. */
    data object AuthenticationRequired : SendOutcome
}

/** Which scopes this user syncs. Derived from the permissions in `GET /v1/me`. */
fun interface SyncScopeProvider {
    suspend fun subscribedScopes(): List<String>
}
