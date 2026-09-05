package com.hr.notification

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A user's "do not disturb" window.
 *
 * Held as local wall-clock times plus an IANA zone rather than as a UTC range, because a UTC range
 * is wrong for half the year anywhere that observes daylight saving — and the symptom is push
 * notifications arriving an hour into the night, every night, for six months.
 *
 * @param days weekdays the window applies to; empty means every day.
 */
data class QuietHours(
    val start: LocalTime,
    val end: LocalTime,
    val zone: ZoneId,
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
) {
    /**
     * Whether the window wraps past midnight. `22:00 → 07:00` is the common configuration, and it
     * is the one that makes every naive `start <= t && t < end` comparison wrong.
     */
    val crossesMidnight: Boolean get() = start > end

    /**
     * Whether [instant] falls inside the window.
     *
     * The subtlety is which day a wrapped window belongs to. With `days = {FRIDAY}` and a
     * `22:00 → 07:00` window, Saturday at 03:00 is inside the window that *started* Friday night —
     * so the weekday is tested against the day the window opened, not against the day it is now.
     * Reading it the other way silently unmutes the small hours of every Saturday.
     */
    fun contains(instant: Instant): Boolean {
        if (!enabled) return false
        if (start == end) return false

        val local = instant.atZone(zone)
        val time = local.toLocalTime()

        val inWindow: Boolean
        val windowOpenedOn: DayOfWeek
        if (crossesMidnight) {
            val inEvening = time >= start
            inWindow = inEvening || time < end
            // Before `end` means we are in the tail of yesterday's window.
            windowOpenedOn = if (inEvening) local.dayOfWeek else local.dayOfWeek.minus(1)
        } else {
            inWindow = time >= start && time < end
            windowOpenedOn = local.dayOfWeek
        }

        if (!inWindow) return false
        return days.isEmpty() || windowOpenedOn in days
    }

    /**
     * The instant the current window closes, for deferring a held notification.
     *
     * Returns null when [instant] is not inside the window — asking when quiet time ends while it
     * is not quiet has no sensible answer, and returning "now" would let a caller defer things that
     * were never held.
     *
     * A window ending at a wall-clock time that does not exist on a spring-forward day (02:30 in a
     * zone that jumps 02:00 → 03:00) resolves forward to the first valid instant after it, which is
     * `java.time`'s gap behaviour and the right one here: the alternative is releasing early, into
     * the hour the user asked to be left alone.
     */
    fun endOf(instant: Instant): Instant? {
        if (!contains(instant)) return null

        val local = instant.atZone(zone)
        val endsToday = !crossesMidnight || local.toLocalTime() < end
        val endDate = if (endsToday) local.toLocalDate() else local.toLocalDate().plusDays(1)

        return ZonedDateTime.of(endDate, end, zone).toInstant()
    }
}
