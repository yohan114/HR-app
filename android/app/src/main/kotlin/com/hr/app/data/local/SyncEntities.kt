package com.hr.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A queued user mutation awaiting confirmation from the server.
 *
 * See docs/sync-protocol.md §4. The contract that makes indefinite retry safe is
 * [idempotencyKey]: generated once when the row is created, never regenerated on retry, and sent
 * as the `Idempotency-Key` header. The server records it and returns the original outcome for a
 * replay, so a response lost on the return path cannot produce a duplicate leave application or a
 * double attendance punch.
 */
@Entity(
    tableName = "outbox",
    indices = [
        // Drain order: oldest first, within an aggregate.
        Index(value = ["aggregateType", "aggregateId", "createdAt"]),
        Index(value = ["state"]),
        Index(value = ["idempotencyKey"], unique = true),
    ],
)
data class OutboxEntry(
    @PrimaryKey val id: String,
    /** UUIDv7. Generated once, at creation. Never regenerated. */
    val idempotencyKey: String,
    /**
     * Entries are drained in order within an aggregate and concurrently across aggregates.
     * "Apply for leave" then "cancel it" must not be reordered; one employee's leave and
     * another's attendance need not be serialised.
     */
    val aggregateType: String,
    val aggregateId: String,
    val httpMethod: String,
    val path: String,
    /** Serialised JSON request body. May contain personal data — the store is encrypted. */
    val payload: String,
    val state: OutboxState,
    val attemptCount: Int = 0,
    val createdAt: Long,
    val lastAttemptAt: Long? = null,
    /** Earliest time the next attempt may run. Drives exponential backoff with jitter. */
    val nextAttemptAt: Long = 0,
    /** Machine-readable error code from the server on terminal rejection. */
    val failureCode: String? = null,
    val failureMessage: String? = null,
)

enum class OutboxState {
    /** Written locally, not yet accepted by the server. The UI shows a queued badge. */
    PENDING,

    /** Currently being sent. Guards against two workers draining the same entry. */
    IN_FLIGHT,

    /** Server rejected it on business grounds. Terminal; the user's input is kept for editing. */
    REJECTED,

    /** Retried past the deadline. Terminal until the user retries manually. */
    FAILED,
}

/**
 * The sync position for one scope.
 *
 * The cursor is opaque and **must not** be parsed, compared or sorted — see
 * docs/sync-protocol.md §3.4. It encodes a monotonic commit sequence rather than a timestamp,
 * because a timestamp-based cursor silently loses rows committed inside long transactions.
 */
@Entity(tableName = "sync_cursor")
data class SyncCursor(
    @PrimaryKey val scope: String,
    val cursor: String?,
    val lastSyncedAt: Long?,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
)

/**
 * Local sync state for a business record.
 *
 * Kept alongside each syncable entity so the UI can distinguish a confirmed row from one still
 * queued or rejected. See the state machine in docs/sync-protocol.md §6.
 */
enum class SyncState {
    PENDING,
    CONFIRMED,
    REJECTED,
    FAILED,
}
