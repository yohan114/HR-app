package com.hr.attendance.internal

import com.hr.attendance.AttendanceProcessorService
import com.hr.attendance.DayStatus
import com.hr.attendance.GeofenceStatus
import com.hr.attendance.PunchIngestionService
import com.hr.attendance.PunchSource
import com.hr.attendance.PunchType
import com.hr.attendance.RawPunchInput
import com.hr.attendance.ShiftRosterService
import com.hr.attendance.ShiftType
import com.hr.employee.EmployeeLookupService
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.NotFoundException
import com.hr.tenancy.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Service
class AttendanceAdminService(
    private val shiftRepository: ShiftRepository,
    private val employeeShiftScheduleRepository: EmployeeShiftScheduleRepository,
    private val rawPunchRepository: RawPunchRepository,
    private val dailyAttendanceRepository: DailyAttendanceRepository,
    private val shiftRosterService: ShiftRosterService,
    private val punchIngestionService: PunchIngestionService,
    private val attendanceProcessorService: AttendanceProcessorService,
    private val employeeLookupService: EmployeeLookupService,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ---------------------------------------------------------------------------
    // 1. Shifts
    // ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listShifts(): List<ShiftAdminDto> {
        val shifts = shiftRepository.findAll()
        if (shifts.isEmpty()) {
            return listOf(
                ShiftAdminDto(
                    id = MobileAttendanceController.DEFAULT_SHIFT.id,
                    code = MobileAttendanceController.DEFAULT_SHIFT.code,
                    name = MobileAttendanceController.DEFAULT_SHIFT.name,
                    shiftType = "FIXED",
                    startTime = MobileAttendanceController.DEFAULT_SHIFT.startTime,
                    endTime = MobileAttendanceController.DEFAULT_SHIFT.endTime,
                    breakMinutes = MobileAttendanceController.DEFAULT_SHIFT.breakMinutes,
                    crossesMidnight = false,
                    workingMinutes = MobileAttendanceController.DEFAULT_SHIFT.workingMinutes,
                    graceInMinutes = MobileAttendanceController.DEFAULT_SHIFT.graceInMinutes,
                    graceOutMinutes = MobileAttendanceController.DEFAULT_SHIFT.graceOutMinutes,
                    halfDayThresholdMinutes = 240,
                    otEligible = true,
                    otStartAfterMinutes = 480,
                    minOtMinutes = 30,
                    color = "#3b82f6",
                    isActive = true,
                )
            )
        }
        return shifts.map { s ->
            ShiftAdminDto(
                id = s.id,
                code = s.code,
                name = s.name,
                shiftType = s.shiftType.name,
                startTime = s.startTime,
                endTime = s.endTime,
                breakMinutes = s.breakMinutes,
                crossesMidnight = s.crossesMidnight,
                workingMinutes = s.workingMinutes,
                graceInMinutes = s.graceInMinutes,
                graceOutMinutes = s.graceOutMinutes,
                halfDayThresholdMinutes = s.halfDayThresholdMinutes,
                otEligible = s.otEligible,
                otStartAfterMinutes = s.otStartAfterMinutes,
                minOtMinutes = s.minOtMinutes,
                color = s.color,
                isActive = s.isActive,
            )
        }
    }

    // ---------------------------------------------------------------------------
    // 2. Roster & Shift Assignment
    // ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun getRoster(startDate: LocalDate?, endDate: LocalDate?, department: String?): RosterResponseDto {
        val tenantId = TenantContext.currentId()
        val today = LocalDate.now()
        val start = startDate ?: today.with(DayOfWeek.MONDAY)
        val end = endDate ?: start.plusDays(6)

        // 1. Build calendar days
        val days = mutableListOf<RosterWeekDayDto>()
        var curr = start
        while (!curr.isAfter(end)) {
            val isWeekend = curr.dayOfWeek.value >= 6
            days.add(
                RosterWeekDayDto(
                    date = curr,
                    dayOfWeek = curr.dayOfWeek.value,
                    isWeekend = isWeekend,
                    isHoliday = false,
                    holidayName = null,
                )
            )
            curr = curr.plusDays(1)
        }

        // 2. Fetch employees
        val empSql = StringBuilder(
            """
            SELECT e.id, e.employee_code, e.display_name, d.name as dept_name
            FROM employee e
            LEFT JOIN department d ON d.id = e.department_id
            WHERE e.tenant_id = ? AND e.status = 'ACTIVE'
            """
        )
        val empArgs = mutableListOf<Any>(tenantId)
        if (!department.isNullOrBlank() && department != "ALL") {
            empSql.append(" AND d.name ILIKE ?")
            empArgs.add("%$department%")
        }
        empSql.append(" ORDER BY e.employee_code ASC")

        val employees = jdbc.query(empSql.toString(), { rs, _ ->
            RosterEmployeeItemDto(
                id = rs.getObject("id", UUID::class.java),
                code = rs.getString("employee_code"),
                name = rs.getString("display_name"),
                department = rs.getString("dept_name") ?: "General",
            )
        }, *empArgs.toTypedArray())

        // 3. Fetch schedules for date range
        val schedSql =
            """
            SELECT s.id, s.employee_id, s.work_date, s.shift_id, s.is_rest_day, s.is_holiday, s.source,
                   sh.code as shift_code, sh.name as shift_name, sh.color as shift_color,
                   sh.start_time, sh.end_time
            FROM employee_shift_schedule s
            LEFT JOIN shift sh ON sh.id = s.shift_id
            WHERE s.tenant_id = ? AND s.work_date BETWEEN ? AND ?
            """
        val schedulesList = jdbc.query(schedSql, { rs, _ ->
            val isRest = rs.getBoolean("is_rest_day")
            val startTimeVal = rs.getTime("start_time")?.toLocalTime()?.toString()
            val endTimeVal = rs.getTime("end_time")?.toLocalTime()?.toString()
            ShiftScheduleItemDto(
                id = rs.getObject("id", UUID::class.java),
                employeeId = rs.getObject("employee_id", UUID::class.java),
                workDate = rs.getDate("work_date").toLocalDate(),
                shiftId = rs.getObject("shift_id", UUID::class.java),
                shiftCode = if (isRest) "REST" else (rs.getString("shift_code") ?: "GEN_0830"),
                shiftName = if (isRest) "Rest Day" else (rs.getString("shift_name") ?: "General Day"),
                shiftColor = if (isRest) "#94a3b8" else (rs.getString("shift_color") ?: "#3b82f6"),
                startTime = if (isRest) "--:--" else (startTimeVal ?: "08:30"),
                endTime = if (isRest) "--:--" else (endTimeVal ?: "17:30"),
                isRestDay = isRest,
                isHoliday = rs.getBoolean("is_holiday"),
                source = rs.getString("source") ?: "DEFAULT_SHIFT",
            )
        }, tenantId, start, end)

        val scheduleMap = schedulesList.groupBy { it.employeeId.toString() }

        return RosterResponseDto(
            days = days,
            schedules = scheduleMap,
            employees = employees,
        )
    }

    @Transactional
    fun assignShift(payload: AssignShiftPayloadDto): ShiftScheduleItemDto {
        val tenantId = TenantContext.currentId()
        val id = UUID.randomUUID()
        val isRest = payload.isRestDay ?: false

        val actualShiftId = if (isRest) null else payload.shiftId

        if (actualShiftId != null && actualShiftId == MobileAttendanceController.DEFAULT_SHIFT.id) {
            jdbc.update(
                """
                INSERT INTO shift (id, tenant_id, code, name, shift_type, start_time, end_time, break_minutes, working_minutes, grace_in_minutes, grace_out_minutes, half_day_threshold_minutes, ot_eligible, ot_start_after_minutes, min_ot_minutes, color, is_active, created_at, updated_at, version)
                VALUES (?, ?, 'GEN_0830', 'General Day (08:30 - 17:30)', 'FIXED', '08:30:00', '17:30:00', 60, 480, 15, 10, 240, true, 480, 30, '#3b82f6', true, now(), now(), 0)
                ON CONFLICT (tenant_id, code) DO UPDATE SET name = EXCLUDED.name
                """,
                actualShiftId,
                tenantId,
            )
        }

        jdbc.update(
            """
            INSERT INTO employee_shift_schedule (id, tenant_id, employee_id, work_date, shift_id, is_rest_day, source, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, 'MANUAL', now(), now())
            ON CONFLICT (tenant_id, employee_id, work_date)
            DO UPDATE SET shift_id = EXCLUDED.shift_id, is_rest_day = EXCLUDED.is_rest_day, source = 'MANUAL', updated_at = now()
            """,
            id,
            tenantId,
            payload.employeeId,
            payload.workDate,
            actualShiftId,
            isRest,
        )

        val shift = actualShiftId?.let { shiftRepository.findById(it).orElse(null) }

        return ShiftScheduleItemDto(
            id = id,
            employeeId = payload.employeeId,
            workDate = payload.workDate,
            shiftId = actualShiftId,
            shiftCode = if (isRest) "REST" else (shift?.code ?: "CUSTOM"),
            shiftName = if (isRest) "Rest Day" else (shift?.name ?: "Custom Shift"),
            shiftColor = if (isRest) "#94a3b8" else (shift?.color ?: "#3b82f6"),
            startTime = if (isRest) "--:--" else (shift?.startTime?.toString() ?: "08:30"),
            endTime = if (isRest) "--:--" else (shift?.endTime?.toString() ?: "17:30"),
            isRestDay = isRest,
            isHoliday = false,
            source = "MANUAL",
        )
    }

    // ---------------------------------------------------------------------------
    // 3. Raw Punches
    // ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listPunches(date: LocalDate?, employeeId: UUID?, flag: String?, source: String?): List<RawPunchAdminDto> {
        val tenantId = TenantContext.currentId()
        val targetDate = date ?: LocalDate.now()

        val sql = StringBuilder(
            """
            SELECT p.id, p.employee_id, p.punched_at, p.punch_type, p.source, p.device_id,
                   p.geofence_status, p.is_mock_location, p.recorded_offline, p.synced_at,
                   e.employee_code, e.display_name, d.name as dept_name
            FROM raw_punch p
            JOIN employee e ON e.id = p.employee_id
            LEFT JOIN department d ON d.id = e.department_id
            WHERE p.tenant_id = ? AND (p.punched_at AT TIME ZONE 'UTC')::date = ?
            """
        )
        val args = mutableListOf<Any>(tenantId, targetDate)

        if (employeeId != null) {
            sql.append(" AND p.employee_id = ?")
            args.add(employeeId)
        }
        if (!source.isNullOrBlank() && source != "ALL") {
            sql.append(" AND p.source = ?")
            args.add(source.uppercase())
        }
        if (!flag.isNullOrBlank() && flag != "ALL") {
            when (flag.uppercase()) {
                "MOCK_GPS" -> sql.append(" AND p.is_mock_location = true")
                "OUTSIDE_GEOFENCE" -> sql.append(" AND p.geofence_status = 'OUTSIDE'")
                "OFFLINE" -> sql.append(" AND p.recorded_offline = true")
            }
        }

        sql.append(" ORDER BY p.punched_at DESC")

        return jdbc.query(sql.toString(), { rs, _ ->
            RawPunchAdminDto(
                id = rs.getObject("id", UUID::class.java),
                employeeId = rs.getObject("employee_id", UUID::class.java),
                employeeCode = rs.getString("employee_code"),
                employeeName = rs.getString("display_name"),
                department = rs.getString("dept_name") ?: "General",
                punchedAt = rs.getTimestamp("punched_at").toInstant(),
                punchType = rs.getString("punch_type"),
                source = rs.getString("source"),
                deviceId = rs.getString("device_id"),
                locationName = if (rs.getString("source") == "MOBILE_APP") "Mobile Geo-Location" else "Biometric Terminal",
                geofenceStatus = rs.getString("geofence_status") ?: "UNKNOWN",
                isMockLocation = rs.getBoolean("is_mock_location"),
                recordedOffline = rs.getBoolean("recorded_offline"),
                syncedAt = rs.getTimestamp("synced_at").toInstant(),
            )
        }, *args.toTypedArray())
    }

    @Transactional
    fun ingestPunch(payload: IngestPunchAdminPayloadDto): RawPunchAdminDto {
        val punchTypeEnum = runCatching { PunchType.valueOf(payload.punchType.uppercase()) }.getOrDefault(PunchType.IN)
        val punchSourceEnum = runCatching { PunchSource.valueOf(payload.source.uppercase()) }.getOrDefault(PunchSource.WEB_PORTAL)
        val geofenceStatus = payload.geofenceStatus?.let { runCatching { GeofenceStatus.valueOf(it.uppercase()) }.getOrNull() } ?: GeofenceStatus.INSIDE

        val result = punchIngestionService.ingestPunch(
            RawPunchInput(
                employeeId = payload.employeeId,
                punchedAt = payload.punchedAt,
                punchType = punchTypeEnum,
                source = punchSourceEnum,
                deviceId = payload.deviceId ?: "WEB-ADMIN-CONSOLE",
                locationId = null,
                geoLat = null,
                geoLng = null,
                geoAccuracyM = null,
                geofenceStatus = geofenceStatus,
                isMockLocation = payload.isMockLocation,
                clientIdempotencyKey = UUID.randomUUID().toString(),
                recordedOffline = false,
            )
        )

        val emp = employeeLookupService.findById(payload.employeeId)
        return RawPunchAdminDto(
            id = result.id,
            employeeId = payload.employeeId,
            employeeCode = emp?.employeeCode ?: "EMP",
            employeeName = emp?.displayName ?: "Employee",
            department = "Operations",
            punchedAt = result.punchedAt,
            punchType = result.punchType.name,
            source = payload.source,
            deviceId = payload.deviceId,
            locationName = payload.locationName ?: "Web Admin Console",
            geofenceStatus = geofenceStatus.name,
            isMockLocation = payload.isMockLocation,
            recordedOffline = false,
            syncedAt = Instant.now(),
        )
    }

    // ---------------------------------------------------------------------------
    // 4. Daily Attendance & Traces
    // ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listDailyAttendance(
        date: LocalDate?,
        department: String?,
        status: String?,
        anomaly: String?,
    ): DailyAttendanceListResponseDto {
        val tenantId = TenantContext.currentId()
        val targetDate = date ?: LocalDate.now()

        val sql = StringBuilder(
            """
            SELECT da.id, da.employee_id, da.work_date, da.shift_id, da.first_in_at, da.last_out_at,
                   da.gross_duration_minutes, da.break_minutes, da.net_worked_minutes,
                   da.late_minutes, da.early_leave_minutes, da.overtime_minutes_normal,
                   da.overtime_minutes_rest_day, da.overtime_minutes_holiday, da.day_status,
                   da.leave_days, da.anomaly_flags, da.calculation_trace, da.computed_at,
                   e.employee_code, e.display_name, d.name as dept_name,
                   sh.code as shift_code, sh.name as shift_name
            FROM daily_attendance da
            JOIN employee e ON e.id = da.employee_id
            LEFT JOIN department d ON d.id = e.department_id
            LEFT JOIN shift sh ON sh.id = da.shift_id
            WHERE da.tenant_id = ? AND da.work_date = ?
            """
        )
        val args = mutableListOf<Any>(tenantId, targetDate)

        if (!department.isNullOrBlank() && department != "ALL") {
            sql.append(" AND d.name ILIKE ?")
            args.add("%$department%")
        }
        if (!status.isNullOrBlank() && status != "ALL") {
            sql.append(" AND da.day_status = ?")
            args.add(status.uppercase())
        }
        if (!anomaly.isNullOrBlank() && anomaly != "ALL") {
            sql.append(" AND da.anomaly_flags ILIKE ?")
            args.add("%$anomaly%")
        }

        sql.append(" ORDER BY e.employee_code ASC")

        val records = jdbc.query(sql.toString(), { rs, _ ->
            val flagsStr = rs.getString("anomaly_flags") ?: ""
            val flagsList = flagsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

            DailyAttendanceAdminDto(
                id = rs.getObject("id", UUID::class.java),
                employeeId = rs.getObject("employee_id", UUID::class.java),
                employeeCode = rs.getString("employee_code"),
                employeeName = rs.getString("display_name"),
                department = rs.getString("dept_name") ?: "General",
                workDate = rs.getDate("work_date").toLocalDate(),
                shiftCode = rs.getString("shift_code") ?: "GEN_0830",
                shiftName = rs.getString("shift_name") ?: "General Day",
                firstInAt = rs.getTimestamp("first_in_at")?.toInstant(),
                lastOutAt = rs.getTimestamp("last_out_at")?.toInstant(),
                grossDurationMinutes = rs.getInt("gross_duration_minutes"),
                breakMinutes = rs.getInt("break_minutes"),
                netWorkedMinutes = rs.getInt("net_worked_minutes"),
                lateMinutes = rs.getInt("late_minutes"),
                earlyLeaveMinutes = rs.getInt("early_leave_minutes"),
                overtimeMinutesNormal = rs.getInt("overtime_minutes_normal"),
                overtimeMinutesRestDay = rs.getInt("overtime_minutes_rest_day"),
                overtimeMinutesHoliday = rs.getInt("overtime_minutes_holiday"),
                dayStatus = rs.getString("day_status"),
                leaveTypeName = null,
                leaveDays = rs.getBigDecimal("leave_days")?.toDouble() ?: 0.0,
                anomalyFlags = flagsList,
                calculationTrace = rs.getString("calculation_trace"),
                computedAt = rs.getTimestamp("computed_at").toInstant(),
            )
        }, *args.toTypedArray())

        val totalEmployees = records.size
        val presentCount = records.count { it.dayStatus == "PRESENT" }
        val halfDayCount = records.count { it.dayStatus == "HALF_DAY" }
        val absentCount = records.count { it.dayStatus == "ABSENT" }
        val restDayCount = records.count { it.dayStatus == "REST_DAY" }
        val holidayCount = records.count { it.dayStatus == "HOLIDAY" }
        val anomaliesCount = records.count { it.anomalyFlags.isNotEmpty() }
        val totalOtMinutes = records.sumOf { it.overtimeMinutesNormal + it.overtimeMinutesRestDay + it.overtimeMinutesHoliday }
        val totalOtHours = BigDecimal(totalOtMinutes).divide(BigDecimal(60), 1, java.math.RoundingMode.HALF_UP).toDouble()
        val totalLateMinutes = records.sumOf { it.lateMinutes }

        val summary = DailyAttendanceSummaryMetricsDto(
            totalEmployees = totalEmployees,
            presentCount = presentCount,
            halfDayCount = halfDayCount,
            absentCount = absentCount,
            restDayCount = restDayCount,
            holidayCount = holidayCount,
            anomaliesCount = anomaliesCount,
            totalOtHours = totalOtHours,
            totalLateMinutes = totalLateMinutes,
        )

        return DailyAttendanceListResponseDto(
            records = records,
            summary = summary,
        )
    }

    @Transactional(readOnly = true)
    fun getDailyAttendanceDetails(id: UUID): DailyAttendanceAdminDto {
        val tenantId = TenantContext.currentId()
        val sql =
            """
            SELECT da.id, da.employee_id, da.work_date, da.shift_id, da.first_in_at, da.last_out_at,
                   da.gross_duration_minutes, da.break_minutes, da.net_worked_minutes,
                   da.late_minutes, da.early_leave_minutes, da.overtime_minutes_normal,
                   da.overtime_minutes_rest_day, da.overtime_minutes_holiday, da.day_status,
                   da.leave_days, da.anomaly_flags, da.calculation_trace, da.computed_at,
                   e.employee_code, e.display_name, d.name as dept_name,
                   sh.code as shift_code, sh.name as shift_name
            FROM daily_attendance da
            JOIN employee e ON e.id = da.employee_id
            LEFT JOIN department d ON d.id = e.department_id
            LEFT JOIN shift sh ON sh.id = da.shift_id
            WHERE da.tenant_id = ? AND da.id = ?
            """
        return jdbc.query(sql, { rs, _ ->
            val flagsStr = rs.getString("anomaly_flags") ?: ""
            val flagsList = flagsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

            DailyAttendanceAdminDto(
                id = rs.getObject("id", UUID::class.java),
                employeeId = rs.getObject("employee_id", UUID::class.java),
                employeeCode = rs.getString("employee_code"),
                employeeName = rs.getString("display_name"),
                department = rs.getString("dept_name") ?: "General",
                workDate = rs.getDate("work_date").toLocalDate(),
                shiftCode = rs.getString("shift_code") ?: "GEN_0830",
                shiftName = rs.getString("shift_name") ?: "General Day",
                firstInAt = rs.getTimestamp("first_in_at")?.toInstant(),
                lastOutAt = rs.getTimestamp("last_out_at")?.toInstant(),
                grossDurationMinutes = rs.getInt("gross_duration_minutes"),
                breakMinutes = rs.getInt("break_minutes"),
                netWorkedMinutes = rs.getInt("net_worked_minutes"),
                lateMinutes = rs.getInt("late_minutes"),
                earlyLeaveMinutes = rs.getInt("early_leave_minutes"),
                overtimeMinutesNormal = rs.getInt("overtime_minutes_normal"),
                overtimeMinutesRestDay = rs.getInt("overtime_minutes_rest_day"),
                overtimeMinutesHoliday = rs.getInt("overtime_minutes_holiday"),
                dayStatus = rs.getString("day_status"),
                leaveTypeName = null,
                leaveDays = rs.getBigDecimal("leave_days")?.toDouble() ?: 0.0,
                anomalyFlags = flagsList,
                calculationTrace = rs.getString("calculation_trace"),
                computedAt = rs.getTimestamp("computed_at").toInstant(),
            )
        }, tenantId, id).firstOrNull() ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Daily attendance record not found: $id")
    }

    @Transactional
    fun recomputeAttendance(date: LocalDate?, employeeId: UUID?): Int {
        val tenantId = TenantContext.currentId()
        val targetDate = date ?: LocalDate.now()

        if (employeeId != null) {
            attendanceProcessorService.computeDayAttendance(employeeId, targetDate)
            return 1
        }

        val employeeIds = jdbc.query(
            "SELECT id FROM employee WHERE tenant_id = ? AND status = 'ACTIVE'",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            tenantId,
        )

        var count = 0
        for (empId in employeeIds) {
            runCatching {
                attendanceProcessorService.computeDayAttendance(empId, targetDate)
                count++
            }.onFailure { err ->
                log.warn("Failed recomputing attendance for emp {} on {}: {}", empId, targetDate, err.message)
            }
        }
        return count
    }

    // ---------------------------------------------------------------------------
    // 5. Payroll Variable Inputs Summary
    // ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun getPayrollVariableSummary(payPeriodId: String?): AttendancePayrollSummaryResponseDto {
        val tenantId = TenantContext.currentId()
        val today = LocalDate.now()
        val periodStart = today.withDayOfMonth(1)
        val periodEnd = today.withDayOfMonth(today.lengthOfMonth())

        val sql =
            """
            SELECT e.id as emp_id, e.employee_code, e.display_name, d.name as dept_name,
                   COALESCE(SUM(da.overtime_minutes_normal), 0) as normal_ot,
                   COALESCE(SUM(da.overtime_minutes_rest_day), 0) as rest_ot,
                   COALESCE(SUM(da.overtime_minutes_holiday), 0) as hol_ot,
                   COALESCE(SUM(da.late_minutes), 0) as total_late,
                   COALESCE(SUM(CASE WHEN da.day_status = 'ABSENT' THEN 1.0 ELSE 0.0 END), 0) as unpaid_days
            FROM employee e
            LEFT JOIN department d ON d.id = e.department_id
            LEFT JOIN daily_attendance da ON da.employee_id = e.id AND da.work_date BETWEEN ? AND ?
            WHERE e.tenant_id = ? AND e.status = 'ACTIVE'
            GROUP BY e.id, e.employee_code, e.display_name, d.name
            ORDER BY e.employee_code ASC
            """

        val items = jdbc.query(sql, { rs, _ ->
            val normalOt = rs.getInt("normal_ot")
            val restOt = rs.getInt("rest_ot")
            val holOt = rs.getInt("hol_ot")
            val totalLate = rs.getInt("total_late")
            val unpaidDays = rs.getDouble("unpaid_days")

            // Approximation for variable earnings display
            val hourlyRate = 12.50
            val normalOtEarn = (normalOt / 60.0) * hourlyRate * 1.5
            val restOtEarn = (restOt / 60.0) * hourlyRate * 2.0
            val holOtEarn = (holOt / 60.0) * hourlyRate * 2.5
            val totalOtEarnings = normalOtEarn + restOtEarn + holOtEarn
            val latePenalty = (totalLate / 60.0) * hourlyRate

            AttendancePayrollSummaryItemDto(
                employeeId = rs.getObject("emp_id", UUID::class.java),
                employeeCode = rs.getString("employee_code"),
                employeeName = rs.getString("display_name"),
                department = rs.getString("dept_name") ?: "General",
                normalOtMinutes = normalOt,
                restDayOtMinutes = restOt,
                holidayOtMinutes = holOt,
                otGrossEarnings = totalOtEarnings,
                lateMinutesTotal = totalLate,
                latePenaltyDeduction = latePenalty,
                unpaidAbsenceDays = unpaidDays,
            )
        }, periodStart, periodEnd, tenantId)

        val totalNormalOtHours = items.sumOf { it.normalOtMinutes } / 60.0
        val totalRestDayOtHours = items.sumOf { it.restDayOtMinutes } / 60.0
        val totalHolidayOtHours = items.sumOf { it.holidayOtMinutes } / 60.0
        val totalOtEarnings = items.sumOf { it.otGrossEarnings }
        val totalLatePenalty = items.sumOf { it.latePenaltyDeduction }
        val totalUnpaidDays = items.sumOf { it.unpaidAbsenceDays }

        return AttendancePayrollSummaryResponseDto(
            payPeriodId = payPeriodId ?: "${today.year}-${today.monthValue.toString().padStart(2, '0')}",
            totalNormalOtHours = BigDecimal(totalNormalOtHours).setScale(1, java.math.RoundingMode.HALF_UP).toDouble(),
            totalRestDayOtHours = BigDecimal(totalRestDayOtHours).setScale(1, java.math.RoundingMode.HALF_UP).toDouble(),
            totalHolidayOtHours = BigDecimal(totalHolidayOtHours).setScale(1, java.math.RoundingMode.HALF_UP).toDouble(),
            totalOtEarningsAmount = BigDecimal(totalOtEarnings).setScale(2, java.math.RoundingMode.HALF_UP).toDouble(),
            totalLatePenaltyDeductionAmount = BigDecimal(totalLatePenalty).setScale(2, java.math.RoundingMode.HALF_UP).toDouble(),
            totalUnpaidAbsenceDays = totalUnpaidDays,
            employeeCountWithOt = items.count { it.normalOtMinutes > 0 || it.restDayOtMinutes > 0 || it.holidayOtMinutes > 0 },
            employeeCountWithLatePenalty = items.count { it.lateMinutesTotal > 0 },
            employeeCountWithUnpaidAbsence = items.count { it.unpaidAbsenceDays > 0 },
            items = items,
        )
    }
}
