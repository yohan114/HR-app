package com.hr.app.data.sync

import com.hr.app.data.local.OutboxDao
import com.hr.app.data.local.OutboxEntry
import com.hr.app.data.local.OutboxState
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/**
 * The write side of offline sync.
 *
 * Implements docs/sync-protocol.md §4. Callers enqueue a mutation and return immediately; the UI
 * has already been updated from the local write that accompanied it.
 */
@Singleton
class Outbox
    @Inject
    constructor(
        private val dao: OutboxDao,
        private val clock: Clock,
    ) {
        val pendingCount: Flow<Int> = dao.pendingCount()

        val problems: Flow<List<OutboxEntry>> = dao.problems()

        /**
         * Queues a mutation.
         *
         * The idempotency key is generated here, once. It must never be regenerated on retry —
         * that is precisely what makes unlimited retries safe, and regenerating it would turn a
         * lost response into a duplicate record.
         */
        suspend fun enqueue(
            aggregateType: String,
            aggregateId: String,
            httpMethod: String,
            path: String,
            payload: String,
        ): String {
            val id = UUID.randomUUID().toString()
            dao.insert(
                OutboxEntry(
                    id = id,
                    idempotencyKey = UUID.randomUUID().toString(),
                    aggregateType = aggregateType,
                    aggregateId = aggregateId,
                    httpMethod = httpMethod,
                    path = path,
                    payload = payload,
                    state = OutboxState.PENDING,
                    createdAt = clock.now(),
                ),
            )
            return id
        }

        suspend fun nextBatch(limit: Int = DRAIN_BATCH): List<OutboxEntry> = dao.nextBatch(clock.now(), limit)

        suspend fun markInFlight(entry: OutboxEntry) {
            dao.update(entry.copy(state = OutboxState.IN_FLIGHT, lastAttemptAt = clock.now()))
        }

        suspend fun markConfirmed(entry: OutboxEntry) {
            dao.delete(entry.id)
        }

        /**
         * Records a terminal business rejection.
         *
         * The payload is deliberately retained. Discarding what someone typed because the server
         * said no is hostile — the UI offers it back for editing (docs/sync-protocol.md §4.1).
         */
        suspend fun markRejected(
            entry: OutboxEntry,
            code: String?,
            message: String?,
        ) {
            dao.update(
                entry.copy(
                    state = OutboxState.REJECTED,
                    failureCode = code,
                    failureMessage = message,
                    lastAttemptAt = clock.now(),
                ),
            )
        }

        /**
         * Schedules a retry, or gives up if the entry has been trying for too long.
         *
         * Backoff is exponential **with jitter**. Jitter is not decoration: without it every
         * device in a company retries in lockstep after an outage, and the recovering server is
         * immediately knocked over again by its own users.
         */
        suspend fun scheduleRetry(entry: OutboxEntry) {
            val now = clock.now()
            val attempt = entry.attemptCount + 1

            if (now - entry.createdAt > RETRY_DEADLINE_MS) {
                dao.update(
                    entry.copy(
                        state = OutboxState.FAILED,
                        attemptCount = attempt,
                        lastAttemptAt = now,
                        failureCode = "RETRY_DEADLINE_EXCEEDED",
                        failureMessage = "Could not reach the server for 7 days",
                    ),
                )
                return
            }

            dao.update(
                entry.copy(
                    state = OutboxState.PENDING,
                    attemptCount = attempt,
                    lastAttemptAt = now,
                    nextAttemptAt = now + backoffMillis(attempt),
                ),
            )
        }

        /** Requeues entries stranded `IN_FLIGHT` by a process death. Safe: idempotency keys. */
        suspend fun recoverStranded(): Int = dao.requeueStranded()

        /** Called on sign-out. Queued mutations are discarded after the user is warned. */
        suspend fun clear() = dao.clear()

        internal fun backoffMillis(attempt: Int): Long {
            val exponential = BASE_BACKOFF_MS shl min(attempt - 1, MAX_SHIFT)
            val capped = min(exponential, MAX_BACKOFF_MS)
            // Full jitter: uniform in [0, capped]. Spreads a thundering herd far more effectively
            // than a small ± band around the target.
            return Random.nextLong(capped + 1)
        }

        private companion object {
            const val DRAIN_BATCH = 20
            const val BASE_BACKOFF_MS = 1_000L
            const val MAX_BACKOFF_MS = 5 * 60 * 1_000L
            const val MAX_SHIFT = 20
            const val RETRY_DEADLINE_MS = 7L * 24 * 60 * 60 * 1_000
        }
    }

/** Injected so tests can control time rather than sleeping. */
fun interface Clock {
    fun now(): Long
}
