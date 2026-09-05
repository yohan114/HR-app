package com.hr.notification.internal

import com.hr.notification.DigestMode
import com.hr.notification.NotificationChannel
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * One user's choice about one event on one channel.
 *
 * Keyed by what identifies it rather than by a surrogate id. A generated id plus a unique
 * constraint on the same four columns would be the same statement made twice, and would let a
 * second row for the same combination exist for as long as it took someone to drop the constraint.
 *
 * Not a [com.hr.shared.persistence.TenantScopedEntity]: that base class carries a single-column
 * `id`, which is incompatible with a composite key. `tenantId` is therefore set explicitly by the
 * service. Row-level security remains the real boundary either way — the base class is defence in
 * depth, not the control itself.
 */
@Entity
@Table(name = "notification_preference")
@IdClass(NotificationPreferenceId::class)
class NotificationPreference(
    @Id
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,
    @Id
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Id
    @Column(name = "event_key", nullable = false, length = 128)
    var eventKey: String,
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    var channel: NotificationChannel,
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
    @Enumerated(EnumType.STRING)
    @Column(name = "digest_mode", nullable = false, length = 16)
    var digestMode: DigestMode = DigestMode.IMMEDIATE,
) {
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}

/** Composite key for [NotificationPreference]. */
data class NotificationPreferenceId(
    var tenantId: UUID? = null,
    var userId: UUID? = null,
    var eventKey: String? = null,
    var channel: NotificationChannel? = null,
) : Serializable

/**
 * A user's do-not-disturb window.
 *
 * One row per user, not per event: "do not wake me at 3am" is a property of the person.
 */
@Entity
@Table(name = "notification_quiet_hours")
@IdClass(NotificationQuietHoursId::class)
class NotificationQuietHoursEntity(
    @Id
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,
    @Id
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "start_at", nullable = false)
    var startAt: LocalTime,
    @Column(name = "end_at", nullable = false)
    var endAt: LocalTime,
    /** IANA zone. See the column comment in `V9__notifications.sql` for why not an offset. */
    @Column(name = "timezone", nullable = false, length = 64)
    var timezone: String,
    /**
     * Weekdays the window applies to, 1 = Monday. Empty means every day.
     *
     * A Postgres `smallint[]`. This is the only array column mapped in the codebase, and like the
     * rest of the persistence layer it has never run against a real database.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days", nullable = false)
    var days: Array<Short> = emptyArray(),
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
) {
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}

/** Composite key for [NotificationQuietHoursEntity]. */
data class NotificationQuietHoursId(
    var tenantId: UUID? = null,
    var userId: UUID? = null,
) : Serializable
