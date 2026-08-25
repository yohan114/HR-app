package com.hr.shared.persistence

import java.security.SecureRandom
import java.util.UUID

/**
 * UUIDv7 generator (RFC 9562).
 *
 * Every primary key in the system is a UUIDv7 rather than a UUIDv4. The first 48 bits are a
 * Unix millisecond timestamp, so generated keys are *time-ordered*. That matters a great deal at
 * our write volumes: random UUIDv4 keys scatter inserts across the whole B-tree, causing page
 * splits and cache churn on the busiest tables (`raw_punch`, `audit_log`, `change_feed`).
 * Time-ordered keys append to the right-hand edge of the index instead.
 *
 * Layout:
 * ```
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          unix_ts_ms                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          unix_ts_ms           |  ver  |       rand_a          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |var|                        rand_b                             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                            rand_b                             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 *
 * A monotonic counter guards against collisions within the same millisecond on the same JVM.
 */
object Uuid7 {
    private val random = SecureRandom()

    @Volatile private var lastTimestamp = 0L

    @Volatile private var sequence = 0

    private val lock = Any()

    fun generate(): UUID {
        val now = System.currentTimeMillis()
        val seq: Int
        synchronized(lock) {
            if (now == lastTimestamp) {
                sequence = (sequence + 1) and 0x0FFF
                // Sequence exhausted inside one millisecond — extremely unlikely (4096 ids/ms),
                // but spin to the next millisecond rather than emit a duplicate.
                if (sequence == 0) {
                    var spin = System.currentTimeMillis()
                    while (spin <= lastTimestamp) spin = System.currentTimeMillis()
                    lastTimestamp = spin
                }
            } else {
                lastTimestamp = now
                sequence = random.nextInt(0x1000)
            }
            seq = sequence
        }

        val ts = lastTimestamp
        // high 64 bits: 48-bit timestamp | version 7 | 12-bit sequence (rand_a)
        val msb = (ts shl 16) or (0x7000L) or seq.toLong()

        // low 64 bits: variant 0b10 | 62 bits of randomness
        val randB = ByteArray(8).also(random::nextBytes)
        var lsb = 0L
        for (b in randB) lsb = (lsb shl 8) or (b.toLong() and 0xFF)
        lsb = (lsb and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE // set variant bits to 0b10

        return UUID(msb, lsb)
    }

    /** Extracts the embedded creation timestamp. Useful in diagnostics and partition routing. */
    fun timestampOf(uuid: UUID): Long {
        require(uuid.version() == 7) { "Not a UUIDv7: $uuid" }
        return uuid.mostSignificantBits ushr 16
    }
}
