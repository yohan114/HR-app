package com.hr.app.ui.settings

import com.hr.client.model.ChannelPreference
import com.hr.client.model.NotificationChannel
import com.hr.client.model.NotificationSettings
import com.hr.client.model.QuietHoursSettings

/**
 * An event the user can be notified about.
 *
 * A local catalogue, and that is a real limitation worth naming: adding an event type currently
 * needs an app release before anyone can configure it, which is exactly the problem the
 * server-driven form schema solves for profile fields. A `GET /v1/notification-events` returning
 * keys and labels would close it the same way. Until then this list and the server's event keys
 * are coupled by convention.
 */
enum class NotifiableEvent(
    val key: String,
    val label: String,
    val description: String,
) {
    LEAVE_DECIDED(
        "leave.decided",
        "Leave decisions",
        "When your leave request is approved or rejected",
    ),
    LEAVE_REQUESTED(
        "leave.requested",
        "Leave requests",
        "When someone you approve for submits a request",
    ),
    PAYSLIP_PUBLISHED(
        "payroll.payslip.published",
        "Payslips",
        "When a new payslip is available",
    ),
    ATTENDANCE_MISSING(
        "attendance.missing",
        "Missing attendance",
        "When a clock-in or clock-out was not recorded",
    ),
    DOCUMENT_EXPIRING(
        "document.expiring",
        "Expiring documents",
        "When a visa, passport or contract is close to its expiry date",
    ),
    SECURITY_NEW_DEVICE(
        "security.new_device",
        "New sign-ins",
        "When your account is used on a device you have not used before",
    ),
    ;

    companion object {
        fun byKey(key: String): NotifiableEvent? = entries.firstOrNull { it.key == key }
    }
}

/** The channels the app offers a switch for. SMS is omitted: it is tenant-billed and opt-in elsewhere. */
val CONFIGURABLE_CHANNELS: List<NotificationChannel> =
    listOf(NotificationChannel.PUSH, NotificationChannel.EMAIL)

/**
 * Whether a channel is on when the server has stored nothing.
 *
 * Duplicated from `NotificationSettingsService.defaultEnabled` on the server, because the response
 * is deliberately sparse and the client cannot render a switch without knowing where it rests.
 * The two must agree: if they drift, a user sees a switch that is on while nothing is sent.
 */
fun defaultEnabled(channel: NotificationChannel): Boolean = channel != NotificationChannel.SMS

/**
 * The full matrix the screen draws, from the sparse set the server stores.
 *
 * The server returns only what differs from the default, so an absent entry is not "off" — it is
 * "unset, therefore the default". Reading absence as off would show every switch in the wrong
 * position on first load, and then save that wrong position back the moment the user touched
 * anything else.
 */
fun expand(settings: NotificationSettings?): Map<Pair<String, NotificationChannel>, Boolean> {
    val stored =
        settings
            ?.preferences
            .orEmpty()
            .associate { (it.eventKey to it.channel) to it.enabled }

    return NotifiableEvent.entries
        .flatMap { event -> CONFIGURABLE_CHANNELS.map { event.key to it } }
        .associateWith { (eventKey, channel) ->
            stored[eventKey to channel] ?: defaultEnabled(channel)
        }
}

/**
 * The sparse payload to send back, from the dense matrix on screen.
 *
 * Only entries differing from the default are sent, mirroring what the server stores. Sending the
 * whole matrix would work — the server filters it again — but it would also mean a client with a
 * stale idea of the defaults could write those stale values in as explicit overrides, which then
 * survive every later change to the default.
 */
fun collapse(
    matrix: Map<Pair<String, NotificationChannel>, Boolean>,
    quietHours: QuietHoursSettings?,
): NotificationSettings =
    NotificationSettings(
        preferences =
            matrix
                .filter { (key, enabled) -> enabled != defaultEnabled(key.second) }
                .map { (key, enabled) ->
                    ChannelPreference(eventKey = key.first, channel = key.second, enabled = enabled)
                },
        quietHours = quietHours,
    )
