package com.hr.leave

import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import com.hr.leave.internal.DefaultLeaveApplicationService
import com.hr.leave.internal.EmployeeLeaveEntitlement
import com.hr.leave.internal.EmployeeLeaveEntitlementRepository
import com.hr.leave.internal.LeaveApplication
import com.hr.leave.internal.LeaveApplicationDay
import com.hr.leave.internal.LeaveApplicationDayRepository
import com.hr.leave.internal.LeaveApplicationRepository
import com.hr.leave.internal.LeaveLedgerEntry
import com.hr.leave.internal.LeaveLedgerRepository
import com.hr.leave.internal.LeaveType
import com.hr.leave.internal.LeaveTypeRepository
import com.hr.leave.internal.LeaveYear
import com.hr.leave.internal.LeaveYearRepository
import com.hr.leave.internal.PublicHoliday
import com.hr.leave.internal.PublicHolidayRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Leave Application & Approval Workflow Engine (P2-BE-31 to P2-BE-36)")
class LeaveApplicationWorkflowTest {

    private val employeeLookupService = mock(EmployeeLookupService::class.java)
    private val leaveYearRepository = mock(LeaveYearRepository::class.java)
    private val leaveTypeRepository = mock(LeaveTypeRepository::class.java)
    private val entitlementRepository = mock(EmployeeLeaveEntitlementRepository::class.java)
    private val ledgerRepository = mock(LeaveLedgerRepository::class.java)
    private val applicationRepository = mock(LeaveApplicationRepository::class.java)
    private val applicationDayRepository = mock(LeaveApplicationDayRepository::class.java)
    private val publicHolidayRepository = mock(PublicHolidayRepository::class.java)

    private lateinit var workflowService: DefaultLeaveApplicationService

    private val tenantId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()
    private val approverId = UUID.randomUUID()

    private val leaveYear = LeaveYear(
        code = "2026",
        name = "Calendar Year 2026",
        startDate = LocalDate.of(2026, 1, 1),
        endDate = LocalDate.of(2026, 12, 31),
    ).apply {
        tenantId = this@LeaveApplicationWorkflowTest.tenantId
    }
    private val leaveYearId = leaveYear.id

    private val leaveType = LeaveType(
        code = "ANNUAL",
        name = "Annual Leave",
    ).apply {
        tenantId = this@LeaveApplicationWorkflowTest.tenantId
    }
    private val leaveTypeId = leaveType.id

    private val activeEmployee = EmployeeLeaveProfile(
        id = employeeId,
        tenantId = tenantId,
        employeeCode = "EMP001",
        displayName = "Kasun Perera",
        joinDate = LocalDate.of(2023, 1, 1),
        resignDate = null,
        status = "ACTIVE",
    )

    @BeforeEach
    fun setUp() {
        workflowService = DefaultLeaveApplicationService(
            employeeLookupService = employeeLookupService,
            leaveYearRepository = leaveYearRepository,
            leaveTypeRepository = leaveTypeRepository,
            entitlementRepository = entitlementRepository,
            ledgerRepository = ledgerRepository,
            applicationRepository = applicationRepository,
            applicationDayRepository = applicationDayRepository,
            publicHolidayRepository = publicHolidayRepository,
        )

        `when`(employeeLookupService.findById(employeeId)).thenReturn(activeEmployee)
        `when`(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType))
        `when`(leaveYearRepository.findAll()).thenReturn(listOf(leaveYear))
        `when`(applicationRepository.save(any(LeaveApplication::class.java))).thenAnswer { it.arguments[0] }
        `when`(applicationDayRepository.save(any(LeaveApplicationDay::class.java))).thenAnswer { it.arguments[0] }
        `when`(entitlementRepository.save(any(EmployeeLeaveEntitlement::class.java))).thenAnswer { it.arguments[0] }
        `when`(ledgerRepository.save(any(LeaveLedgerEntry::class.java))).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `day expansion excludes weekends and public holidays`() {
        // Monday 2026-03-09 to Sunday 2026-03-15 (7 calendar days)
        // Saturday & Sunday are weekend
        // Wednesday 2026-03-11 is a Public Holiday
        // Expected working days = 4.00 days (Mon, Tue, Thu, Fri)
        val startDate = LocalDate.of(2026, 3, 9)
        val endDate = LocalDate.of(2026, 3, 15)

        val holiday = PublicHoliday(
            holidayDate = LocalDate.of(2026, 3, 11),
            name = "Maha Shivaratri",
        ).apply {
            tenantId = this@LeaveApplicationWorkflowTest.tenantId
        }
        `when`(publicHolidayRepository.findAllByHolidayDateBetween(startDate, endDate)).thenReturn(listOf(holiday))

        // Balance = 10.00 days
        val entitlement = EmployeeLeaveEntitlement(
            tenantId = tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
        ).apply { balance = BigDecimal("10.00") }
        `when`(entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(employeeId, leaveYearId, leaveTypeId))
            .thenReturn(entitlement)

        val request = LeaveApplicationRequest(
            employeeId = employeeId,
            leaveTypeId = leaveTypeId,
            startDate = startDate,
            endDate = endDate,
            reason = "Family pilgrimage",
        )

        val eligibility = workflowService.checkEligibility(request)
        assertThat(eligibility.eligible).isTrue()
        assertThat(eligibility.workingDaysRequested).isEqualByComparingTo("4.00")

        val applicationDto = workflowService.submitApplication(request)
        assertThat(applicationDto.status).isEqualTo(ApplicationStatus.SUBMITTED)
        assertThat(applicationDto.totalDays).isEqualByComparingTo("4.00")
        assertThat(applicationDto.days).hasSize(7)

        val holidayDay = applicationDto.days.find { it.date == LocalDate.of(2026, 3, 11) }
        assertThat(holidayDay?.isWorkingDay).isFalse()
        assertThat(holidayDay?.hours).isEqualByComparingTo("0.00")

        val saturdayDay = applicationDto.days.find { it.date == LocalDate.of(2026, 3, 14) }
        assertThat(saturdayDay?.isWorkingDay).isFalse()
    }

