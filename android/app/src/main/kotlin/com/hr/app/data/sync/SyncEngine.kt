package com.hr.app.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.hr.app.data.local.HrDatabase
import com.hr.app.data.local.SyncCursor
import com.hr.app.data.local.SyncCursorDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The read side of offline sync.
 *
 * Implements docs/sync-protocol.md §3. Pulls changes by opaque cursor and applies them to the
 * local store, which is what the UI observes.
 *
 * **The server endpoint does not exist yet** (P0-BE-33 — it is deliberately absent from the
 * OpenAPI spec until implemented, so the generated clients do not expose a method that 404s).
 * The engine is built now because the protocol, the persistence and the failure handling are the
 * parts that take time to get right; wiring it to the endpoint is the small remainder.
 * [SyncApi] is the seam.
 */
@Singleton
class SyncEngine
    @Inject
    constructor(
        private val database: HrDatabase,
        private val cursorDao: SyncCursorDao,
        private val api: SyncApi,
        private val appliers: Map<String, @JvmSuppressWildcards ChangeApplier>,
        private val clock: Clock,
    ) {
        /**
         * Syncs one scope to completion.
         *
         * Loops until the server reports no more pages. Each page is applied in a single
         * transaction **together with its cursor** — see [applyPage] for why that matters.
         */
        suspend fun sync(scope: String): SyncResult {
            var pages = 0
            var applied = 0

            try {
                while (true) {
                    val since = cursorDao.forScope(scope)?.cursor
                    val page = api.fetch(scope = scope, since = since)

                    applyPage(scope, page)
                    pages++
                    applied += page.changes.size + page.deletes.size

                    if (!page.hasMore) break
                    if (pages >= MAX_PAGES_PER_RUN) {
                        // Defensive: a server bug that always reports hasMore would otherwise
                        // spin here forever, draining the battery with nobody noticing.
                        Log.w(TAG, "Stopping sync of '$scope' after $MAX_PAGES_PER_RUN pages")
                        return SyncResult.Incomplete(applied)
                    }
                }
                return SyncResult.Success(applied)
            } catch (e: SyncCursorExpiredException) {
                // Expected for a device offline longer than the change-feed retention. Not an
                // error to show the user beyond a progress indicator.
                Log.i(TAG, "Cursor for '$scope' expired; performing a full resync")
                resetScope(scope)
                return runCatching { sync(scope) }.getOrElse { SyncResult.Failed(it) }
            } catch (e: Exception) {
                cursorDao.forScope(scope)?.let {
                    cursorDao.upsert(it.copy(lastAttemptAt = clock.now(), lastError = e.message))
                }
                return SyncResult.Failed(e)
            }
        }

        /**
         * Applies one page of changes.
         *
         * The cursor is persisted **inside the same transaction** as the data. Writing them
         * separately means a crash between the two either loses changes (cursor advanced, data
         * not written — silent and unrecoverable) or reprocesses them. The first is the reason
         * this is a transaction and not two statements.
         */
        private suspend fun applyPage(
            scope: String,
            page: SyncPage,
        ) {
            database.withTransaction {
                page.changes.forEach { change ->
                    val applier = appliers[change.entityType]
                    if (applier == null) {
                        // An entity type we do not know about. Skipping is correct: an older
                        // client must not fail because the server added something new.
                        Log.d(TAG, "No applier for '${change.entityType}'; skipping")
                        return@forEach
                    }
                    applier.upsert(change.entityId, change.payload)
                }

                page.deletes.forEach { delete ->
                    appliers[delete.entityType]?.delete(delete.entityId)
                }

                cursorDao.upsert(
                    SyncCursor(
                        scope = scope,
                        cursor = page.cursor,
                        lastSyncedAt = clock.now(),
                        lastAttemptAt = clock.now(),
                        lastError = null,
                    ),
                )
            }
        }

        /** Discards local state for a scope so the next sync starts from scratch. */
        suspend fun resetScope(scope: String) {
            database.withTransaction {
                appliers.values.forEach { it.clear(scope) }
                cursorDao.reset(scope)
            }
        }

        suspend fun clearAll() {
            database.withTransaction {
                appliers.values.forEach { it.clearAll() }
                cursorDao.clear()
            }
        }

        private companion object {
            const val TAG = "SyncEngine"
            const val MAX_PAGES_PER_RUN = 200
        }
    }

/**
 * Applies changes for one entity type.
 *
 * Each syncable entity registers one of these. The conflict strategy for the entity type
 * (docs/sync-protocol.md §5) is implemented here — there is deliberately no generic
 * last-write-wins, because LWW is wrong for every entity type in this product.
 */
interface ChangeApplier {
    suspend fun upsert(
        entityId: String,
        payload: String,
    )

    suspend fun delete(entityId: String)

    suspend fun clear(scope: String)

    suspend fun clearAll()
}

/** The seam onto `GET /v1/sync`. Implemented against the generated client once P0-BE-33 lands. */
interface SyncApi {
    suspend fun fetch(
        scope: String,
        since: String?,
        limit: Int = 500,
    ): SyncPage
}

data class SyncPage(
    val changes: List<SyncChange>,
    val deletes: List<SyncDeletion>,
    val cursor: String,
    val hasMore: Boolean,
)

data class SyncChange(
    val entityType: String,
    val entityId: String,
    val payload: String,
)

data class SyncDeletion(
    val entityType: String,
    val entityId: String,
)

/** Signals HTTP 410 `SYNC_CURSOR_EXPIRED`: discard local state and resync from scratch. */
class SyncCursorExpiredException(scope: String) : Exception("Sync cursor for '$scope' has expired")

sealed interface SyncResult {
    data class Success(val recordsApplied: Int) : SyncResult

    data class Incomplete(val recordsApplied: Int) : SyncResult

    data class Failed(val cause: Throwable) : SyncResult
}
