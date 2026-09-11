package com.hr.attendance

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Full definition of a work shift schedule template.
 */
data class ShiftDefinition(
    val id: UUID,
    val code: String,
    val name: String,
    val shiftType: ShiftType = ShiftType.FIXED,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val breakMinutes: Int = 60,
    val crossesMidnight: Boolean = false,
    val workingMinutes: Int = 480,
    val graceInMinutes: Int = 15,
    val graceOutMinutes: Int = 10,
    val halfDayThresholdMinutes: Int = 240,
    val otEligible: Boolean = true,
    val otStartAfterMinutes: Int = 480,
    val minOtMinutes: Int = 30,
    val color: String = "#3b82f6",
    val isActive: Boolean = true,
)

/**
 * Scheduled shift assignment for an employee on a single work date.
 */
data class ShiftScheduleAssignment(
    val id: UUID = UUID.randomUUID(),
    val employeeId: UUID,
    val workDate: LocalDate,
    val shiftId: UUID? = null,
    val shift: ShiftDefinition? = null,
    val isRestDay: Boolean = false,
    val isHoliday: Boolean = false,
    val holidayName: String? = null,
    val source: ScheduleSource = ScheduleSource.DEFAULT_SHIFT,
)

/**
 * Raw punch event ingested from a hardware device, mobile app, or web console.
 */
data class RawPunchInput(
    val employeeId: UUID,
    val punchedAt: Instant,
    val punchType: PunchType = PunchType.AUTO,
    val source: PunchSource = PunchSource.BIOMETRIC_DEVICE,
    val deviceId: String? = null,
    val locationId: UUID? = null,
    val geoLat: BigDecimal? = null,
    val geoLng: BigDecimal? = null,
    val geoAccuracyM: BigDecimal? = null,
    val geofenceStatus: GeofenceStatus = GeofenceStatus.UNKNOWN,
    val isMockLocation: Boolean = false,
    val clientIdempotencyKey: String? = null,
    val recordedOffline: Boolean = false,
)

/**
 * Acknowledgement of punch ingestion with duplicate detection flag.
 */
data class PunchIngestResult(
    val id: UUID,
    val employeeId: UUID,
    val punchedAt: Instant,
    val punchType: PunchType,
    val isDuplicate: Boolean,
    val statusMessage: String,
)

/**
 * Consolidated daily attendance record with worked hours, late minutes, overtime, and audit trace.
 */
data class DailyAttendanceRecord(
    val id: UUID,
    val employeeId: UUID,
    val workDate: LocalDate,
    val shiftId: UUID? = null,
    val firstInAt: Instant? = null,
    val lastOutAt: Instant? = null,
    val grossDurationMinutes: Int = 0,
    val breakMinutes: Int = 0,
    val netWorkedMinutes: Int = 0,
    val lateMinutes: Int = 0,
    val earlyLeaveMinutes: Int = 0,
    val overtimeMinutesNormal: Int = 0,
    val overtimeMinutesRestDay: Int = 0,
    val overtimeMinutesHoliday: Int = 0,
    val dayStatus: DayStatus = DayStatus.PRESENT,
    val leaveTypeId: UUID? = null,
    val leaveDays: BigDecimal = BigDecimal.ZERO,
    val anomalyFlags: List<String> = emptyList(),
    val calculationTrace: String? = null,
    val computedAt: Instant = Instant.now(),
)

/**
 * Explainability trace detailing how raw punches and schedule rules produced the daily roll-up.
 */
data class AttendanceCalculationTrace(
    val steps: List<String>,
    val traceText: String,
)

/**
 * Period roll-up of an employee's attendance metrics.
 */
data class PeriodAttendanceSummary(
    val employeeId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalPresentDays: Int,
    val totalHalfDays: Int,
    val totalAbsentDays: Int,
    val totalLeaveDays: BigDecimal,
    val totalRestDays: Int,
    val totalHolidays: Int,
    val totalWorkedMinutes: Int,
    val totalLateMinutes: Int,
    val totalEarlyLeaveMinutes: Int,
    val totalOvertimeMinutesNormal: Int,
    val totalOvertimeMinutesRestDay: Int,
    val totalOvertimeMinutesHoliday: Int,
    val records: List<DailyAttendanceRecord> = emptyList(),
)

/**
 * Overtime earning item derived from verified attendance to feed payroll earnings.
 */
data class AttendanceOvertimePayItem(
    val code: String,
    val name: String,
    val hours: BigDecimal,
    val rateMultiplier: BigDecimal,
    val amount: BigDecimal,
    val calculationTrace: String,
)

/**
 * Lateness penalty deduction derived from late arrival minutes to feed payroll deductions.
 */
data class AttendanceLatenessDeductionItem(
    val code: String,
    val name: String,
    val lateMinutes: Int,
    val amount: BigDecimal,
    val calculationTrace: String,
)

/**
 * Variable payroll inputs derived from an employee's attendance over a pay period.
 */
data class AttendancePayrollItems(
    val employeeId: UUID,
    val periodStartDate: LocalDate,
    val periodEndDate: LocalDate,
    val hourlyRate: BigDecimal,
    val overtimeEarnings: List<AttendanceOvertimePayItem> = emptyList(),
    val latenessDeductions: List<AttendanceLatenessDeductionItem> = emptyList(),
    val unpaidAbsenceDays: BigDecimal = BigDecimal.ZERO,
    val totalGrossOvertime: BigDecimal = BigDecimal.ZERO,
    val totalLatenessDeduction: BigDecimal = BigDecimal.ZERO,
)
