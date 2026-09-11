package com.hr.lifecycle

import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import com.hr.lifecycle.internal.*
import com.hr.organisation.OrganisationLookupService
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Lifecycle Service & Cascade Engine Unit Tests")
class LifecycleServiceTest {

    private val careerMovementRepository = mockk<CareerMovementRepository>()
    private val salaryHistoryRepository = mockk<EmployeeSalaryHistoryRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)
    private val organisationLookupService = mockk<OrganisationLookupService>(relaxed = true)

    private lateinit var cascadeEngine: CareerCascadeEngine
    private lateinit var lifecycleService: LifecycleService

    private val tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val initiatorId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val sampleEmployee = EmployeeLeaveProfile(
        id = employeeId,
        tenantId = tenantId,
        employeeCode = "LK010",
        firstName = "Kasun",
        lastName = "Mendis",
        displayName = "Kasun Mendis",
        joinDate = LocalDate.of(2024, 1, 1),
        departmentId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        designationId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        salaryGradeId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        status = "ACTIVE",
    )

    @BeforeEach
    fun setUp() {
        cascadeEngine = CareerCascadeEngine(
            employeeLookupService = employeeLookupService,
            salaryHistoryRepository = salaryHistoryRepository,
            careerMovementRepository = careerMovementRepository,
        )

        lifecycleService = LifecycleService(
            careerMovementRepository = careerMovementRepository,
            salaryHistoryRepository = salaryHistoryRepository,
            employeeLookupService = employeeLookupService,
            organisationLookupService = organisationLookupService,
            cascadeEngine = cascadeEngine,
        )
    }

    @Test
    fun `proposeCareerMovement snapshots current employee workstation and creates submitted movement`() {
        every { employeeLookupService.findById(employeeId) } returns sampleEmployee
        every { salaryHistoryRepository.findByTenantIdAndEmployeeIdAndIsCurrentTrue(tenantId, employeeId) } returns EmployeeSalaryHistory(
            employeeId = employeeId,
            effectiveFrom = LocalDate.of(2024, 1, 1),
            baseSalary = BigDecimal("150000.00"),
            revisionReason = "Initial",
        ).apply { this.tenantId = this@LifecycleServiceTest.tenantId }
        every { organisationLookupService.findDepartmentName(any()) } returns "Engineering"
        every { organisationLookupService.findDesignationName(any()) } returns "Software Engineer"
        every { organisationLookupService.findSalaryGradeCode(any()) } returns "E2"
        every { organisationLookupService.findLocationName(any()) } returns null
        every { careerMovementRepository.countByTenantId(tenantId) } returns 5L
        every { careerMovementRepository.save(any()) } answers { firstArg() }

        val newDesignationId = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val request = CareerMovementProposalRequest(
            employeeId = employeeId,
            movementType = MovementType.PROMOTION,
            effectiveDate = LocalDate.now().plusMonths(1),
            justification = "Merit promotion for technical leadership",
            newDesignationId = newDesignationId,
            newBaseSalary = BigDecimal("200000.00"),
        )

        val result = lifecycleService.proposeCareerMovement(request, initiatorId, tenantId)

        assertNotNull(result)
        assertEquals(MovementStatus.SUBMITTED, result.status)
        assertEquals(MovementType.PROMOTION, result.movementType)
        assertEquals(BigDecimal("150000.00"), result.prevBaseSalary)
        assertEquals(BigDecimal("200000.00"), result.newBaseSalary)
        assertEquals(sampleEmployee.designationId, result.prevDesignationId)
        assertEquals(newDesignationId, result.newDesignationId)
        assertTrue(result.movementNumber.startsWith("MOV-"))
    }

    @Test
    fun `approveCareerMovement executes cascade when effective date is today`() {
        val newDesignationId = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val movement = CareerMovement(
            employeeId = employeeId,
            movementNumber = "MOV-2026-0001",
            movementType = MovementType.PROMOTION,
            status = MovementStatus.SUBMITTED,
            requestDate = LocalDate.now(),
            effectiveDate = LocalDate.now(),
            initiatorId = initiatorId,
            justification = "Promotion taking effect today",
            prevBaseSalary = BigDecimal("150000.00"),
            newBaseSalary = BigDecimal("200000.00"),
            newDesignationId = newDesignationId,
        ).apply {
            this.tenantId = this@LifecycleServiceTest.tenantId
        }
        val movementId = movement.id

        every { careerMovementRepository.findByIdAndTenantId(movementId, tenantId) } returns movement
        every { employeeLookupService.findById(employeeId) } returns sampleEmployee
        every { employeeLookupService.applyCareerCascade(any(), any(), any(), any(), any(), any(), any()) } just Runs
        every { salaryHistoryRepository.findByTenantIdAndEmployeeIdAndIsCurrentTrue(tenantId, employeeId) } returns null
        every { salaryHistoryRepository.save(any()) } answers { firstArg() }
        every { careerMovementRepository.save(any()) } answers { firstArg() }

        val approved = lifecycleService.approveCareerMovement(movementId, initiatorId, tenantId)

        assertEquals(MovementStatus.APPLIED, approved.status)
        assertTrue(approved.cascadeApplied)
        assertNotNull(approved.approvedAt)
        verify(exactly = 1) { employeeLookupService.applyCareerCascade(match { it == employeeId }, any(), match { it == newDesignationId }, any(), any(), any(), any()) }
    }

    @Test
    fun `revertCareerMovement restores prior employee master attributes and rolls back salary`() {
        val prevDesignationId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val newDesignationId = UUID.fromString("55555555-5555-5555-5555-555555555555")

        val movement = CareerMovement(
            employeeId = employeeId,
            movementNumber = "MOV-2026-0001",
            movementType = MovementType.PROMOTION,
            status = MovementStatus.APPLIED,
            requestDate = LocalDate.now(),
            effectiveDate = LocalDate.now(),
            initiatorId = initiatorId,
            justification = "Promotion applied",
            prevDesignationId = prevDesignationId,
            newDesignationId = newDesignationId,
            cascadeApplied = true,
            cascadeAppliedAt = Instant.now(),
        ).apply {
            this.tenantId = this@LifecycleServiceTest.tenantId
        }
        val movementId = movement.id

        val salaryRecord = EmployeeSalaryHistory(
            employeeId = employeeId,
            careerMovementId = movementId,
            effectiveFrom = LocalDate.now(),
            baseSalary = BigDecimal("200000.00"),
            revisionReason = "Promotion",
            isCurrent = true,
        ).apply { this.tenantId = this@LifecycleServiceTest.tenantId }

        every { careerMovementRepository.findByIdAndTenantId(movementId, tenantId) } returns movement
        every { employeeLookupService.findById(employeeId) } returns sampleEmployee
        every { employeeLookupService.revertCareerCascade(any(), any(), any(), any(), any(), any(), any()) } just Runs
        every { salaryHistoryRepository.findByTenantIdAndCareerMovementId(tenantId, movementId) } returns salaryRecord
        every { salaryHistoryRepository.delete(salaryRecord) } just Runs
        every { salaryHistoryRepository.findAllByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(tenantId, employeeId) } returns emptyList()
        every { careerMovementRepository.save(any()) } answers { firstArg() }

        val reverted = lifecycleService.revertCareerMovement(movementId, "Wrong grade assigned", tenantId)

        assertEquals(MovementStatus.REVERTED, reverted.status)
        assertFalse(reverted.cascadeApplied)
        assertEquals("Wrong grade assigned", reverted.reversionReason)
        verify(exactly = 1) { employeeLookupService.revertCareerCascade(match { it == employeeId }, any(), match { it == prevDesignationId }, any(), any(), any(), any()) }
    }

    @Test
    fun `getCareerTimeline compiles hire milestone and sorted lifecycle events`() {
        every { employeeLookupService.findById(employeeId) } returns sampleEmployee
        every { organisationLookupService.findDepartmentName(any()) } returns "Engineering"
        every { organisationLookupService.findDesignationName(any()) } returns "Software Engineer"
        every { organisationLookupService.findSalaryGradeCode(any()) } returns "E2"

        val mov1 = CareerMovement(
            employeeId = employeeId,
            movementNumber = "MOV-2024-001",
            movementType = MovementType.CONFIRMATION,
            status = MovementStatus.APPLIED,
            requestDate = LocalDate.of(2024, 6, 1),
            effectiveDate = LocalDate.of(2024, 7, 1),
            initiatorId = initiatorId,
            justification = "Probation pass",
        ).apply {
            this.tenantId = this@LifecycleServiceTest.tenantId
        }

        every { careerMovementRepository.findAllByTenantIdAndEmployeeIdOrderByEffectiveDateDesc(tenantId, employeeId) } returns listOf(mov1)
        every { salaryHistoryRepository.findAllByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(tenantId, employeeId) } returns listOf(
            EmployeeSalaryHistory(
                employeeId = employeeId,
                careerMovementId = null,
                effectiveFrom = LocalDate.of(2024, 1, 1),
                baseSalary = BigDecimal("100000.00"),
                revisionReason = "Initial Joining",
            ).apply { this.tenantId = this@LifecycleServiceTest.tenantId },
        )

        val timeline = lifecycleService.getCareerTimeline(employeeId, tenantId)

        assertNotNull(timeline)
        assertEquals(2, timeline.events.size) // HIRE + CONFIRMATION
        assertEquals(TimelineEventType.CONFIRMATION, timeline.events[0].eventType)
        assertEquals(TimelineEventType.HIRE, timeline.events[1].eventType)
    }
}
