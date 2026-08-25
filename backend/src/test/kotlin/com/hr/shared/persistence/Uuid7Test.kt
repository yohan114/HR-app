package com.hr.shared.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@DisplayName("UUIDv7 generation")
class Uuid7Test {
    @Test
    fun `sets version 7 and the RFC 9562 variant`() {
        repeat(1_000) {
            val uuid = Uuid7.generate()
            assertThat(uuid.version()).isEqualTo(7)
            // Variant 0b10 — the two most significant bits of the low half.
            assertThat(uuid.variant()).isEqualTo(2)
        }
    }

    @Test
    fun `embeds the current timestamp`() {
        val before = System.currentTimeMillis()
        val uuid = Uuid7.generate()
        val after = System.currentTimeMillis()

        assertThat(Uuid7.timestampOf(uuid)).isBetween(before, after)
    }

    /**
     * The whole point of choosing v7 over v4. Time-ordered keys append to the right-hand edge of
     * the index; random keys scatter inserts across the tree and cause page splits on exactly the
     * tables we write to most (`raw_punch`, `audit_log`, `change_feed`).
     */
    @Test
    fun `ids generated in sequence sort in generation order`() {
        val ids = (1..10_000).map { Uuid7.generate() }

        assertThat(ids).isEqualTo(ids.sortedWith(::compareUnsigned))
    }

    @Test
    fun `ids are unique under concurrent generation`() {
        val threads = 16
        val perThread = 5_000
        val pool = Executors.newFixedThreadPool(threads)

        val results =
            try {
                pool.invokeAll(
                    (1..threads).map {
                        Callable { (1..perThread).map { Uuid7.generate() } }
                    },
                ).flatMap { it.get() }
            } finally {
                pool.shutdown()
            }

        assertThat(results).hasSize(threads * perThread)
        assertThat(results.toSet()).hasSize(threads * perThread)
    }

    @Test
    fun `rejects timestamp extraction from a non-v7 uuid`() {
        val v4 = UUID.randomUUID()

        assertThat(runCatching { Uuid7.timestampOf(v4) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    /**
     * UUIDs must be compared as unsigned 128-bit values. `UUID.compareTo` compares the halves as
     * *signed* longs, so it orders incorrectly whenever the high bit differs — which for v7 is
     * whenever the timestamp's top bit flips. Using it here would make this test lie.
     */
    private fun compareUnsigned(
        a: UUID,
        b: UUID,
    ): Int {
        val high = java.lang.Long.compareUnsigned(a.mostSignificantBits, b.mostSignificantBits)
        return if (high != 0) high else java.lang.Long.compareUnsigned(a.leastSignificantBits, b.leastSignificantBits)
    }
}
