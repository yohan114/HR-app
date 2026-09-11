package com.hr.attendance

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Service managing shift definition templates, roster patterns, and employee daily schedules.
 */
interface ShiftRosterService {
    fun createShift(shift: ShiftDefinition): ShiftDefinition
    fun getShift(id: UUID): ShiftDefinition?
    fun listShifts(): List<ShiftDefinition>
    fun assignSchedule(assignment: ShiftScheduleAssignment): ShiftScheduleAssignment
    fun getSchedule(employeeId: UUID, date: LocalDate): ShiftScheduleAssignment?
    fun getSchedules(employeeId: UUID, startDate: LocalDate, endDate: LocalDate): List<ShiftScheduleAssignment>
    fun generateDefaultSchedules(
        employeeIds: List<UUID>,
        defaultShiftId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Int
}

/**
 * Service managing ingestion and deduplication of clock-in and clock-out punches.
 */
interface PunchIngestionService {
    fun ingestPunch(input: RawPunchInput): PunchIngestResult
    fun ingestBatch(inputs: List<RawPunchInput>): List<PunchIngestResult>
    fun getPunches(employeeId: UUID, startDate: LocalDate, endDate: LocalDate): List<RawPunchInput>
}

/**
 * Core attendance calculation engine computing daily roll-ups, grace period applications,
 * worked minutes, overtime hours, and transparent mathematical explainability traces.
 */
interface AttendanceProcessorService {
    fun computeDayAttendance(employeeId: UUID, workDate: LocalDate): DailyAttendanceRecord
    fun recomputePeriodAttendance(employeeId: UUID, startDate: LocalDate, endDate: LocalDate): List<DailyAttendanceRecord>
    fun getAttendanceRecord(employeeId: UUID, workDate: LocalDate): DailyAttendanceRecord?
    fun getAttendanceRecords(employeeId: UUID, startDate: LocalDate, endDate: LocalDate): List<DailyAttendanceRecord>
}

/**
 * Service aggregating attendance records over a pay period to produce overtime earnings,
 * lateness penalty deductions, and unapproved absence days to feed the payroll pipeline.
 */
interface AttendancePayrollSummaryService {
    fun summarizePeriodAttendance(
        employeeId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
    ): PeriodAttendanceSummary

    fun computePayrollItems(
        employeeId: UUID,
        periodStartDate: LocalDate,
        periodEndDate: LocalDate,
        basicSalary: BigDecimal,
        standardWorkingDays: BigDecimal = BigDecimal("22.00"),
        standardHoursPerDay: BigDecimal = BigDecimal("8.00"),
    ): AttendancePayrollItems
}
