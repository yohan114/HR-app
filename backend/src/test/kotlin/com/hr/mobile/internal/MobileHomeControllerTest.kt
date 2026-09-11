package com.hr.mobile.internal

import com.hr.employee.EmployeeExpiringDocument
import com.hr.employee.EmployeeMilestone
import com.hr.employee.EmployeeMilestonesService
import com.hr.employee.MilestoneType
import com.hr.identity.Caller
import com.hr.mobile.CountedEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Mobile home composite (P1-BE-25 — GET /v1/mobile/home)")
class MobileHomeControllerTest {
    private val milestonesService = mock(EmployeeMilestonesService::class.java)
    private val compositeService = HomeCompositeService(milestonesService)
    private val controller = MobileHomeController(compositeService)

    private val userId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()
    private val today = LocalDate.of(2026, 9, 6)

    @Test
    fun `manager receives pending approvals card with counted entities and quick actions`() {
        val caller = Caller(userId = userId, employeeId = employeeId, deviceId = UUID.randomUUID())
        val roles = setOf("MANAGER")

        `when`(milestonesService.findExpiringDocumentsForEmployee(employeeId, today)).thenReturn(emptyList())
        `when`(milestonesService.findUpcomingMilestones(today)).thenReturn(
            listOf(
                EmployeeMilestone(
                    employeeId = UUID.randomUUID(),
                    displayName = "Kasun Fernando",
                    designation = "Software Engineer",
                    photoKey = null,
                    type = MilestoneType.BIRTHDAY,
                    eventDate = today.plusDays(1),
                    yearsCount = null,
                ),
            ),
        )

        val response = compositeService.buildHomeScreen(caller, roles, asOfDate = today)

        assertThat(response.syncCursor).isNotNull()
        assertThat(response.cards).isNotEmpty

        val cardTypes = response.cards.map { it.type }
        assertThat(cardTypes).contains("PENDING_APPROVALS", "QUICK_ACTIONS", "ANNOUNCEMENTS", "MILESTONES")

        val approvalsCard = response.cards.first { it.type == "PENDING_APPROVALS" }
        assertThat(approvalsCard.priority).isEqualTo(10)
        assertThat(approvalsCard.payload["totalCount"]).isEqualTo(3)

        @Suppress("UNCHECKED_CAST")
        val countedEntities = approvalsCard.payload["countedEntities"] as List<CountedEntity>
        assertThat(countedEntities).hasSize(3)
        assertThat(countedEntities.map { it.entityType }).contains("leaveApplication", "attendanceRegularization")

        // Milestones card
        val milestonesCard = response.cards.first { it.type == "MILESTONES" }
        assertThat(milestonesCard.priority).isEqualTo(50)
    }

    @Test
    fun `employee without manager role omits approvals card but includes expiring documents`() {
        val caller = Caller(userId = userId, employeeId = employeeId, deviceId = UUID.randomUUID())
        val roles = setOf("EMPLOYEE")

        val expiringDoc =
            EmployeeExpiringDocument(
                id = UUID.randomUUID(),
                docType = "VISA",
                expiryDate = today.plusDays(14),
                daysRemaining = 14,
                status = "EXPIRING",
                actionUri = "hrapp://documents/123",
            )

        `when`(milestonesService.findExpiringDocumentsForEmployee(employeeId, today)).thenReturn(listOf(expiringDoc))
        `when`(milestonesService.findUpcomingMilestones(today)).thenReturn(emptyList())

        val response = compositeService.buildHomeScreen(caller, roles, asOfDate = today)

        val cardTypes = response.cards.map { it.type }
        assertThat(cardTypes).doesNotContain("PENDING_APPROVALS")
        assertThat(cardTypes).contains("EXPIRING_DOCUMENTS", "QUICK_ACTIONS", "ANNOUNCEMENTS")

        val docsCard = response.cards.first { it.type == "EXPIRING_DOCUMENTS" }
        assertThat(docsCard.priority).isEqualTo(20)

        // Cards must be strictly ordered by priority ascending
        val priorities = response.cards.map { it.priority }
        assertThat(priorities).isSorted
    }

    @Test
    fun `controller extracts caller and roles from JWT`() {
        val jwt =
            Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("employee_id", employeeId.toString())
                .claim("roles", listOf("EMPLOYEE"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()

        val now = LocalDate.now()
        `when`(milestonesService.findExpiringDocumentsForEmployee(employeeId, now))
            .thenReturn(emptyList())
        `when`(milestonesService.findUpcomingMilestones(now))
            .thenReturn(emptyList())

        val response = controller.home(jwt)

        assertThat(response).isNotNull
        assertThat(response.cards).isNotEmpty
    }
}
