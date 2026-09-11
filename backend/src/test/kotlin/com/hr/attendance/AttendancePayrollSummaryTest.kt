package com.hr.attendance

import com.hr.attendance.internal.DailyAttendance
import com.hr.attendance.internal.DailyAttendanceRepository
import com.hr.attendance.internal.DefaultAttendancePayrollSummaryService
import com.hr.payroll.AllowanceInput
import com.hr.payroll.DeductionInput
import com.hr.payroll.EmployeePayrollProfile
import com.hr.payroll.GrossToNetInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Attendance to Payroll Integration Pipeline")
class AttendancePayrollSummaryTest {

    private lateinit var dailyAttendanceRepository: DailyAttendanceRepository
    private lateinit var summaryService: DefaultAttendancePayrollSummaryService

    private val employeeId = UUID.randomUUID()
    private val periodStart = LocalDate.of(2026, 3, 1)
    private val periodEnd = LocalDate.of(2026, 3, 31)

    @BeforeEach
    fun setUp() {
        dailyAttendanceRepository = mock(DailyAttendanceRepository::class.java)
        summaryService = DefaultAttendancePayrollSummaryService(dailyAttendanceRepository)
    }

    @Test
    fun `aggregates period attendance and computes overtime allowances and lateness deduction`() {
        // Seed 4 days:
        // Day 1: Present with 120m normal OT (2.0 hrs)
        // Day 2: Present with 45m late arrival
        // Day 3: Rest day worked with 240m rest day OT (4.0 hrs)
        // Day 4: Absent (unapproved absence)
        val day1 = DailyAttendance(
            employeeId = employeeId,
            workDate = LocalDate.of(2026, 3, 2),
            netWorkedMinutes = 600,
            overtimeMinutesNormal = 120,
            dayStatus = DayStatus.PRESENT,
        )
        val day2 = DailyAttendance(
            employeeId = employeeId,
            workDate = LocalDate.of(2026, 3, 3),
            netWorkedMinutes = 450,
            lateMinutes = 45,
            dayStatus = DayStatus.PRESENT,
        )
        val day3 = DailyAttendance(
            employeeId = employeeId,
            workDate = LocalDate.of(2026, 3, 7),
            netWorkedMinutes = 240,
            overtimeMinutesRestDay = 240,
            dayStatus = DayStatus.REST_DAY,
        )
        val day4 = DailyAttendance(
            employeeId = employeeId,
            workDate = LocalDate.of(2026, 3, 9),
            netWorkedMinutes = 0,
            dayStatus = DayStatus.ABSENT,
        )

        `when`(dailyAttendanceRepository.findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, periodStart, periodEnd))
            .thenReturn(listOf(day1, day2, day3, day4))

        val basicSalary = BigDecimal("200000.00")
        val standardDays = BigDecimal("22.00")
        val standardHours = BigDecimal("8.00")
        // Standard hours = 22 * 8 = 176
        // Hourly rate = 200,000 / 176 = 1136.3636

        val payrollItems = summaryService.computePayrollItems(
            employeeId = employeeId,
            periodStartDate = periodStart,
            periodEndDate = periodEnd,
            basicSalary = basicSalary,
            standardWorkingDays = standardDays,
            standardHoursPerDay = standardHours,
        )

        assertThat(payrollItems.hourlyRate).isEqualByComparingTo(BigDecimal("1136.36"))

        // Normal OT: 2.0 hrs * 1136.3636 * 1.50 = 3409.09 LKR
        val normalOt = payrollItems.overtimeEarnings.find { it.code == "OT_NORMAL" }
        assertThat(normalOt).isNotNull
        assertThat(normalOt!!.hours).isEqualByComparingTo(BigDecimal("2.00"))
        assertThat(normalOt.amount).isEqualByComparingTo(BigDecimal("3409.09"))

        // Rest Day OT: 4.0 hrs * 1136.3636 * 2.00 = 9090.91 LKR
        val restDayOt = payrollItems.overtimeEarnings.find { it.code == "OT_REST_DAY" }
        assertThat(restDayOt).isNotNull
        assertThat(restDayOt!!.hours).isEqualByComparingTo(BigDecimal("4.00"))
        assertThat(restDayOt.amount).isEqualByComparingTo(BigDecimal("9090.91"))

        // Lateness Penalty: 45 minutes (0.75 hrs) * 1136.3636 = 852.27 LKR
        val lateness = payrollItems.latenessDeductions.find { it.code == "LATE_PENALTY" }
        assertThat(lateness).isNotNull
        assertThat(lateness!!.lateMinutes).isEqualTo(45)
        assertThat(lateness.amount).isEqualByComparingTo(BigDecimal("852.27"))

        // Unapproved Absence Days: 1 absent day
        assertThat(payrollItems.unpaidAbsenceDays).isEqualByComparingTo(BigDecimal("1.00"))

        // Feeds directly into GrossToNetInput:
        val allowances: List<AllowanceInput> = payrollItems.overtimeEarnings.map {
            AllowanceInput(
                code = it.code,
                name = it.name,
                amount = it.amount,
                isTaxable = true,
                isStatutoryBase = true,
            )
        }
        val deductions: List<DeductionInput> = payrollItems.latenessDeductions.map {
            DeductionInput(
                code = it.code,
                name = it.name,
                amount = it.amount,
                isPreTax = true,
            )
        }

        val profile = EmployeePayrollProfile(
            id = employeeId,
            employeeCode = "E004",
            displayName = "Kasun Fernando",
            countryCode = "LK",
            currency = "LKR",
            joinDate = LocalDate.of(2023, 1, 1),
        )

        val grossToNetInput = GrossToNetInput(
            employee = profile,
            payPeriodId = UUID.randomUUID(),
            periodStartDate = periodStart,
            periodEndDate = periodEnd,
            basicSalary = basicSalary,
            allowances = allowances,
            deductions = deductions,
            workingDaysInMonth = standardDays,
            unpaidLeaveDays = payrollItems.unpaidAbsenceDays,
        )

        assertThat(grossToNetInput.allowances).hasSize(2)
        assertThat(grossToNetInput.deductions).hasSize(1)
        assertThat(grossToNetInput.unpaidLeaveDays).isEqualByComparingTo(BigDecimal("1.00"))
    }
}
