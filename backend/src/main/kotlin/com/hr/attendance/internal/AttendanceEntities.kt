package com.hr.attendance.internal

import com.hr.attendance.DayStatus
import com.hr.attendance.GeofenceEnforcementPolicy
import com.hr.attendance.GeofenceStatus
import com.hr.attendance.LatenessPenaltyTier
import com.hr.attendance.LocationCapturePolicy
import com.hr.attendance.MockLocationAction
import com.hr.attendance.PunchSource
import com.hr.attendance.PunchType
import com.hr.attendance.ScheduleSource
import com.hr.attendance.ShiftType
import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "shift")
class Shift(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", nullable = false, length = 32)
    var shiftType: ShiftType = ShiftType.FIXED,
    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime,
    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime,
    @Column(name = "break_minutes", nullable = false)
    var breakMinutes: Int = 60,
    @Column(name = "crosses_midnight", nullable = false)
    var crossesMidnight: Boolean = false,
    @Column(name = "working_minutes", nullable = false)
    var workingMinutes: Int = 480,
    @Column(name = "grace_in_minutes", nullable = false)
    var graceInMinutes: Int = 15,
    @Column(name = "grace_out_minutes", nullable = false)
    var graceOutMinutes: Int = 10,
    @Column(name = "half_day_threshold_minutes", nullable = false)
    var halfDayThresholdMinutes: Int = 240,
    @Column(name = "ot_eligible", nullable = false)
    var otEligible: Boolean = true,
    @Column(name = "ot_start_after_minutes", nullable = false)
    var otStartAfterMinutes: Int = 480,
    @Column(name = "min_ot_minutes", nullable = false)
    var minOtMinutes: Int = 30,
    @Column(name = "color", nullable = false, length = 32)
    var color: String = "#3b82f6",
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "attendance_policy")
class AttendancePolicy(
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "location_capture", nullable = false, length = 32)
    var locationCapture: LocationCapturePolicy = LocationCapturePolicy.OPTIONAL,
    @Enumerated(EnumType.STRING)
    @Column(name = "geofence_enforcement", nullable = false, length = 32)
    var geofenceEnforcement: GeofenceEnforcementPolicy = GeofenceEnforcementPolicy.WARN,
    @Enumerated(EnumType.STRING)
    @Column(name = "mock_location_action", nullable = false, length = 32)
    var mockLocationAction: MockLocationAction = MockLocationAction.FLAG,
    @Column(name = "allow_offline_punch", nullable = false)
    var allowOfflinePunch: Boolean = true,
    @Column(name = "max_offline_hours", nullable = false)
    var maxOfflineHours: Int = 72,
    @Enumerated(EnumType.STRING)
    @Column(name = "lateness_penalty_tier", nullable = false, length = 32)
    var latenessPenaltyTier: LatenessPenaltyTier = LatenessPenaltyTier.PER_MINUTE,
    @Column(name = "overtime_weekday_multiplier", nullable = false, precision = 4, scale = 2)
    var overtimeWeekdayMultiplier: BigDecimal = BigDecimal("1.50"),
    @Column(name = "overtime_restday_multiplier", nullable = false, precision = 4, scale = 2)
    var overtimeRestdayMultiplier: BigDecimal = BigDecimal("2.00"),
    @Column(name = "overtime_holiday_multiplier", nullable = false, precision = 4, scale = 2)
    var overtimeHolidayMultiplier: BigDecimal = BigDecimal("2.50"),
) : TenantScopedEntity()

@Entity
@Table(name = "employee_shift_schedule")
class EmployeeShiftSchedule(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "work_date", nullable = false)
    var workDate: LocalDate,
    @Column(name = "shift_id")
    var shiftId: UUID? = null,
    @Column(name = "is_rest_day", nullable = false)
    var isRestDay: Boolean = false,
    @Column(name = "is_holiday", nullable = false)
    var isHoliday: Boolean = false,
    @Column(name = "holiday_name", length = 128)
    var holidayName: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    var source: ScheduleSource = ScheduleSource.DEFAULT_SHIFT,
) : TenantScopedEntity()

@Entity
@Table(name = "raw_punch")
class RawPunch(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "punched_at", nullable = false)
    var punchedAt: Instant,
    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type", nullable = false, length = 32)
    var punchType: PunchType = PunchType.AUTO,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    var source: PunchSource = PunchSource.BIOMETRIC_DEVICE,
    @Column(name = "device_id", length = 64)
    var deviceId: String? = null,
    @Column(name = "location_id")
    var locationId: UUID? = null,
    @Column(name = "geo_lat", precision = 10, scale = 7)
    var geoLat: BigDecimal? = null,
    @Column(name = "geo_lng", precision = 10, scale = 7)
    var geoLng: BigDecimal? = null,
    @Column(name = "geo_accuracy_m", precision = 8, scale = 2)
    var geoAccuracyM: BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "geofence_status", nullable = false, length = 32)
    var geofenceStatus: GeofenceStatus = GeofenceStatus.UNKNOWN,
    @Column(name = "is_mock_location", nullable = false)
    var isMockLocation: Boolean = false,
    @Column(name = "client_idempotency_key", length = 128)
    var clientIdempotencyKey: String? = null,
    @Column(name = "recorded_offline", nullable = false)
    var recordedOffline: Boolean = false,
    @Column(name = "synced_at", nullable = false)
    var syncedAt: Instant = Instant.now(),
) : TenantScopedEntity()

@Entity
@Table(name = "daily_attendance")
class DailyAttendance(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "work_date", nullable = false)
    var workDate: LocalDate,
    @Column(name = "shift_id")
    var shiftId: UUID? = null,
    @Column(name = "first_in_at")
    var firstInAt: Instant? = null,
    @Column(name = "last_out_at")
    var lastOutAt: Instant? = null,
    @Column(name = "gross_duration_minutes", nullable = false)
    var grossDurationMinutes: Int = 0,
    @Column(name = "break_minutes", nullable = false)
    var breakMinutes: Int = 0,
    @Column(name = "net_worked_minutes", nullable = false)
    var netWorkedMinutes: Int = 0,
    @Column(name = "late_minutes", nullable = false)
    var lateMinutes: Int = 0,
    @Column(name = "early_leave_minutes", nullable = false)
    var earlyLeaveMinutes: Int = 0,
    @Column(name = "overtime_minutes_normal", nullable = false)
    var overtimeMinutesNormal: Int = 0,
    @Column(name = "overtime_minutes_rest_day", nullable = false)
    var overtimeMinutesRestDay: Int = 0,
    @Column(name = "overtime_minutes_holiday", nullable = false)
    var overtimeMinutesHoliday: Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "day_status", nullable = false, length = 32)
    var dayStatus: DayStatus = DayStatus.PRESENT,
    @Column(name = "leave_type_id")
    var leaveTypeId: UUID? = null,
    @Column(name = "leave_days", nullable = false, precision = 4, scale = 2)
    var leaveDays: BigDecimal = BigDecimal.ZERO,
    @Column(name = "anomaly_flags", nullable = false)
    var anomalyFlags: String = "",
    @Column(name = "calculation_trace")
    var calculationTrace: String? = null,
    @Column(name = "computed_at", nullable = false)
    var computedAt: Instant = Instant.now(),
) : TenantScopedEntity()
