package com.hr.app.ui.settings

import com.hr.client.model.ChannelPreference
import com.hr.client.model.NotificationChannel
import com.hr.client.model.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * The sparse↔dense conversion between what the server stores and what the screen draws.
 *
 * The server deliberately stores only what differs from the default, so the client has to fill the
 * gaps. Getting that backwards puts every switch in the wrong position on first load — and then
 * saves those wrong positions back the moment the user touches anything else, turning a display
 * bug into a silent change of what reaches them.
 */
class NotificationSettingsStateTest {
    private val leave = NotifiableEvent.LEAVE_DECIDED.key

    @Test
    fun `an absent preference reads as its default, not as off`() {
        val switches = expand(NotificationSettings(preferences = emptyList()))

        assertTrue("push defaults on", switches[leave to NotificationChannel.PUSH] == true)
        assertTrue("email defaults on", switches[leave to NotificationChannel.EMAIL] == true)
    }

    @Test
    fun `a null response still produces a full matrix`() {
        val switches = expand(null)

        assertEquals(
            NotifiableEvent.entries.size * CONFIGURABLE_CHANNELS.size,
            switches.size,
        )
        assertTrue(switches.values.all { it })
    }

    @Test
    fun `a stored opt-out is reflected`() {
        val switches =
            expand(
                NotificationSettings(
                    preferences =
                        listOf(
                            ChannelPreference(
                                eventKey = leave,
                                channel = NotificationChannel.EMAIL,
                                enabled = false,
                            ),
                        ),
                ),
            )

        assertFalse(switches[leave to NotificationChannel.EMAIL]!!)
        assertTrue("push was untouched", switches[leave to NotificationChannel.PUSH]!!)
    }

    /** A preference for one event must not move another event's switches. */
    @Test
    fun `a preference applies only to its own event`() {
        val switches =
            expand(
                NotificationSettings(
                    preferences =
                        listOf(
                            ChannelPreference(leave, NotificationChannel.PUSH, enabled = false),
                        ),
                ),
            )

        assertTrue(switches[NotifiableEvent.PAYSLIP_PUBLISHED.key to NotificationChannel.PUSH]!!)
    }

    /**
     * A preference the app has no switch for must not be invented into the matrix. The server's
     * event vocabulary is larger than this build's, and will grow without an app release.
     */
    @Test
    fun `an unknown event key is ignored rather than added`() {
        val switches =
            expand(
                NotificationSettings(
                    preferences =
                        listOf(
                            ChannelPreference(
                                "module.we.have.not.shipped",
                                NotificationChannel.PUSH,
                                enabled = false,
                            ),
                        ),
                ),
            )

        assertNull(switches["module.we.have.not.shipped" to NotificationChannel.PUSH])
        assertEquals(NotifiableEvent.entries.size * CONFIGURABLE_CHANNELS.size, switches.size)
    }

    @Test
    fun `only changed switches are sent back`() {
        val matrix = expand(null) + ((leave to NotificationChannel.EMAIL) to false)

        val payload = collapse(matrix, quietHours = null)

        assertEquals(1, payload.preferences.size)
        assertEquals(leave, payload.preferences.single().eventKey)
        assertEquals(NotificationChannel.EMAIL, payload.preferences.single().channel)
        assertFalse(payload.preferences.single().enabled)
    }

    /**
     * Sending the whole matrix would work — the server filters it again — but a client with a
     * stale idea of the defaults would write those stale values in as explicit overrides, which
     * then survive every later change to the default.
     */
    @Test
    fun `an all-default matrix sends nothing`() {
        assertTrue(collapse(expand(null), quietHours = null).preferences.isEmpty())
    }

    /** Expanding what was collapsed must return the same screen. */
    @Test
    fun `expand and collapse round-trip`() {
        val original =
            expand(null) +
                mapOf(
                    (leave to NotificationChannel.EMAIL) to false,
                    (NotifiableEvent.ATTENDANCE_MISSING.key to NotificationChannel.PUSH) to false,
                )

        assertEquals(original, expand(collapse(original, quietHours = null)))
    }

    @Test
    fun `every event in the catalogue has a distinct key`() {
        val keys = NotifiableEvent.entries.map { it.key }

        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it.isNotBlank() })
    }

    @Test
    fun `an event can be found by its key`() {
        assertEquals(NotifiableEvent.LEAVE_DECIDED, NotifiableEvent.byKey(leave))
        assertNull(NotifiableEvent.byKey("nothing.like.this"))
    }

    /**
     * SMS is off by default and this must match the server's rule. If the two drift, a user sees a
     * switch that is on while nothing is sent — or worse, off while things are.
     */
    @Test
    fun `sms is the only channel off by default`() {
        assertFalse(defaultEnabled(NotificationChannel.SMS))
        assertTrue(defaultEnabled(NotificationChannel.PUSH))
        assertTrue(defaultEnabled(NotificationChannel.EMAIL))
        assertTrue(defaultEnabled(NotificationChannel.IN_APP))
    }

    @Test
    fun `times parse in both the forms the server may send`() {
        assertEquals(LocalTime.of(22, 0), parseTime("22:00:00"))
        assertEquals(LocalTime.of(22, 0), parseTime("22:00"))
    }

    /** A malformed time must not take the settings screen down with it. */
    @Test
    fun `an unparseable time reads as absent`() {
        assertNull(parseTime("not a time"))
        assertNull(parseTime(""))
        assertNull(parseTime(null))
    }
}
