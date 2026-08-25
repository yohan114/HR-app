package com.hr.app.data.sync

import com.google.common.truth.Truth.assertThat
import com.hr.app.data.local.OutboxDao
import com.hr.app.data.local.OutboxEntry
import com.hr.app.data.local.OutboxState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Tests for the outbox, covering the parts of docs/sync-protocol.md §4 that are pure logic.
 *
 * The scenarios that need a real database or a real network live in the instrumented suite; these
 * cover the decisions — backoff, deadlines, idempotency-key stability — that are easy to get
 * subtly wrong and impossible to notice in manual testing.
 */
class OutboxTest {
    private val dao: OutboxDao = mockk(relaxed = true)
    private var now = 1_000_000L
    private val clock = Clock { now }

    private lateinit var outbox: Outbox

    @Before
    fun setUp() {
        outbox = Outbox(dao, clock)
    }

    @Test
    fun `enqueue assigns a distinct idempotency key per entry`() =
        runTest {
            val captured = mutableListOf<OutboxEntry>()
            coEvery { dao.insert(capture(captured)) } returns Unit

            outbox.enqueue("leaveApplication", "a", "POST", "/v1/leave/applications", "{}")
            outbox.enqueue("leaveApplication", "b", "POST", "/v1/leave/applications", "{}")

            assertThat(captured).hasSize(2)
            assertThat(captured[0].idempotencyKey).isNotEqualTo(captured[1].idempotencyKey)
            assertThat(captured[0].state).isEqualTo(OutboxState.PENDING)
        }

    /**
     * The property that makes indefinite retry safe. If a retry regenerated the key, a response
     * lost on the return path would produce a duplicate leave application or a double punch.
     */
    @Test
    fun `retry preserves the original idempotency key`() =
        runTest {
            val entry = entry(createdAt = now)
            val updated = slot<OutboxEntry>()
            coEvery { dao.update(capture(updated)) } returns Unit

            outbox.scheduleRetry(entry)

            assertThat(updated.captured.idempotencyKey).isEqualTo(entry.idempotencyKey)
            assertThat(updated.captured.attemptCount).isEqualTo(1)
            assertThat(updated.captured.state).isEqualTo(OutboxState.PENDING)
        }

    @Test
    fun `backoff grows exponentially and is capped`() {
        // Full jitter means each value is a sample from [0, bound], so assert the bound rather
        // than an exact figure — a test that pinned the exact value would be asserting the RNG.
        val samples = (1..12).map { attempt -> (1..200).maxOf { outbox.backoffMillis(attempt) } }

        assertThat(samples[0]).isAtMost(1_000L)
        assertThat(samples[3]).isAtMost(8_000L)
        // Capped at five minutes regardless of how many attempts have been made.
        assertThat(samples.last()).isAtMost(5 * 60 * 1_000L)
    }

    @Test
    fun `backoff is jittered rather than fixed`() {
        // Without jitter every device in a company retries in lockstep after an outage and knocks
        // the recovering server over again.
        val values = (1..200).map { outbox.backoffMillis(6) }.toSet()

        assertThat(values.size).isGreaterThan(1)
    }

    @Test
    fun `an entry retrying past the deadline is marked failed`() =
        runTest {
            val eightDaysAgo = now - 8L * 24 * 60 * 60 * 1_000
            val updated = slot<OutboxEntry>()
            coEvery { dao.update(capture(updated)) } returns Unit

            outbox.scheduleRetry(entry(createdAt = eightDaysAgo))

            assertThat(updated.captured.state).isEqualTo(OutboxState.FAILED)
            assertThat(updated.captured.failureCode).isEqualTo("RETRY_DEADLINE_EXCEEDED")
        }

    @Test
    fun `an entry inside the deadline stays pending`() =
        runTest {
            val sixDaysAgo = now - 6L * 24 * 60 * 60 * 1_000
            val updated = slot<OutboxEntry>()
            coEvery { dao.update(capture(updated)) } returns Unit

            outbox.scheduleRetry(entry(createdAt = sixDaysAgo))

            assertThat(updated.captured.state).isEqualTo(OutboxState.PENDING)
        }

    /**
     * Discarding what someone typed because the server said no is hostile. The UI offers it back
     * for editing, which it can only do if the payload survives.
     */
    @Test
    fun `rejection retains the payload for editing`() =
        runTest {
            val entry = entry(createdAt = now)
            val updated = slot<OutboxEntry>()
            coEvery { dao.update(capture(updated)) } returns Unit

            outbox.markRejected(entry, "LEAVE_BALANCE_INSUFFICIENT", "Not enough balance")

            assertThat(updated.captured.state).isEqualTo(OutboxState.REJECTED)
            assertThat(updated.captured.payload).isEqualTo(entry.payload)
            assertThat(updated.captured.failureCode).isEqualTo("LEAVE_BALANCE_INSUFFICIENT")
        }

    @Test
    fun `confirmation removes the entry`() =
        runTest {
            val entry = entry(createdAt = now)

            outbox.markConfirmed(entry)

            coVerify { dao.delete(entry.id) }
        }

    @Test
    fun `stranded in-flight entries are requeued`() =
        runTest {
            coEvery { dao.requeueStranded() } returns 3

            assertThat(outbox.recoverStranded()).isEqualTo(3)
        }

    private fun entry(createdAt: Long) =
        OutboxEntry(
            id = "entry-1",
            idempotencyKey = "0193f2a1-key",
            aggregateType = "leaveApplication",
            aggregateId = "agg-1",
            httpMethod = "POST",
            path = "/v1/leave/applications",
            payload = """{"days":3}""",
            state = OutboxState.IN_FLIGHT,
            createdAt = createdAt,
        )
}
