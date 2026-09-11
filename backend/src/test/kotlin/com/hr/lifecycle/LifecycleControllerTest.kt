package com.hr.lifecycle

import com.hr.lifecycle.internal.LifecycleController
import com.hr.lifecycle.internal.LifecycleService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Lifecycle Controller Unit Tests")
class LifecycleControllerTest {

    private val lifecycleService = mockk<LifecycleService>(relaxed = true)
    private val controller = LifecycleController(lifecycleService)

    private val sampleItem = CareerMovementItem(
        id = UUID.randomUUID(),
        employeeId = UUID.randomUUID(),
        movementNumber = "MOV-2026-0001",
        movementType = MovementType.PROMOTION,
        status = MovementStatus.SUBMITTED,
        requestDate = LocalDate.now(),
        effectiveDate = LocalDate.now(),
        initiatorId = UUID.randomUUID(),
        justification = "Promotion to senior",
        newBaseSalary = BigDecimal("220000.00"),
        createdAt = Instant.now(),
    )

    @Test
    fun `getCareerMovements delegates to service`() {
        every { lifecycleService.getCareerMovements(any(), any(), any()) } returns CareerMovementListResponse(
            totalCount = 1,
            movements = listOf(sampleItem),
        )

        val response = controller.getCareerMovements(null, null, null, null)
        assertThat(response.totalCount).isEqualTo(1)
        assertThat(response.movements).hasSize(1)
        assertThat(response.movements[0].movementNumber).isEqualTo("MOV-2026-0001")
    }

    @Test
    fun `proposeCareerMovement delegates to service`() {
        val req = CareerMovementProposalRequest(
            employeeId = sampleItem.employeeId,
            movementType = MovementType.PROMOTION,
            effectiveDate = LocalDate.now(),
            justification = "Merit promotion",
        )
        every { lifecycleService.proposeCareerMovement(any(), any()) } returns sampleItem

        val response = controller.proposeCareerMovement(req, null, null)
        assertThat(response.id).isEqualTo(sampleItem.id)
        assertThat(response.movementType).isEqualTo(MovementType.PROMOTION)
    }

    @Test
    fun `getCareerTimeline delegates to service`() {
        val timeline = CareerTimelineResponse(
            employeeId = sampleItem.employeeId,
            employeeName = "Kasun Mendis",
            currentDesignation = "Software Engineer",
            currentDepartment = "Engineering",
            currentGrade = "E2",
            joinDate = LocalDate.of(2024, 1, 1),
            confirmationDate = LocalDate.of(2024, 7, 1),
            events = listOf(
                CareerTimelineEvent(
                    id = UUID.randomUUID(),
                    eventType = TimelineEventType.HIRE,
                    title = "Joined Company",
                    date = LocalDate.of(2024, 1, 1),
                    description = "Hired",
                )
            ),
        )
        every { lifecycleService.getCareerTimeline(any()) } returns timeline

        val response = controller.getCareerTimeline(null, null)
        assertThat(response.employeeName).isEqualTo("Kasun Mendis")
        assertThat(response.events).hasSize(1)
        assertThat(response.events[0].eventType).isEqualTo(TimelineEventType.HIRE)
    }

    @Test
    fun `getCareerMovementById delegates to service`() {
        every { lifecycleService.getCareerMovementById(sampleItem.id) } returns sampleItem

        val response = controller.getCareerMovementById(sampleItem.id, null)
        assertThat(response.id).isEqualTo(sampleItem.id)
    }

    @Test
    fun `approveCareerMovement delegates to service`() {
        val approvedItem = sampleItem.copy(status = MovementStatus.APPLIED, cascadeApplied = true)
        every { lifecycleService.approveCareerMovement(sampleItem.id, any()) } returns approvedItem

        val response = controller.approveCareerMovement(sampleItem.id, null, null)
        assertThat(response.status).isEqualTo(MovementStatus.APPLIED)
        assertThat(response.cascadeApplied).isTrue()
    }

    @Test
    fun `revertCareerMovement delegates to service`() {
        val revertedItem = sampleItem.copy(status = MovementStatus.REVERTED, cascadeApplied = false)
        every { lifecycleService.revertCareerMovement(sampleItem.id, any()) } returns revertedItem

        val response = controller.revertCareerMovement(sampleItem.id, CareerMovementRevertRequest("Misassigned grade"), null, null)
        assertThat(response.status).isEqualTo(MovementStatus.REVERTED)
    }
}
