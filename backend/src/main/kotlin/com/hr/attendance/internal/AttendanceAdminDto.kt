package com.hr.attendance.internal

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// ---------------------------------------------------------------------------
// 1. Shifts
// ---------------------------------------------------------------------------

data class ShiftAdminDto(
    val id: UUID,
    val code: String,
    val name: String,
    val shiftType: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val breakMinutes: Int,
    val crossesMidnight: Boolean,
    val workingMinutes: Int,
    val graceInMinutes: Int,
    val graceOutMinutes: Int,
    val halfDayThresholdMinutes: Int,
    val otEligible: Boolean,
    val otStartAfterMinutes: Int,
    val minOtMinutes: Int,
    val color: String,
    val isActive: Boolean,
)

data class ShiftListResponseDto(
    val shifts: List<ShiftAdminDto>,
)

// ---------------------------------------------------------------------------
// 2. Roster & Shift Assignment
// ---------------------------------------------------------------------------

data class RosterWeekDayDto(
    val date: LocalDate,
    val dayOfWeek: Int,
    val isWeekend: Boolean,
    val isHoliday: Boolean,
    val holidayName: String? = null,
)

data class ShiftScheduleItemDto(
    val id: UUID,
    val employeeId: UUID,
    val workDate: LocalDate,
    val shiftId: UUID? = null,
    val shiftCode: String? = null,
    val shiftName: String? = null,
    val shiftColor: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val isRestDay: Boolean = false,
    val isHoliday: Boolean = false,
    val source: String = "DEFAULT_SHIFT",
)

data class RosterEmployeeItemDto(
    val id: UUID,
    val code: String,
    val name: String,
    val department: String,
)

data class RosterResponseDto(
    val days: List<RosterWeekDayDto>,
    val schedules: Map<String, List<ShiftScheduleItemDto>>,
    val employees: List<RosterEmployeeItemDto>,
)

data class AssignShiftPayloadDto(
    val employeeId: UUID,
    val workDate: LocalDate,
    val shiftId: UUID? = null,
    val isRestDay: Boolean? = false,
)

// ---------------------------------------------------------------------------
// 3. Raw Punches
// ---------------------------------------------------------------------------

data class RawPunchAdminDto(
    val id: UUID,
    val employeeId: UUID,
    val employeeCode: String,
    val employeeName: String,
    val department: String,
    val punchedAt: Instant,
    val punchType: String,
    val source: String,
    val deviceId: String? = null,
    val locationName: String? = null,
    val geofenceStatus: String = "UNKNOWN",
    val isMockLocation: Boolean = false,
    val recordedOffline: Boolean = false,
    val syncedAt: Instant,
)

data class RawPunchListResponseDto(
    val punches: List<RawPunchAdminDto>,
)

data class IngestPunchAdminPayloadDto(
    val employeeId: UUID,
    val punchedAt: Instant,
    val punchType: String,
    val source: String,
    val deviceId: String? = null,
    val locationName: String? = null,
    val isMockLocation: Boolean = false,
    val geofenceStatus: String? = null,
)

// ---------------------------------------------------------------------------
// 4. Daily Attendance & Traces
// ---------------------------------------------------------------------------

data class DailyAttendanceAdminDto(
    val id: UUID,
    val employeeId: UUID,
    val employeeCode: String,
    val employeeName: String,
    val department: String,
    val workDate: LocalDate,
    val shiftCode: String? = null,
    val shiftName: String? = null,
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
    val dayStatus: String,
    val leaveTypeName: String? = null,
    val leaveDays: Double = 0.0,
    val anomalyFlags: List<String> = emptyList(),
    val calculationTrace: String? = null,
    val computedAt: Instant,
)

data class DailyAttendanceSummaryMetricsDto(
    val totalEmployees: Int,
    val presentCount: Int,
    val halfDayCount: Int,
    val absentCount: Int,
    val restDayCount: Int,
    val holidayCount: Int,
    val anomaliesCount: Int,
    val totalOtHours: Double,
    val totalLateMinutes: Int,
)

data class DailyAttendanceListResponseDto(
    val records: List<DailyAttendanceAdminDto>,
    val summary: DailyAttendanceSummaryMetricsDto,
)

data class RecomputeAttendancePayloadDto(
    val date: LocalDate? = null,
    val employeeId: UUID? = null,
)

data class RecomputeAttendanceResponseDto(
    val recomputedCount: Int,
)

// ---------------------------------------------------------------------------
// 5. Payroll Variable Inputs Summary
// ---------------------------------------------------------------------------

data class AttendancePayrollSummaryItemDto(
    val employeeId: UUID,
    val employeeCode: String,
    val employeeName: String,
    val department: String,
    val normalOtMinutes: Int,
    val restDayOtMinutes: Int,
    val holidayOtMinutes: Int,
    val otGrossEarnings: Double,
    val lateMinutesTotal: Int,
    val latePenaltyDeduction: Double,
    val unpaidAbsenceDays: Double,
)

data class AttendancePayrollSummaryResponseDto(
    val payPeriodId: String,
    val totalNormalOtHours: Double,
    val totalRestDayOtHours: Double,
    val totalHolidayOtHours: Double,
    val totalOtEarningsAmount: Double,
    val totalLatePenaltyDeductionAmount: Double,
    val totalUnpaidAbsenceDays: Double,
    val employeeCountWithOt: Int,
    val employeeCountWithLatePenalty: Int,
    val employeeCountWithUnpaidAbsence: Int,
    val items: List<AttendancePayrollSummaryItemDto>,
)
