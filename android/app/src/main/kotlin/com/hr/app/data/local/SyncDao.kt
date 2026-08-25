package com.hr.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: OutboxEntry)

    @Update
    suspend fun update(entry: OutboxEntry)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * The next entry to send for each aggregate.
     *
     * Selects the oldest `PENDING` entry per aggregate whose backoff has elapsed. Returning one
     * per aggregate is what gives ordering within an aggregate and concurrency across them
     * (docs/sync-protocol.md §4.2).
     */
    @Query(
        """
        SELECT * FROM outbox
        WHERE state = 'PENDING'
          AND nextAttemptAt <= :now
          AND id IN (
            SELECT id FROM outbox o2
            WHERE o2.state = 'PENDING'
            GROUP BY o2.aggregateType, o2.aggregateId
            HAVING o2.createdAt = MIN(o2.createdAt)
          )
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun nextBatch(
        now: Long,
        limit: Int = 20,
    ): List<OutboxEntry>

    @Query("SELECT COUNT(*) FROM outbox WHERE state IN ('PENDING', 'IN_FLIGHT')")
    fun pendingCount(): Flow<Int>

    @Query("SELECT * FROM outbox WHERE state IN ('REJECTED', 'FAILED') ORDER BY createdAt DESC")
    fun problems(): Flow<List<OutboxEntry>>

    @Query("SELECT * FROM outbox WHERE aggregateType = :type AND aggregateId = :id ORDER BY createdAt ASC")
    suspend fun forAggregate(
        type: String,
        id: String,
    ): List<OutboxEntry>

    /**
     * Recovers entries stranded `IN_FLIGHT` by a process death mid-send.
     *
     * Safe precisely because of idempotency keys: if the request did reach the server, the retry
     * returns the original result rather than applying it twice.
     */
    @Query("UPDATE outbox SET state = 'PENDING' WHERE state = 'IN_FLIGHT'")
    suspend fun requeueStranded(): Int

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface SyncCursorDao {
    @Upsert
    suspend fun upsert(cursor: SyncCursor)

    @Query("SELECT * FROM sync_cursor WHERE scope = :scope")
    suspend fun forScope(scope: String): SyncCursor?

    @Query("SELECT * FROM sync_cursor")
    fun all(): Flow<List<SyncCursor>>

    @Query("DELETE FROM sync_cursor WHERE scope = :scope")
    suspend fun reset(scope: String)

    @Query("DELETE FROM sync_cursor")
    suspend fun clear()
}