    @Test
    fun `half day application counts 0_50 days`() {
        val date = LocalDate.of(2026, 3, 10) // Tuesday

        `when`(publicHolidayRepository.findAllByHolidayDateBetween(date, date)).thenReturn(emptyList())
        val entitlement = EmployeeLeaveEntitlement(
            tenantId = tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
        ).apply { balance = BigDecimal("5.00") }
        `when`(entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(employeeId, leaveYearId, leaveTypeId))
            .thenReturn(entitlement)

        val request = LeaveApplicationRequest(
            employeeId = employeeId,
            leaveTypeId = leaveTypeId,
            startDate = date,
            endDate = date,
            dayPortion = DayPortion.FIRST_HALF,
            reason = "Doctor appointment",
        )

        val eligibility = workflowService.checkEligibility(request)
        assertThat(eligibility.eligible).isTrue()
        assertThat(eligibility.workingDaysRequested).isEqualByComparingTo("0.50")

        val applicationDto = workflowService.submitApplication(request)
        assertThat(applicationDto.totalDays).isEqualByComparingTo("0.50")
        assertThat(applicationDto.dayPortion).isEqualTo(DayPortion.FIRST_HALF)
        assertThat(applicationDto.days.first().hours).isEqualByComparingTo("4.00")
    }

    @Test
    fun `checkEligibility returns explicit failure reasons on insufficient balance and overlap`() {
        val startDate = LocalDate.of(2026, 4, 1)
        val endDate = LocalDate.of(2026, 4, 5)

        `when`(publicHolidayRepository.findAllByHolidayDateBetween(startDate, endDate)).thenReturn(emptyList())

        // Entitlement only has 1.00 day, but request is for 3.00 working days (Wed, Thu, Fri)
        val entitlement = EmployeeLeaveEntitlement(
            tenantId = tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
        ).apply { balance = BigDecimal("1.00") }
        `when`(entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(employeeId, leaveYearId, leaveTypeId))
            .thenReturn(entitlement)

        // Existing approved overlap
        val existingApp = LeaveApplication(
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
            startDate = LocalDate.of(2026, 4, 2),
            endDate = LocalDate.of(2026, 4, 3),
            totalDays = BigDecimal("2.00"),
            status = ApplicationStatus.APPROVED,
        ).apply { tenantId = this@LeaveApplicationWorkflowTest.tenantId }
        `when`(
            applicationRepository.findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                employeeId, listOf(ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVED), endDate, startDate
            )
        ).thenReturn(listOf(existingApp))

        val request = LeaveApplicationRequest(
            employeeId = employeeId,
            leaveTypeId = leaveTypeId,
            startDate = startDate,
            endDate = endDate,
        )

