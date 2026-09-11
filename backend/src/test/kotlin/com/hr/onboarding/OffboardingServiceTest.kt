package com.hr.onboarding

import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import com.hr.onboarding.internal.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Offboarding Service Unit Tests")
class OffboardingServiceTest {

    private val exitTypeRepository = mockk<ExitTypeRepository>()
    private val exitReasonRepository = mockk<ExitReasonRepository>()
    private val exitNoticeRepository = mockk<ExitNoticeRepository>()
    private val exitInterviewRepository = mockk<ExitInterviewRepository>()
    private val clearanceItemRepository = mockk<ClearanceItemRepository>()
    private val clearanceTaskRepository = mockk<ClearanceTaskRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)

    private lateinit var offboardingService: OffboardingService

    private val employeeId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        offboardingService = OffboardingService(
            exitTypeRepository = exitTypeRepository,
            exitReasonRepository = exitReasonRepository,
            exitNoticeRepository = exitNoticeRepository,
            exitInterviewRepository = exitInterviewRepository,
            clearanceItemRepository = clearanceItemRepository,
            clearanceTaskRepository = clearanceTaskRepository,
            employeeLookupService = employeeLookupService,
        )
    }

    @Test
    fun `createExitNotice saves submitted resignation notice`() {
        val emp = EmployeeLeaveProfile(
            id = employeeId,
            tenantId = UUID.randomUUID(),
            employeeCode = "LK010",
            firstName = "Kasun",
            lastName = "Mendis",
            displayName = "Kasun Mendis",
            joinDate = LocalDate.of(2023, 1, 1),
            status = "ACTIVE",
        )
        val type = ExitTypeEntity(
            code = "RESIGNATION",
            name = "Voluntary Resignation",
        )

        val reason = ExitReasonEntity(
            exitTypeId = type.id,
            code = "CAREER_GROWTH",
            name = "Better Opportunity",
        )

        val notice = ExitNoticeEntity(
            noticeNumber = "EXIT-2026-001",
            employeeId = employeeId,
            exitTypeId = type.id,
            exitReasonId = reason.id,
            noticeDate = LocalDate.now(),
            requestedLastWorkingDate = LocalDate.now().plusDays(30),
            remarks = "Moving to new role",
            status = ExitNoticeStatus.SUBMITTED,
        )

        every { employeeLookupService.findById(employeeId) } returns emp
        every { exitTypeRepository.findById(type.id) } returns Optional.of(type)
        every { exitReasonRepository.findById(reason.id) } returns Optional.of(reason)
        every { exitNoticeRepository.count() } returns 0
        every { exitNoticeRepository.save(any()) } returns notice

        val req = ExitNoticeCreateRequest(
            exitTypeId = type.id,
            exitReasonId = reason.id,
            noticeDate = LocalDate.now(),
            requestedLastWorkingDate = LocalDate.now().plusDays(30),
            remarks = "Moving to new role",
        )

        val result = offboardingService.createExitNotice(employeeId, req)

        assertNotNull(result)
        assertEquals("EXIT-2026-001", result.noticeNumber)
        assertEquals(ExitNoticeStatus.SUBMITTED, result.status)
        assertEquals("Voluntary Resignation", result.exitTypeName)
        verify { exitNoticeRepository.save(any()) }
    }

    @Test
    fun `approveExitNotice advances status to APPROVED and generates clearance tasks`() {
        val approverId = UUID.randomUUID()
        val type = ExitTypeEntity(
            code = "RESIGNATION",
            name = "Voluntary Resignation",
        )
        val notice = ExitNoticeEntity(
            noticeNumber = "EXIT-2026-001",
            employeeId = employeeId,
            exitTypeId = type.id,
            exitReasonId = null,
            noticeDate = LocalDate.now(),
            requestedLastWorkingDate = LocalDate.now().plusDays(30),
            status = ExitNoticeStatus.SUBMITTED,
        )

        val itItem = ClearanceItemEntity(
            department = ClearanceDepartment.IT_INFRASTRUCTURE,
            code = "LAPTOP_RETURN",
            name = "Laptop Return",
        )

        every { exitNoticeRepository.findById(notice.id) } returns Optional.of(notice)
        every { employeeLookupService.findById(approverId) } returns null
        every { exitNoticeRepository.save(any()) } returns notice
        every { clearanceTaskRepository.findByExitNoticeId(notice.id) } returns emptyList()
        every { clearanceItemRepository.findAll() } returns listOf(itItem)
        every { clearanceTaskRepository.save(any()) } returns mockk()
        every { employeeLookupService.findById(employeeId) } returns null
        every { exitTypeRepository.findById(type.id) } returns Optional.of(type)
        every { exitReasonRepository.findById(any()) } returns Optional.empty()

        val req = ExitNoticeApproveRequest(
            approvedLastWorkingDate = LocalDate.now().plusDays(30),
            remarks = "Approved by manager",
        )

        val result = offboardingService.approveExitNotice(notice.id, req, approverId)

        assertEquals(ExitNoticeStatus.APPROVED, result.status)
        assertEquals(LocalDate.now().plusDays(30), result.approvedLastWorkingDate)
        verify { clearanceTaskRepository.save(any()) }
    }

    @Test
    fun `getClearance aggregates task counts and recoverable dues`() {
        val type = ExitTypeEntity(
            code = "RESIGNATION",
            name = "Voluntary Resignation",
        )
        val notice = ExitNoticeEntity(
            noticeNumber = "EXIT-2026-001",
            employeeId = employeeId,
            exitTypeId = type.id,
            noticeDate = LocalDate.now(),
            requestedLastWorkingDate = LocalDate.now().plusDays(30),
        )

        val task1 = ClearanceTaskEntity(
            exitNoticeId = notice.id,
            employeeId = employeeId,
            department = ClearanceDepartment.IT_INFRASTRUCTURE,
            title = "Laptop Return",
            status = ClearanceTaskStatus.CLEARED,
            recoverableAmount = BigDecimal.ZERO,
        )

        val task2 = ClearanceTaskEntity(
            exitNoticeId = notice.id,
            employeeId = employeeId,
            department = ClearanceDepartment.FINANCE_PAYROLL,
            title = "Company Loan Balance",
            status = ClearanceTaskStatus.PENDING,
            recoverableAmount = BigDecimal("25000.00"),
        )

        every { exitNoticeRepository.findById(notice.id) } returns Optional.of(notice)
        every { employeeLookupService.findById(employeeId) } returns null
        every { clearanceTaskRepository.findByExitNoticeId(notice.id) } returns listOf(task1, task2)
        every { employeeLookupService.findAll() } returns emptyList()

        val clearance = offboardingService.getClearance(notice.id)

        assertEquals(2, clearance.totalTasks)
        assertEquals(1, clearance.clearedTasks)
        assertEquals(1, clearance.pendingTasks)
        assertEquals(BigDecimal("25000.00"), clearance.totalRecoverableAmount)
    }
}
