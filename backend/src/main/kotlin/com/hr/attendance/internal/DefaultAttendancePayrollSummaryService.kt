package com.hr.attendance.internal

import com.hr.attendance.AttendanceLatenessDeductionItem
import com.hr.attendance.AttendanceOvertimePayItem
import com.hr.attendance.AttendancePayrollItems
import com.hr.attendance.AttendancePayrollSummaryService
import com.hr.attendance.DailyAttendanceRecord
import com.hr.attendance.DayStatus
import com.hr.attendance.PeriodAttendanceSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class DefaultAttendancePayrollSummaryService(
    private val dailyAttendanceRepository: DailyAttendanceRepository,
) : AttendancePayrollSummaryService {

    override fun summarizePeriodAttendance(
        employeeId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
    ): PeriodAttendanceSummary {
        val entities = dailyAttendanceRepository
            .findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, startDate, endDate)
        val records = entities.map { it.toDto() }

        return PeriodAttendanceSummary(
            employeeId = employeeId,
            startDate = startDate,
            endDate = endDate,
            totalPresentDays = records.count { it.dayStatus == DayStatus.PRESENT },
            totalHalfDays = records.count { it.dayStatus == DayStatus.HALF_DAY },
            totalAbsentDays = records.count { it.dayStatus == DayStatus.ABSENT },
            totalLeaveDays = records.filter { it.dayStatus == DayStatus.ON_LEAVE }.sumOf { it.leaveDays },
            totalRestDays = records.count { it.dayStatus == DayStatus.REST_DAY },
            totalHolidays = records.count { it.dayStatus == DayStatus.HOLIDAY },
            totalWorkedMinutes = records.sumOf { it.netWorkedMinutes },
            totalLateMinutes = records.sumOf { it.lateMinutes },
            totalEarlyLeaveMinutes = records.sumOf { it.earlyLeaveMinutes },
            totalOvertimeMinutesNormal = records.sumOf { it.overtimeMinutesNormal },
            totalOvertimeMinutesRestDay = records.sumOf { it.overtimeMinutesRestDay },
            totalOvertimeMinutesHoliday = records.sumOf { it.overtimeMinutesHoliday },
            records = records,
        )
    }

    override fun computePayrollItems(
        employeeId: UUID,
        periodStartDate: LocalDate,
        periodEndDate: LocalDate,
        basicSalary: BigDecimal,
        standardWorkingDays: BigDecimal,
        standardHoursPerDay: BigDecimal,
    ): AttendancePayrollItems {
        val summary = summarizePeriodAttendance(employeeId, periodStartDate, periodEndDate)

        // Derive base hourly rate = basicSalary / (standardWorkingDays * standardHoursPerDay)
        val totalStandardHours = standardWorkingDays.multiply(standardHoursPerDay)
        val hourlyRate = if (totalStandardHours > BigDecimal.ZERO) {
            basicSalary.divide(totalStandardHours, 4, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val overtimeEarnings = mutableListOf<AttendanceOvertimePayItem>()

        // 1. Normal Overtime (Weekday 1.50x)
        if (summary.totalOvertimeMinutesNormal > 0) {
            val hours = BigDecimal(summary.totalOvertimeMinutesNormal)
                .divide(BigDecimal("60"), 2, RoundingMode.HALF_UP)
            val multiplier = BigDecimal("1.50")
            val amount = hours.multiply(hourlyRate).multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
            val trace = "Weekday Overtime: ${summary.totalOvertimeMinutesNormal}m ($hours hrs) × Hourly LKR ${hourlyRate.setScale(2, RoundingMode.HALF_UP)} × 1.50 = LKR $amount"
            overtimeEarnings.add(
                AttendanceOvertimePayItem(
                    code = "OT_NORMAL",
                    name = "Normal Overtime (1.5x)",
                    hours = hours,
                    rateMultiplier = multiplier,
                    amount = amount,
                    calculationTrace = trace,
                ),
            )
        }

        // 2. Rest Day Overtime (Weekend 2.00x)
        if (summary.totalOvertimeMinutesRestDay > 0) {
            val hours = BigDecimal(summary.totalOvertimeMinutesRestDay)
                .divide(BigDecimal("60"), 2, RoundingMode.HALF_UP)
            val multiplier = BigDecimal("2.00")
            val amount = hours.multiply(hourlyRate).multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
            val trace = "Rest Day Overtime: ${summary.totalOvertimeMinutesRestDay}m ($hours hrs) × Hourly LKR ${hourlyRate.setScale(2, RoundingMode.HALF_UP)} × 2.00 = LKR $amount"
            overtimeEarnings.add(
                AttendanceOvertimePayItem(
                    code = "OT_REST_DAY",
                    name = "Rest Day Overtime (2.0x)",
                    hours = hours,
                    rateMultiplier = multiplier,
                    amount = amount,
                    calculationTrace = trace,
                ),
            )
        }

        // 3. Public Holiday Overtime (Holiday 2.50x)
        if (summary.totalOvertimeMinutesHoliday > 0) {
            val hours = BigDecimal(summary.totalOvertimeMinutesHoliday)
                .divide(BigDecimal("60"), 2, RoundingMode.HALF_UP)
            val multiplier = BigDecimal("2.50")
            val amount = hours.multiply(hourlyRate).multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
            val trace = "Public Holiday Overtime: ${summary.totalOvertimeMinutesHoliday}m ($hours hrs) × Hourly LKR ${hourlyRate.setScale(2, RoundingMode.HALF_UP)} × 2.50 = LKR $amount"
            overtimeEarnings.add(
                AttendanceOvertimePayItem(
                    code = "OT_HOLIDAY",
                    name = "Holiday Overtime (2.5x)",
                    hours = hours,
                    rateMultiplier = multiplier,
                    amount = amount,
                    calculationTrace = trace,
                ),
            )
        }

        // 4. Lateness Penalty Deduction
        val latenessDeductions = mutableListOf<AttendanceLatenessDeductionItem>()
        if (summary.totalLateMinutes > 0) {
            val lateHours = BigDecimal(summary.totalLateMinutes)
                .divide(BigDecimal("60"), 4, RoundingMode.HALF_UP)
            val amount = lateHours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP)
            val trace = "Lateness Penalty: Cumulative ${summary.totalLateMinutes} late minutes ($lateHours hrs) × Hourly LKR ${hourlyRate.setScale(2, RoundingMode.HALF_UP)} = LKR $amount"
            latenessDeductions.add(
                AttendanceLatenessDeductionItem(
                    code = "LATE_PENALTY",
                    name = "Lateness Penalty Deduction",
                    lateMinutes = summary.totalLateMinutes,
                    amount = amount,
                    calculationTrace = trace,
                ),
            )
        }

        // 5. Unapproved Absence Days
        // Full absent days count as 1.00; half days count as 0.50
        val halfDayAbsence = BigDecimal(summary.totalHalfDays).multiply(BigDecimal("0.50"))
        val unpaidAbsenceDays = BigDecimal(summary.totalAbsentDays).add(halfDayAbsence).setScale(2, RoundingMode.HALF_UP)

        val totalGrossOvertime = overtimeEarnings.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }
        val totalLatenessDeduction = latenessDeductions.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }

        return AttendancePayrollItems(
            employeeId = employeeId,
            periodStartDate = periodStartDate,
            periodEndDate = periodEndDate,
            hourlyRate = hourlyRate.setScale(2, RoundingMode.HALF_UP),
            overtimeEarnings = overtimeEarnings,
            latenessDeductions = latenessDeductions,
            unpaidAbsenceDays = unpaidAbsenceDays,
            totalGrossOvertime = totalGrossOvertime,
            totalLatenessDeduction = totalLatenessDeduction,
        )
    }

    private fun DailyAttendance.toDto() = DailyAttendanceRecord(
        id = id ?: UUID.randomUUID(),
        employeeId = employeeId,
        workDate = workDate,
        shiftId = shiftId,
        firstInAt = firstInAt,
        lastOutAt = lastOutAt,
        grossDurationMinutes = grossDurationMinutes,
        breakMinutes = breakMinutes,
        netWorkedMinutes = netWorkedMinutes,
        lateMinutes = lateMinutes,
        earlyLeaveMinutes = earlyLeaveMinutes,
        overtimeMinutesNormal = overtimeMinutesNormal,
        overtimeMinutesRestDay = overtimeMinutesRestDay,
        overtimeMinutesHoliday = overtimeMinutesHoliday,
        dayStatus = dayStatus,
        leaveTypeId = leaveTypeId,
        leaveDays = leaveDays,
        anomalyFlags = if (anomalyFlags.isBlank()) emptyList() else anomalyFlags.split(","),
        calculationTrace = calculationTrace,
        computedAt = computedAt,
    )
}
