package com.hr.mobile

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Top-level response for GET /v1/mobile/home.
 *
 * Implements P1-BE-25 per docs/home-composite.md.
 */
data class MobileHomeResponse(
    /** Opaque sync cursor for the 'home' scope. */
    val syncCursor: String?,
    val cards: List<HomeCardDto>,
)

/**
 * A server-driven dashboard card.
 *
 * Card [type] uses open strings rather than closed enums on the wire to prevent
 * older client app versions from crashing on new cards.
 */
data class HomeCardDto(
    val id: String,
    val type: String,
    val title: String,
    val priority: Int,
    val payload: Map<String, Any?>,
)

/**
 * Identified entity contributing to a count, allowing mobile clients to subtract
 * pending outbox mutations while offline.
 */
data class CountedEntity(
    val entityType: String,
    val entityId: String,
)

data class PendingApprovalsPayload(
    val totalCount: Int,
    val countIsApproximate: Boolean,
    val countedEntities: List<CountedEntity>,
    val leaveCount: Int,
    val attendanceCount: Int,
    val claimsCount: Int,
    val actionUri: String,
)

data class MilestoneItem(
    val employeeId: UUID,
    val displayName: String,
    val designation: String?,
    val photoKey: String?,
    val type: String,
    val eventDate: LocalDate,
    val yearsCount: Int?,
    val actionUri: String?,
)

data class MilestonesPayload(
    val milestones: List<MilestoneItem>,
)

data class AnnouncementItem(
    val id: UUID,
    val title: String,
    val summary: String,
    val publishedAt: Instant,
    val priority: String,
    val authorName: String,
    val actionUri: String?,
)

data class AnnouncementsPayload(
    val announcements: List<AnnouncementItem>,
)

data class QuickActionItem(
    val key: String,
    val label: String,
    val icon: String,
    val actionUri: String,
)

data class QuickActionsPayload(
    val actions: List<QuickActionItem>,
)

data class ExpiringDocumentItem(
    val id: UUID,
    val docType: String,
    val expiryDate: LocalDate,
    val daysRemaining: Long,
    val status: String,
    val actionUri: String?,
)

data class ExpiringDocumentsPayload(
    val documents: List<ExpiringDocumentItem>,
)
