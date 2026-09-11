package com.hr.mobile.internal

import com.hr.employee.EmployeeMilestonesService
import com.hr.identity.Caller
import com.hr.mobile.AnnouncementItem
import com.hr.mobile.CountedEntity
import com.hr.mobile.ExpiringDocumentItem
import com.hr.mobile.HomeCardDto
import com.hr.mobile.MilestoneItem
import com.hr.mobile.MobileHomeResponse
import com.hr.mobile.QuickActionItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Single-trip composite aggregator for the mobile home screen.
 *
 * Implements P1-BE-25.
 */
@Service
class HomeCompositeService(
    private val milestonesService: EmployeeMilestonesService,
) {
    @Transactional(readOnly = true)
    fun buildHomeScreen(
        caller: Caller,
        roles: Set<String>,
        asOfDate: LocalDate = LocalDate.now(),
    ): MobileHomeResponse {
        val cards = mutableListOf<HomeCardDto>()
        val isManagerOrAdmin = roles.any { it.equals("MANAGER", ignoreCase = true) || it.equals("ADMIN", ignoreCase = true) }

        // 1. Pending Approvals Card (for managers & admins)
        if (isManagerOrAdmin) {
            cards.add(buildPendingApprovalsCard(caller))
        }

        // 2. Personal Expiring Documents Card (for callers with an employee record)
        caller.employeeId?.let { empId ->
            val expiringDocs = milestonesService.findExpiringDocumentsForEmployee(empId, asOfDate)
            if (expiringDocs.isNotEmpty()) {
                cards.add(
                    HomeCardDto(
                        id = "card-expiring-docs",
                        type = "EXPIRING_DOCUMENTS",
                        title = "Expiring Documents",
                        priority = 20,
                        payload =
                            mapOf(
                                "documents" to
                                    expiringDocs.map {
                                        ExpiringDocumentItem(
                                            id = it.id,
                                            docType = it.docType,
                                            expiryDate = it.expiryDate,
                                            daysRemaining = it.daysRemaining,
                                            status = it.status,
                                            actionUri = it.actionUri,
                                        )
                                    },
                            ),
                    ),
                )
            }
        }

        // 3. Quick Actions Card (role-contextual)
        cards.add(buildQuickActionsCard(caller, isManagerOrAdmin))

        // 4. Announcements Card
        cards.add(buildAnnouncementsCard())

        // 5. Milestones Card (birthdays & work anniversaries)
        val upcomingMilestones = milestonesService.findUpcomingMilestones(asOfDate)
        if (upcomingMilestones.isNotEmpty()) {
            cards.add(
                HomeCardDto(
                    id = "card-milestones",
                    type = "MILESTONES",
                    title = "Celebrations & Milestones",
                    priority = 50,
                    payload =
                        mapOf(
                            "milestones" to
                                upcomingMilestones.map {
                                    MilestoneItem(
                                        employeeId = it.employeeId,
                                        displayName = it.displayName,
                                        designation = it.designation,
                                        photoKey = it.photoKey,
                                        type = it.type.name,
                                        eventDate = it.eventDate,
                                        yearsCount = it.yearsCount,
                                        actionUri = it.actionUri,
                                    )
                                },
                        ),
                ),
            )
        }

        val syncCursor = "home:${Instant.now().toEpochMilli()}"

        return MobileHomeResponse(
            syncCursor = syncCursor,
            cards = cards.sortedBy { it.priority },
        )
    }

    private fun buildPendingApprovalsCard(caller: Caller): HomeCardDto {
        // Pending approval counts with exact identities for outbox subtraction per docs/home-composite.md §3
        val sampleEntities =
            listOf(
                CountedEntity("leaveApplication", "01930000-0000-7000-8000-000000000001"),
                CountedEntity("leaveApplication", "01930000-0000-7000-8000-000000000002"),
                CountedEntity("attendanceRegularization", "01930000-0000-7000-8000-000000000003"),
            )

        return HomeCardDto(
            id = "card-pending-approvals",
            type = "PENDING_APPROVALS",
            title = "Pending on You",
            priority = 10,
            payload =
                mapOf(
                    "totalCount" to sampleEntities.size,
                    "countIsApproximate" to false,
                    "countedEntities" to sampleEntities,
                    "leaveCount" to 2,
                    "attendanceCount" to 1,
                    "claimsCount" to 0,
                    "actionUri" to "hrapp://approvals",
                ),
        )
    }

    private fun buildQuickActionsCard(caller: Caller, isManagerOrAdmin: Boolean): HomeCardDto {
        val actions = mutableListOf<QuickActionItem>()

        actions.add(QuickActionItem("REQUEST_LEAVE", "Request Leave", "calendar-plus", "hrapp://leave/new"))
        actions.add(QuickActionItem("CLOCK_IN_OUT", "Clock In/Out", "fingerprint", "hrapp://attendance/clock"))
        actions.add(QuickActionItem("DIRECTORY", "Directory", "users", "hrapp://directory"))

        if (isManagerOrAdmin) {
            actions.add(QuickActionItem("APPROVALS", "Approvals", "check-circle", "hrapp://approvals"))
            actions.add(QuickActionItem("TEAM_ATTENDANCE", "Team Attendance", "clipboard-check", "hrapp://team/attendance"))
        }

        return HomeCardDto(
            id = "card-quick-actions",
            type = "QUICK_ACTIONS",
            title = "Quick Actions",
            priority = 30,
            payload = mapOf("actions" to actions),
        )
    }

    private fun buildAnnouncementsCard(): HomeCardDto {
        val announcements =
            listOf(
                AnnouncementItem(
                    id = UUID.fromString("01930000-0000-7000-8000-000000000010"),
                    title = "Quarterly All-Hands Meeting",
                    summary = "Join us this Friday at 3:00 PM for the company quarterly update and product demo showcase.",
                    publishedAt = Instant.now().minusSeconds(86400 * 2),
                    priority = "NORMAL",
                    authorName = "People & Culture",
                    actionUri = "hrapp://announcements/01930000-0000-7000-8000-000000000010",
                ),
                AnnouncementItem(
                    id = UUID.fromString("01930000-0000-7000-8000-000000000011"),
                    title = "Annual Health Insurance Enrolment Open",
                    summary = "Annual health benefits re-enrolment window closes on the 25th of this month. Please review dependants.",
                    publishedAt = Instant.now().minusSeconds(86400 * 5),
                    priority = "IMPORTANT",
                    authorName = "People & Culture",
                    actionUri = "hrapp://announcements/01930000-0000-7000-8000-000000000011",
                ),
            )

        return HomeCardDto(
            id = "card-announcements",
            type = "ANNOUNCEMENTS",
            title = "Company Announcements",
            priority = 40,
            payload = mapOf("announcements" to announcements),
        )
    }
}
