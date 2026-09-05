package com.hr.notification.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationPreferenceRepository :
    JpaRepository<NotificationPreference, NotificationPreferenceId> {
    /**
     * Every stored preference for one user.
     *
     * Absence is meaningful: a user with no rows has expressed no opinion and gets the defaults.
     * Seeding a row per user per event per channel would put 1.6 M rows in a 10,000-person tenant,
     * all of them saying the same thing as the default.
     */
    fun findAllByTenantIdAndUserId(
        tenantId: UUID,
        userId: UUID,
    ): List<NotificationPreference>

    fun deleteByTenantIdAndUserId(
        tenantId: UUID,
        userId: UUID,
    )
}

interface NotificationQuietHoursRepository :
    JpaRepository<NotificationQuietHoursEntity, NotificationQuietHoursId> {
    fun findByTenantIdAndUserId(
        tenantId: UUID,
        userId: UUID,
    ): NotificationQuietHoursEntity?

    fun deleteByTenantIdAndUserId(
        tenantId: UUID,
        userId: UUID,
    )
}
