package com.hr.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Quiet hours.
 *
 * Worth this many tests because the failure is invisible to us and obvious to the user: nobody
 * reports "I was not woken at 3am", so a window that silently stops applying is found by a customer,
 * in production, in the form of a complaint about the whole product.
 */
class QuietHoursTest {
    private val colombo = ZoneId.of("Asia/Colombo")

    private fun at(
        date: String,
        time: String,
        zone: ZoneId = colombo,
    ) = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(zone).toInstant()

    private fun nightly(
        start: String = "22:00",
        end: String = "07:00",
        days: Set<DayOfWeek> = emptySet(),
        zone: ZoneId = colombo,
    ) = QuietHours(LocalTime.parse(start), LocalTime.parse(end), zone, days)

    @Nested
    @DisplayName("a window that crosses midnight")
    inner class CrossingMidnight {
        @Test
        fun `covers the late evening and the small hours`() {
            val quiet = nightly()

            assertTrue(quiet.contains(at("2026-03-10", "23:30")), "23:30 is inside 22:00-07:00")
            assertTrue(quiet.contains(at("2026-03-10", "03:00")), "03:00 is inside 22:00-07:00")
            assertTrue(quiet.contains(at("2026-03-10", "22:00")), "the window is closed at its start")
        }

        @Test
        fun `leaves the working day alone`() {
            val quiet = nightly()

            assertFalse(quiet.contains(at("2026-03-10", "07:00")), "the window is open at its end")
            assertFalse(quiet.contains(at("2026-03-10", "12:00")))
            assertFalse(quiet.contains(at("2026-03-10", "21:59")))
        }

        /**
         * The case a naive implementation gets wrong. With the window restricted to Friday, Saturday
         * 03:00 belongs to the window that opened on Friday night — testing today's weekday instead
         * would unmute the small hours of every Saturday morning.
         */
        @Test
        fun `the weekday is the day the window opened, not the day it is now`() {
            val fridayNightsOnly = nightly(days = setOf(DayOfWeek.FRIDAY))

            // 2026-03-13 is a Friday; 2026-03-14 a Saturday.
            assertTrue(
                fridayNightsOnly.contains(at("2026-03-13", "23:00")),
                "Friday 23:00 opens the window",
            )
            assertTrue(
                fridayNightsOnly.contains(at("2026-03-14", "03:00")),
                "Saturday 03:00 is still inside the window Friday opened",
            )
            assertFalse(
                fridayNightsOnly.contains(at("2026-03-14", "23:00")),
                "Saturday 23:00 opens no window",
            )
        }
    }

    @Nested
    @DisplayName("a window within one day")
    inner class SameDay {
        @Test
        fun `covers only its own span`() {
            val lunch = nightly(start = "12:00", end = "13:00")

            assertTrue(lunch.contains(at("2026-03-10", "12:30")))
            assertFalse(lunch.contains(at("2026-03-10", "11:59")))
            assertFalse(lunch.contains(at("2026-03-10", "13:00")))
            assertFalse(lunch.contains(at("2026-03-10", "23:00")))
        }
    }

    @Test
    fun `a disabled window never applies`() {
        val quiet = nightly().copy(enabled = false)

        assertFalse(quiet.contains(at("2026-03-10", "03:00")))
    }

    /**
     * An empty window is a configuration a UI can easily produce — two pickers defaulting to the
     * same value. Reading it as "quiet all day" would mute the user entirely and look like the
     * notifications had broken.
     */
    @Test
    fun `a zero-length window is not a permanent mute`() {
        val empty = nightly(start = "09:00", end = "09:00")

        assertFalse(empty.contains(at("2026-03-10", "09:00")))
        assertFalse(empty.contains(at("2026-03-10", "03:00")))
    }

    @Nested
    @DisplayName("the end of the window")
    inner class EndOfWindow {
        @Test
        fun `an evening notification is held until the following morning`() {
            val quiet = nightly()

            assertEquals(
                at("2026-03-11", "07:00"),
                quiet.endOf(at("2026-03-10", "23:30")),
                "23:30 releases at 07:00 the next day",
            )
        }

        @Test
        fun `a small-hours notification is held until the same morning`() {
            val quiet = nightly()

            assertEquals(
                at("2026-03-10", "07:00"),
                quiet.endOf(at("2026-03-10", "03:00")),
                "03:00 releases at 07:00 the same day, not tomorrow",
            )
        }

        @Test
        fun `asking outside the window has no answer`() {
            assertNull(nightly().endOf(at("2026-03-10", "12:00")))
        }
    }

    /**
     * The reason the column is an IANA zone and not an offset. London is UTC+0 in March and UTC+1 in
     * July; a window stored as a UTC range would be an hour wrong for half the year, in the
     * direction that sends notifications into the night.
     */
    @Test
    fun `the window follows local time across a daylight saving change`() {
        val london = ZoneId.of("Europe/London")
        val quiet = nightly(zone = london)

        val winterNight = LocalDateTime.of(2026, 1, 15, 23, 0).atZone(london).toInstant()
        val summerNight = LocalDateTime.of(2026, 7, 15, 23, 0).atZone(london).toInstant()

        assertTrue(quiet.contains(winterNight), "23:00 GMT is inside the window")
        assertTrue(quiet.contains(summerNight), "23:00 BST is inside the same window")

        // The two releases are a different number of UTC hours away, which is the entire point.
        assertEquals(
            LocalTime.of(7, 0),
            quiet.endOf(summerNight)!!.atZone(london).toLocalTime(),
            "release is 07:00 local, whatever the offset",
        )
    }

    /**
     * A spring-forward gap can swallow the release time itself. Lisbon jumps 01:00 → 02:00 on
     * 2026-03-29, so a window ending at 01:30 ends at a wall-clock time that does not exist that
     * day. Resolving forward is correct: resolving backward would release *before* the window ended.
     */
    @Test
    fun `a release time inside a daylight saving gap resolves forward`() {
        val lisbon = ZoneId.of("Europe/Lisbon")
        val quiet = nightly(start = "22:00", end = "01:30", zone = lisbon)

        val duringGapNight = LocalDateTime.of(2026, 3, 28, 23, 0).atZone(lisbon).toInstant()
        val release = quiet.endOf(duringGapNight)!!

        assertTrue(
            release > duringGapNight,
            "the release must be after the notification, not before it",
        )
        assertFalse(
            quiet.contains(release),
            "the resolved release must actually be outside the window",
        )
    }
}
