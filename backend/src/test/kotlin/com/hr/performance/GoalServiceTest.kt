package com.hr.performance

import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import com.hr.performance.internal.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Goal Service Unit Tests")
class GoalServiceTest {

    private val goalRepository = mockk<GoalRepository>()
    private val goalCheckInRepository = mockk<GoalCheckInRepository>()
    private val goalCycleRepository = mockk<GoalCycleRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)

    private lateinit var service: GoalService

    private val tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val employeeId = UUID.randomUUID()
    private val cycleId = UUID.randomUUID()

    private val sampleEmployee = EmployeeLeaveProfile(
        id = employeeId,
        tenantId = tenantId,
        employeeCode = "LK010",
        firstName = "Kasun",
        lastName = "Mendis",
        displayName = "Kasun Mendis",
        joinDate = LocalDate.of(2024, 1, 1),
        status = "ACTIVE",
    )

    private val sampleCycle = GoalCycle(
        code = "FY2026_GOALS",
        name = "FY2026 Goals",
        startDate = LocalDate.of(2026, 1, 1),
        endDate = LocalDate.of(2026, 12, 31),
    ).apply { this.tenantId = this@GoalServiceTest.tenantId }

    private val sampleGoal = GoalEntity(
        employeeId = employeeId,
        cycleId = cycleId,
        title = "Payroll Engine Delivery",
        category = GoalCategory.INDIVIDUAL,
        weight = BigDecimal("40.00"),
        targetValue = BigDecimal("100.00"),
        currentValue = BigDecimal("50.00"),
        unit = "%",
        startDate = LocalDate.of(2026, 1, 10),
        dueDate = LocalDate.of(2026, 6, 30),
        status = GoalStatus.IN_PROGRESS,
        progressPercentage = BigDecimal("50.00"),
    ).apply {
        this.tenantId = this@GoalServiceTest.tenantId
    }
    private val goalId get() = sampleGoal.id

    @BeforeEach
    fun setUp() {
        service = GoalService(
            goalRepository = goalRepository,
            goalCheckInRepository = goalCheckInRepository,
            goalCycleRepository = goalCycleRepository,
            employeeLookupService = employeeLookupService,
        )

        every { employeeLookupService.findById(any()) } returns sampleEmployee
        every { goalCycleRepository.findByIdAndTenantId(any(), any()) } returns sampleCycle
        every { goalCheckInRepository.findAllByTenantIdAndGoalIdOrderByCreatedAtDesc(any(), any()) } returns emptyList()
    }

    @Test
    fun `getGoals returns filtered list and counts completed goals`() {
        every { goalRepository.findAllByTenantIdAndEmployeeId(tenantId, employeeId) } returns listOf(sampleGoal)

        val response = service.getGoals(employeeId = employeeId, tenantId = tenantId)

        assertEquals(1, response.totalCount)
        assertEquals(0, response.completedCount)
        assertEquals("Payroll Engine Delivery", response.goals[0].title)
    }

    @Test
    fun `createGoal persists new goal with initial NOT_STARTED status`() {
        val request = GoalCreateRequestDto(
            employeeId = employeeId,
            cycleId = cycleId,
            title = "Zero Defect Sprint",
            category = GoalCategory.DEVELOPMENTAL,
            weight = BigDecimal("30.00"),
            targetValue = BigDecimal("10.00"),
            unit = "Bugs",
            startDate = LocalDate.of(2026, 2, 1),
            dueDate = LocalDate.of(2026, 5, 31),
        )

        every { goalRepository.save(any()) } answers { firstArg() }

        val created = service.createGoal(request, tenantId)

        assertEquals("Zero Defect Sprint", created.title)
        assertEquals(GoalStatus.NOT_STARTED, created.status)
        assertEquals(BigDecimal.ZERO, created.currentValue)
        verify(exactly = 1) { goalRepository.save(any()) }
    }

    @Test
    fun `recordGoalCheckIn updates current value, calculates percentage and updates status`() {
        val request = GoalCheckInRequestDto(
            newValue = BigDecimal("100.00"),
            note = "All deliverables shipped",
        )

        every { goalRepository.findByIdAndTenantId(goalId, tenantId) } returns sampleGoal
        every { goalCheckInRepository.save(any()) } answers { firstArg() }
        every { goalRepository.save(any()) } answers { firstArg() }

        val updated = service.recordGoalCheckIn(
            goalId = goalId,
            request = request,
            actorId = employeeId,
            tenantId = tenantId,
        )

        assertEquals(BigDecimal("100.00"), updated.currentValue)
        assertEquals(BigDecimal("100.00"), updated.progressPercentage)
        assertEquals(GoalStatus.COMPLETED, updated.status)
        verify(exactly = 1) { goalCheckInRepository.save(any()) }
        verify(exactly = 1) { goalRepository.save(any()) }
    }
}
