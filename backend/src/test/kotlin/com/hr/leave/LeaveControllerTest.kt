package com.hr.leave

import com.hr.employee.EmployeeLookupService
import com.hr.employee.EmployeeLeaveProfile
import com.hr.identity.internal.TokenService
import com.hr.leave.internal.CancelLeaveRequestPayload
import com.hr.leave.internal.LeaveApplicationRepository
import com.hr.leave.internal.LeaveApplicationRequestPayload
import com.hr.leave.internal.LeaveController
import com.hr.leave.internal.LeaveEligibilityRequestPayload
import com.hr.leave.internal.LeaveLedgerEntry
import com.hr.leave.internal.LeaveLedgerRepository
import com.hr.leave.internal.LeaveType
import com.hr.leave.internal.LeaveTypeRepository
import com.hr.leave.internal.LeaveYear
import com.hr.leave.internal.LeaveYearRepository
import com.hr.leave.internal.PublicHolidayRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Leave Controller REST Endpoints Unit Tests")
class LeaveControllerTest {

    private val leaveBalanceService = mockk<LeaveBalanceService>(relaxed = true)
    private val leaveApplicationService = mockk<LeaveApplicationService>(relaxed = true)
    private val leaveYearRepository = mockk<LeaveYearRepository>(relaxed = true)
    private val leaveTypeRepository = mockk<LeaveTypeRepository>(relaxed = true)
    private val leaveLedgerRepository = mockk<LeaveLedgerRepository>(relaxed = true)
    private val applicationRepository = mockk<LeaveApplicationRepository>(relaxed = true)
    private val publicHolidayRepository = mockk<PublicHolidayRepository>(relaxed = true)
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)

    private val controller = LeaveController(
        leaveBalanceService,
        leaveApplicationService,
        leaveYearRepository,
        leaveTypeRepository,
        leaveLedgerRepository,
        applicationRepository,
        publicHolidayRepository,
        employeeLookupService,
    )

    private val tenantId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()

    private val activeLeaveYear = LeaveYear(
        code = "2026",
        name = "Leave Year 2026",
        startDate = LocalDate.of(2026, 1, 1),
        endDate = LocalDate.of(2026, 12, 31),
    ).apply {
        status = LeaveYearStatus.ACTIVE
    }

    private val annualType = LeaveType(
        code = "ANNUAL",
        name = "Annual Leave",
    ).apply {
        color = "#0284c7"
        sequence = 1
    }

    private val casualType = LeaveType(
        code = "CASUAL",
        name = "Casual Leave",
    ).apply {
        color = "#16a34a"
        sequence = 2
    }

    private lateinit var jwt: Jwt

    @BeforeEach
    fun setUp() {
        jwt = mockk<Jwt>(relaxed = true) {
            every { subject } returns userId.toString()
            every { getClaimAsString(TokenService.CLAIM_TENANT_ID) } returns tenantId.toString()
            every { getClaimAsString(TokenService.CLAIM_EMPLOYEE_ID) } returns employeeId.toString()
        }

        every { leaveYearRepository.findActiveYearAsOf(any()) } returns activeLeaveYear
        every { leaveYearRepository.findByStatus(LeaveYearStatus.ACTIVE) } returns listOf(activeLeaveYear)
        every { leaveTypeRepository.findAll() } returns listOf(annualType, casualType)
        every { leaveTypeRepository.findById(annualType.id) } returns Optional.of(annualType)
        every { leaveTypeRepository.findByCode("ANNUAL") } returns annualType
        every { leaveTypeRepository.findByCode("CASUAL") } returns casualType

        every { employeeLookupService.findById(employeeId) } returns EmployeeLeaveProfile(
            id = employeeId,
            tenantId = tenantId,
            employeeCode = "EMP001",
            displayName = "Kasun Perera",
            joinDate = LocalDate.of(2024, 1, 1),
            resignDate = null,
            status = "ACTIVE",
        )
    }

    @Test
    fun `getMyLeaveBalances returns balance list with computed pending and available days`() {
        val annualDto = LeaveBalanceDto(
            employeeId = employeeId,
            leaveYearId = activeLeaveYear.id,
            leaveTypeId = annualType.id,
            leaveTypeCode = "ANNUAL",
            leaveTypeName = "Annual Leave",
            openingBalance = BigDecimal("14.00"),
            accrued = BigDecimal("0.00"),
            taken = BigDecimal("3.00"),
            adjusted = BigDecimal("0.00"),
            carriedForward = BigDecimal("0.00"),
            encashed = BigDecimal("0.00"),
            expired = BigDecimal("0.00"),
            balance = BigDecimal("11.00"),
            lastAccruedAt = Instant.now(),
        )

        every { leaveBalanceService.getAllBalancesForEmployee(employeeId, activeLeaveYear.id) } returns listOf(annualDto)
        every { applicationRepository.findAllByEmployeeIdAndLeaveTypeIdAndStatus(employeeId, annualType.id, ApplicationStatus.SUBMITTED) } returns emptyList()

        val response = controller.getMyLeaveBalances(jwt)

        assertThat(response.leaveYear).isEqualTo("2026")
        assertThat(response.balances).hasSize(1)
        val balance = response.balances[0]
        assertThat(balance.leaveTypeCode).isEqualTo("ANNUAL")
        assertThat(balance.entitledDays).isEqualTo(14.0)
        assertThat(balance.takenDays).isEqualTo(3.0)
        assertThat(balance.availableDays).isEqualTo(11.0)
        assertThat(balance.color).isEqualTo("#0284c7")
    }

    @Test
    fun `getMyLeaveApplications returns applications list with status filter`() {
        val app1 = LeaveApplicationDto(
            id = UUID.randomUUID(),
            employeeId = employeeId,
            leaveYearId = activeLeaveYear.id,
            leaveTypeId = annualType.id,
            leaveTypeCode = "ANNUAL",
            leaveTypeName = "Annual Leave",
            startDate = LocalDate.of(2026, 9, 14),
            endDate = LocalDate.of(2026, 9, 15),
            totalDays = BigDecimal("2.00"),
            dayPortion = DayPortion.FULL_DAY,
            status = ApplicationStatus.APPROVED,
            reason = "Family vacation",
            submittedAt = Instant.parse("2026-09-01T10:00:00Z"),
            actionedAt = Instant.parse("2026-09-02T12:00:00Z"),
            actionedBy = UUID.randomUUID(),
            actionReason = "Approved by manager",
        )

        val app2 = LeaveApplicationDto(
            id = UUID.randomUUID(),
            employeeId = employeeId,
            leaveYearId = activeLeaveYear.id,
            leaveTypeId = casualType.id,
            leaveTypeCode = "CASUAL",
            leaveTypeName = "Casual Leave",
            startDate = LocalDate.of(2026, 9, 20),
            endDate = LocalDate.of(2026, 9, 20),
            totalDays = BigDecimal("1.00"),
            dayPortion = DayPortion.FULL_DAY,
            status = ApplicationStatus.SUBMITTED,
            reason = "Personal errand",
            submittedAt = Instant.parse("2026-09-05T08:00:00Z"),
            actionedAt = null,
            actionedBy = null,
            actionReason = null,
        )

        every { leaveApplicationService.getEmployeeApplications(employeeId) } returns listOf(app1, app2)

        val allResponse = controller.getMyLeaveApplications(jwt, null)
        assertThat(allResponse.applications).hasSize(2)

        val submittedResponse = controller.getMyLeaveApplications(jwt, "SUBMITTED")
        assertThat(submittedResponse.applications).hasSize(1)
        assertThat(submittedResponse.applications[0].leaveTypeCode).isEqualTo("CASUAL")
        assertThat(submittedResponse.applications[0].employeeName).isEqualTo("Kasun Perera")
    }

    @Test
    fun `checkLeaveEligibility evaluates request and returns day-by-day expansion`() {
        val payload = LeaveEligibilityRequestPayload(
            leaveTypeId = annualType.id.toString(),
            startDate = LocalDate.of(2026, 9, 11), // Friday
            endDate = LocalDate.of(2026, 9, 14),   // Monday (Sat/Sun weekend)
            dayPortion = "FULL_DAY",
        )

        every { leaveApplicationService.checkEligibility(any()) } returns LeaveEligibilityResult(
            eligible = true,
            workingDaysRequested = BigDecimal("2.00"),
            currentBalance = BigDecimal("10.00"),
            pendingDays = BigDecimal("0.00"),
            availableBalance = BigDecimal("10.00"),
            reasons = emptyList(),
        )

        val response = controller.checkLeaveEligibility(jwt, payload)

        assertThat(response.eligible).isTrue()
        assertThat(response.workingDaysRequested).isEqualTo(2.0)
        assertThat(response.balanceAvailable).isEqualTo(10.0)
        assertThat(response.remainingAfter).isEqualTo(8.0)
        assertThat(response.days).hasSize(4) // Fri, Sat, Sun, Mon

        val fri = response.days.first { it.date == LocalDate.of(2026, 9, 11) }
        assertThat(fri.isWorkingDay).isTrue()

        val sat = response.days.first { it.date == LocalDate.of(2026, 9, 12) }
        assertThat(sat.isWorkingDay).isFalse()

        val mon = response.days.first { it.date == LocalDate.of(2026, 9, 14) }
        assertThat(mon.isWorkingDay).isTrue()
    }

    @Test
    fun `submitLeaveApplication creates application and returns DTO`() {
        val payload = LeaveApplicationRequestPayload(
            leaveTypeId = annualType.id.toString(),
            startDate = LocalDate.of(2026, 10, 5),
            endDate = LocalDate.of(2026, 10, 6),
            dayPortion = "FULL_DAY",
            reason = "Festival holiday",
        )

        val createdApp = LeaveApplicationDto(
            id = UUID.randomUUID(),
            employeeId = employeeId,
            leaveYearId = activeLeaveYear.id,
            leaveTypeId = annualType.id,
            leaveTypeCode = "ANNUAL",
            leaveTypeName = "Annual Leave",
            startDate = LocalDate.of(2026, 10, 5),
            endDate = LocalDate.of(2026, 10, 6),
            totalDays = BigDecimal("2.00"),
            dayPortion = DayPortion.FULL_DAY,
            status = ApplicationStatus.SUBMITTED,
            reason = "Festival holiday",
            submittedAt = Instant.now(),
            actionedAt = null,
            actionedBy = null,
            actionReason = null,
        )

        every { leaveApplicationService.submitApplication(any()) } returns createdApp

        val response = controller.submitLeaveApplication(jwt, payload)

        assertThat(response.status).isEqualTo("SUBMITTED")
        assertThat(response.totalDays).isEqualTo(2.0)
        assertThat(response.reason).isEqualTo("Festival holiday")
    }

    @Test
    fun `cancelLeaveApplication withdraws submitted leave request`() {
        val appId = UUID.randomUUID()

        val submittedApp = LeaveApplicationDto(
            id = appId,
            employeeId = employeeId,
            leaveYearId = activeLeaveYear.id,
            leaveTypeId = annualType.id,
            leaveTypeCode = "ANNUAL",
            leaveTypeName = "Annual Leave",
            startDate = LocalDate.of(2026, 10, 5),
            endDate = LocalDate.of(2026, 10, 6),
            totalDays = BigDecimal("2.00"),
            dayPortion = DayPortion.FULL_DAY,
            status = ApplicationStatus.SUBMITTED,
            reason = "Festival holiday",
            submittedAt = Instant.now(),
            actionedAt = null,
            actionedBy = null,
            actionReason = null,
        )

        val withdrawnApp = submittedApp.copy(status = ApplicationStatus.WITHDRAWN)

        every { leaveApplicationService.getApplication(appId) } returns submittedApp
        every { leaveApplicationService.withdrawApplication(appId, employeeId) } returns withdrawnApp

        val response = controller.cancelLeaveApplication(jwt, appId, CancelLeaveRequestPayload("No longer needed"))

        assertThat(response.status).isEqualTo("WITHDRAWN")
    }

    @Test
    fun `getMyLeaveLedger returns itemized audit statement`() {
        val ledgerEntry = LeaveLedgerEntry(
            employeeId = employeeId,
            leaveYearId = activeLeaveYear.id,
            leaveTypeId = annualType.id,
            entryType = LedgerEntryType.OPENING,
            days = BigDecimal("14.00"),
            referenceType = "LEAVE_YEAR",
            referenceId = "2026",
            effectiveDate = LocalDate.of(2026, 1, 1),
            balanceAfter = BigDecimal("14.00"),
            remarks = "Annual upfront entitlement 2026",
        )

        every { leaveLedgerRepository.findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employeeId) } returns listOf(ledgerEntry)

        val response = controller.getMyLeaveLedger(jwt, null)

        assertThat(response.ledger).hasSize(1)
        val item = response.ledger[0]
        assertThat(item.leaveTypeCode).isEqualTo("ANNUAL")
        assertThat(item.eventType).isEqualTo("OPENING")
        assertThat(item.daysCredited).isEqualTo(14.0)
        assertThat(item.daysDebited).isEqualTo(0.0)
        assertThat(item.balanceAfter).isEqualTo(14.0)
    }

    @Test
    fun `getTeamCalendar aggregates days and flags weekends and public holidays`() {
        every { publicHolidayRepository.findAllByHolidayDateBetween(any(), any()) } returns emptyList()
        every { applicationRepository.findByStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(any(), any(), any()) } returns emptyList()
        every { leaveTypeRepository.findAll() } returns listOf(annualType)

        val response = controller.getTeamCalendar(2026, 3)

        assertThat(response.days).hasSize(31)
        // March 1, 2026 is Sunday
        assertThat(response.days[0].date).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(response.days[0].isWeekend).isTrue()
        // March 2, 2026 is Monday
        assertThat(response.days[1].isWeekend).isFalse()
    }
}
