package com.hr.app.data.lifecycle

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.LifecycleApi
import com.hr.client.model.CareerMovementItem
import com.hr.client.model.CareerMovementListResponse
import com.hr.client.model.CareerMovementProposalRequest
import com.hr.client.model.CareerTimelineEvent
import com.hr.client.model.CareerTimelineResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class LifecycleRepositoryTest {

    private val lifecycleApi = mockk<LifecycleApi>()
    private val outbox = mockk<Outbox>(relaxed = true)
    private val clock = Clock { 1772870000000L }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val pendingCountFlow = MutableStateFlow(0)
    private lateinit var repository: LifecycleRepository

    private val employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val sampleTimeline = CareerTimelineResponse(
        employeeId = employeeId,
        employeeName = "Kasun Mendis",
        currentDesignation = "Lead Systems Architect",
        currentDepartment = "Engineering",
        currentGrade = "M1",
        joinDate = LocalDate.of(2021, 3, 1),
        confirmationDate = LocalDate.of(2021, 9, 1),
        events = listOf(
            CareerTimelineEvent(
                id = UUID.randomUUID(),
                eventType = CareerTimelineEvent.EventType.HIRE,
                title = "Joined Acme Corp",
                date = LocalDate.of(2021, 3, 1),
                description = "Commenced employment",
                isMilestone = true,
                departmentName = "Engineering",
                designationName = "Associate Software Engineer",
                salaryGradeCode = "E1",
                baseSalary = BigDecimal("120000.00"),
                currency = "LKR",
                changePercentage = null,
            ),
            CareerTimelineEvent(
                id = UUID.randomUUID(),
                eventType = CareerTimelineEvent.EventType.PROMOTION,
                title = "Promoted to Senior Software Engineer",
                date = LocalDate.of(2023, 4, 1),
                description = "Promotion",
                isMilestone = true,
                departmentName = "Engineering",
                designationName = "Senior Software Engineer",
                salaryGradeCode = "E3",
                baseSalary = BigDecimal("240000.00"),
                currency = "LKR",
                changePercentage = BigDecimal("50.00"),
            ),
        ),
    )

    private val sampleMovement = CareerMovementItem(
        id = UUID.randomUUID(),
        employeeId = employeeId,
        movementNumber = "MOV-2025-001",
        movementType = CareerMovementItem.MovementType.PROMOTION,
        status = CareerMovementItem.Status.APPLIED,
        requestDate = LocalDate.of(2024, 12, 1),
        effectiveDate = LocalDate.of(2025, 1, 1),
        initiatorId = UUID.randomUUID(),
        justification = "Promotion to Lead Architect",
        cascadeApplied = true,
        createdAt = OffsetDateTime.now(),
        prevDepartmentName = "Engineering",
        prevDesignationName = "Senior Software Engineer",
        prevSalaryGradeCode = "E3",
        prevBaseSalary = BigDecimal("240000.00"),
        prevCurrency = "LKR",
        newDepartmentName = "Engineering",
        newDesignationName = "Lead Systems Architect",
        newSalaryGradeCode = "M1",
        newBaseSalary = BigDecimal("350000.00"),
        newCurrency = "LKR",
        cascadeAppliedAt = OffsetDateTime.now(),
    )

    @Before
    fun setUp() {
        coEvery { outbox.pendingCount } returns pendingCountFlow
        repository = LifecycleRepository(
            lifecycleApi = lifecycleApi,
            outbox = outbox,
            json = json,
            clock = clock,
        )
    }

    @Test
    fun `refreshTimeline updates StateFlow on successful API call`() = runTest {
        coEvery { lifecycleApi.getCareerTimeline(employeeId) } returns Response.success(sampleTimeline)

        val result = repository.refreshTimeline(employeeId)

        assertTrue(result.isSuccess)
        val timeline = repository.timeline.value
        assertNotNull(timeline)
        assertEquals("Kasun Mendis", timeline?.employeeName)
        assertEquals(2, timeline?.events?.size)
        assertEquals(BigDecimal("100.00"), repository.totalSalaryGrowthPercentage.value)
    }

    @Test
    fun `refreshTimeline provides offline default fixtures on network error`() = runTest {
        coEvery { lifecycleApi.getCareerTimeline(any()) } throws RuntimeException("Network down")

        val result = repository.refreshTimeline(employeeId)

        assertTrue(result.isFailure)
        val fallback = repository.timeline.value
        assertNotNull(fallback)
        assertEquals("Kasun Mendis", fallback?.employeeName)
        assertTrue(fallback!!.events.isNotEmpty())
        assertEquals("Engineering", fallback.currentDepartment)
    }

    @Test
    fun `refreshMovements populates list from API`() = runTest {
        val listResponse = CareerMovementListResponse(
            movements = listOf(sampleMovement),
            totalCount = 1,
        )
        coEvery { lifecycleApi.getCareerMovements(employeeId, null, null) } returns Response.success(listResponse)

        val result = repository.refreshMovements(employeeId)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.movements.value.size)
        assertEquals("MOV-2025-001", repository.movements.value.first().movementNumber)
    }

    @Test
    fun `refreshMovements uses offline default fixtures on error`() = runTest {
        coEvery { lifecycleApi.getCareerMovements(any(), any(), any()) } throws RuntimeException("Connection error")

        val result = repository.refreshMovements(employeeId)

        assertTrue(result.isFailure)
        val movements = repository.movements.value
        assertTrue(movements.isNotEmpty())
        assertEquals(CareerMovementItem.MovementType.PROMOTION, movements.first().movementType)
    }

    @Test
    fun `proposeMovement adds movement item to list`() = runTest {
        val proposed = sampleMovement.copy(
            id = UUID.randomUUID(),
            movementNumber = "MOV-2026-999",
            status = CareerMovementItem.Status.SUBMITTED,
            cascadeApplied = false,
        )
        coEvery { lifecycleApi.proposeCareerMovement(any(), any()) } returns Response.success(proposed)

        val result = repository.proposeMovement(
            employeeId = employeeId,
            movementType = CareerMovementItem.MovementType.PROMOTION,
            effectiveDate = LocalDate.now().plusMonths(1),
            justification = "Distinguished performance and architecture stewardship",
            newDesignationName = "Principal Architect",
            newSalaryGradeCode = "M2",
            newBaseSalary = BigDecimal("450000.00"),
        )

        assertTrue(result.isSuccess)
        assertEquals("MOV-2026-999", result.getOrNull()?.movementNumber)
        assertTrue(repository.movements.value.any { it.movementNumber == "MOV-2026-999" })
    }

    @Test
    fun `approveMovement triggers cascade and updates movement status`() = runTest {
        val approved = sampleMovement.copy(
            status = CareerMovementItem.Status.APPLIED,
            cascadeApplied = true,
        )
        coEvery { lifecycleApi.approveCareerMovement(sampleMovement.id, any()) } returns Response.success(approved)
        coEvery { lifecycleApi.getCareerTimeline(any()) } returns Response.success(sampleTimeline)

        val result = repository.approveMovement(sampleMovement.id)

        assertTrue(result.isSuccess)
        assertEquals(CareerMovementItem.Status.APPLIED, result.getOrNull()?.status)
        assertTrue(result.getOrNull()?.cascadeApplied == true)
    }

    @Test
    fun `revertMovement restores status and marks reverted`() = runTest {
        val reverted = sampleMovement.copy(
            status = CareerMovementItem.Status.REVERTED,
            reversionReason = "Administrative clerical rollback",
            cascadeApplied = false,
        )
        coEvery { lifecycleApi.revertCareerMovement(sampleMovement.id, any(), any()) } returns Response.success(reverted)
        coEvery { lifecycleApi.getCareerTimeline(any()) } returns Response.success(sampleTimeline)

        val result = repository.revertMovement(sampleMovement.id, "Administrative clerical rollback")

        assertTrue(result.isSuccess)
        assertEquals(CareerMovementItem.Status.REVERTED, result.getOrNull()?.status)
        assertEquals("Administrative clerical rollback", result.getOrNull()?.reversionReason)
        assertFalse(result.getOrNull()?.cascadeApplied == true)
    }
}