        val eligibility = workflowService.checkEligibility(request)
        assertThat(eligibility.eligible).isFalse()
        assertThat(eligibility.reasons).anyMatch { it.contains("Insufficient leave balance") }
        assertThat(eligibility.reasons).anyMatch { it.contains("Overlapping leave application") }
    }

    @Test
    fun `approveApplication debits leave ledger and updates entitlement projection`() {
        val application = LeaveApplication(
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
            startDate = LocalDate.of(2026, 5, 4),
            endDate = LocalDate.of(2026, 5, 5),
            totalDays = BigDecimal("2.00"),
            status = ApplicationStatus.SUBMITTED,
        ).apply { tenantId = this@LeaveApplicationWorkflowTest.tenantId }
        val appId = application.id

        `when`(applicationRepository.findById(appId)).thenReturn(Optional.of(application))

        val entitlement = EmployeeLeaveEntitlement(
            tenantId = tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
        ).apply {
            balance = BigDecimal("10.00")
            taken = BigDecimal("1.00")
        }
        `when`(entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(employeeId, leaveYearId, leaveTypeId))
            .thenReturn(entitlement)

        val result = workflowService.approveApplication(appId, approverId, "Approved by Manager")

        assertThat(result.status).isEqualTo(ApplicationStatus.APPROVED)
        assertThat(result.actionedBy).isEqualTo(approverId)

        // Entitlement balance reduced from 10 to 8; taken increased from 1 to 3
        assertThat(entitlement.balance).isEqualByComparingTo("8.00")
        assertThat(entitlement.taken).isEqualByComparingTo("3.00")

        // Ledger must have a TAKEN debit entry with -2.00 days
        val captor = ArgumentCaptor.forClass(LeaveLedgerEntry::class.java)
        verify(ledgerRepository).save(captor.capture())
        val ledgerEntry = captor.value
        assertThat(ledgerEntry.entryType).isEqualTo(LedgerEntryType.TAKEN)
        assertThat(ledgerEntry.days).isEqualByComparingTo("-2.00")
        assertThat(ledgerEntry.balanceAfter).isEqualByComparingTo("8.00")
        assertThat(ledgerEntry.referenceType).isEqualTo("LEAVE_APPLICATION")
        assertThat(ledgerEntry.referenceId).isEqualTo(appId.toString())
    }

    @Test
    fun `cancelApplication reverses ledger debit and restores entitlement balance`() {
        val application = LeaveApplication(
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
            startDate = LocalDate.of(2026, 5, 4),
            endDate = LocalDate.of(2026, 5, 5),
            totalDays = BigDecimal("2.00"),
            status = ApplicationStatus.APPROVED, // already approved
        ).apply { tenantId = this@LeaveApplicationWorkflowTest.tenantId }
        val appId = application.id

        `when`(applicationRepository.findById(appId)).thenReturn(Optional.of(application))

        val entitlement = EmployeeLeaveEntitlement(
            tenantId = tenantId,
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
        ).apply {
            balance = BigDecimal("8.00")
            taken = BigDecimal("3.00")
        }
        `when`(entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(employeeId, leaveYearId, leaveTypeId))
            .thenReturn(entitlement)

        val result = workflowService.cancelApplication(appId, approverId, "Trip cancelled by user")

        assertThat(result.status).isEqualTo(ApplicationStatus.CANCELLED)

        // Entitlement balance restored to 10.00; taken reduced to 1.00
        assertThat(entitlement.balance).isEqualByComparingTo("10.00")
        assertThat(entitlement.taken).isEqualByComparingTo("1.00")

        // Ledger must have a CANCELLED reversal entry with +2.00 days
        val captor = ArgumentCaptor.forClass(LeaveLedgerEntry::class.java)
        verify(ledgerRepository).save(captor.capture())
        val ledgerEntry = captor.value
        assertThat(ledgerEntry.entryType).isEqualTo(LedgerEntryType.CANCELLED)
        assertThat(ledgerEntry.days).isEqualByComparingTo("2.00")
        assertThat(ledgerEntry.balanceAfter).isEqualByComparingTo("10.00")
        assertThat(ledgerEntry.referenceType).isEqualTo("LEAVE_CANCELLATION")
    }

    @Test
    fun `withdrawApplication transitions submitted request to withdrawn without ledger writes`() {
        val application = LeaveApplication(
            employeeId = employeeId,
            leaveYearId = leaveYearId,
            leaveTypeId = leaveTypeId,
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 2),
            totalDays = BigDecimal("2.00"),
            status = ApplicationStatus.SUBMITTED,
        ).apply { tenantId = this@LeaveApplicationWorkflowTest.tenantId }
        val appId = application.id

        `when`(applicationRepository.findById(appId)).thenReturn(Optional.of(application))

        val result = workflowService.withdrawApplication(appId, employeeId)
        assertThat(result.status).isEqualTo(ApplicationStatus.WITHDRAWN)
    }
}
