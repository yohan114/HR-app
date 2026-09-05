package com.hr.notification.internal

import com.hr.notification.DigestMode
import com.hr.notification.NotificationChannel
import com.hr.notification.QuietHours
import com.hr.notification.RecipientContext
import com.hr.shared.api.BadRequestException
import com.hr.shared.api.ErrorCode
import com.hr.tenancy.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Reads and writes a user's own notification settings, and assembles the context the dispatcher
 * decides from.
 *
 * The two halves are deliberately in one place. A defaulting rule that the settings screen and the
 * dispatcher each implement separately will disagree eventually, and the symptom is a user being
 * shown a switch that is on while nothing is sent — or worse, off while things are.
 */
@Service
class NotificationSettingsService(
    private val preferences: NotificationPreferenceRepository,
    private val quietHours: NotificationQuietHoursRepository,
) {
    @Transactional(readOnly = true)
    fun settingsFor(userId: UUID): NotificationSettings {
        val tenantId = TenantContext.currentId()
        return NotificationSettings(
            preferences =
                preferences.findAllByTenantIdAndUserId(tenantId, userId).map {
                    ChannelPreference(it.eventKey, it.channel, it.enabled, it.digestMode)
                },
            quietHours =
                quietHours.findByTenantIdAndUserId(tenantId, userId)?.let {
                    QuietHoursSettings(
                        startAt = it.startAt,
                        endAt = it.endAt,
                        timezone = it.timezone,
                        days = it.days.map(Short::toInt).sorted(),
                        enabled = it.enabled,
                    )
                },
        )
    }

    /**
     * Replaces the user's settings wholesale.
     *
     * A full replace rather than a patch because the settings screen holds the entire set on
     * screen: sending only what changed means a switch the user flicked twice, ending where it
     * started, has to be tracked as unchanged — and a client that gets that wrong silently drops
     * an edit. Replacing what is shown makes the screen and the stored state the same thing.
     */
    @Transactional
    fun replace(
        userId: UUID,
        request: NotificationSettings,
    ): NotificationSettings {
        val tenantId = TenantContext.currentId()

        request.quietHours?.let(::validate)
        // Rejected before any write, so a bad entry in a long list does not leave half of it saved.
        request.preferences.forEach(::validate)

        preferences.deleteByTenantIdAndUserId(tenantId, userId)
        // Only the settings that differ from the default are stored. Persisting every switch the
        // screen shows would write a row per user per event per channel — 1.6 M in a
        // 10,000-person tenant, all restating the defaults.
        preferences.saveAll(
            request.preferences
                .filter { it.enabled != defaultEnabled(it.channel) || it.digestMode != DigestMode.IMMEDIATE }
                .map {
                    NotificationPreference(
                        tenantId = tenantId,
                        userId = userId,
                        eventKey = it.eventKey,
                        channel = it.channel,
                        enabled = it.enabled,
                        digestMode = it.digestMode,
                    )
                },
        )

        quietHours.deleteByTenantIdAndUserId(tenantId, userId)
        request.quietHours?.let {
            quietHours.save(
                NotificationQuietHoursEntity(
                    tenantId = tenantId,
                    userId = userId,
                    startAt = it.startAt,
                    endAt = it.endAt,
                    timezone = it.timezone,
                    days = it.days.map(Int::toShort).toTypedArray(),
                    enabled = it.enabled,
                ),
            )
        }

        return settingsFor(userId)
    }

    /**
     * Assembles what the dispatcher needs to decide, for one user and one event.
     *
     * Everything the decision depends on is gathered here so the decision itself stays a pure
     * function — which is what makes the quiet-hours edge cases testable without a database.
     */
    @Transactional(readOnly = true)
    fun contextFor(
        userId: UUID,
        eventKey: String,
        hasPushDestination: Boolean,
    ): RecipientContext {
        val tenantId = TenantContext.currentId()
        val stored =
            preferences
                .findAllByTenantIdAndUserId(tenantId, userId)
                .filter { it.eventKey == eventKey }
                .associateBy { it.channel }

        val enabled =
            NotificationChannel.entries
                .filter { stored[it]?.enabled ?: defaultEnabled(it) }
                .toSet()

        return RecipientContext(
            quietHours = quietHours.findByTenantIdAndUserId(tenantId, userId)?.toDomain(),
            enabledChannels = enabled,
            // A user who wants one channel digested and another immediate is asking for something
            // the daily summary cannot express, so the strictest choice wins: if anything is set
            // to digest, the event is digested.
            digestMode =
                if (stored.values.any { it.digestMode == DigestMode.DIGEST }) {
                    DigestMode.DIGEST
                } else {
                    DigestMode.IMMEDIATE
                },
            hasPushDestination = hasPushDestination,
        )
    }

    private fun validate(preference: ChannelPreference) {
        if (preference.eventKey.isBlank() || preference.eventKey.length > 128) {
            throw BadRequestException(
                code = ErrorCode.VALIDATION_FAILED,
                message = "Event key must be between 1 and 128 characters",
                field = "preferences.eventKey",
            )
        }
    }

    private fun validate(settings: QuietHoursSettings) {
        // `ZoneId.of` is the only real check available: a zone name is valid precisely when the
        // JVM's tz database knows it, and a stored zone the JVM cannot resolve would throw at
        // dispatch time — long after the user could do anything about it.
        runCatching { ZoneId.of(settings.timezone) }.getOrElse {
            throw BadRequestException(
                code = ErrorCode.VALIDATION_FAILED,
                message = "Unknown timezone: ${settings.timezone}",
                field = "quietHours.timezone",
            )
        }

        if (settings.days.any { it !in 1..7 }) {
            throw BadRequestException(
                code = ErrorCode.VALIDATION_FAILED,
                message = "Days must be between 1 (Monday) and 7 (Sunday)",
                field = "quietHours.days",
            )
        }

        // Equal times are rejected rather than stored, because the domain reads them as a
        // zero-length window and the user almost certainly meant all day. Guessing which would
        // either mute them completely or not at all, and both look like a bug.
        if (settings.startAt == settings.endAt) {
            throw BadRequestException(
                code = ErrorCode.VALIDATION_FAILED,
                message = "Quiet hours must start and end at different times",
                field = "quietHours.endAt",
            )
        }
    }

    companion object {
        /**
         * Whether a channel is on when the user has said nothing.
         *
         * SMS is the exception, and the reason is money: it is billed per message, and a default
         * that fans every event out to SMS would present a tenant with an invoice they never
         * agreed to. The rest are free to us and expected by the user.
         */
        fun defaultEnabled(channel: NotificationChannel): Boolean =
            when (channel) {
                NotificationChannel.IN_APP, NotificationChannel.PUSH, NotificationChannel.EMAIL -> true
                NotificationChannel.SMS -> false
            }
    }
}

private fun NotificationQuietHoursEntity.toDomain(): QuietHours =
    QuietHours(
        start = startAt,
        end = endAt,
        zone = ZoneId.of(timezone),
        days = days.mapNotNull { DayOfWeek.of(it.toInt()) }.toSet(),
        enabled = enabled,
    )

/* -------------------------------------------------------------------------- */
/* Transport types                                                             */
/* -------------------------------------------------------------------------- */

data class NotificationSettings(
    val preferences: List<ChannelPreference>,
    val quietHours: QuietHoursSettings?,
)

data class ChannelPreference(
    val eventKey: String,
    val channel: NotificationChannel,
    val enabled: Boolean,
    val digestMode: DigestMode = DigestMode.IMMEDIATE,
)

data class QuietHoursSettings(
    val startAt: LocalTime,
    val endAt: LocalTime,
    val timezone: String,
    val days: List<Int> = emptyList(),
    val enabled: Boolean = true,
)
