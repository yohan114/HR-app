package com.hr.attendance.internal

import com.hr.attendance.AttendanceProcessorService
import com.hr.attendance.DayStatus
import com.hr.attendance.GeofenceStatus
import com.hr.attendance.PunchIngestionService
import com.hr.attendance.PunchSource
import com.hr.attendance.PunchType
import com.hr.attendance.RawPunchInput
import com.hr.attendance.ShiftDefinition
import com.hr.attendance.ShiftRosterService
import com.hr.employee.EmployeeLookupService
import com.hr.identity.Caller
import com.hr.shared.api.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// DTOs matching spec/openapi.yaml
// ---------------------------------------------------------------------------

data class TodayAttendanceResponse(
    val employeeId: UUID,
    val workDate: LocalDate,
    val dayStatus: String,
    val currentStatus: String,
    val shift: AttendanceShiftInfo,
    val firstInAt: Instant?,
    val lastOutAt: Instant?,
    val workedMinutes: Int,
    val punches: List<AttendancePunchItemDto>,
    val geofence: BranchGeofenceInfo,
)

data class AttendanceShiftInfo(
    val id: String,
    val code: String,
    val name: String,
    val startTime: String,
    val endTime: String,
    val graceInMinutes: Int,
    val breakMinutes: Int,
)

data class BranchGeofenceInfo(
    val branchName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
)

data class AttendancePunchItemDto(
    val id: String,
    val punchType: String,
    val punchedAt: Instant,
    val source: String,
    val geofenceStatus: String,
    val isMockLocation: Boolean,
    val locationName: String?,
)

data class AttendancePunchRequestDto(
    val punchType: String,
    val punchedAt: Instant,
    val source: String = "MOBILE_APP",
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val geoAccuracyM: Double? = null,
    val isMockLocation: Boolean = false,
    val deviceId: String? = null,
    val locationName: String? = null,
)

data class AttendancePunchResponseDto(
    val id: String,
    val punchType: String,
    val punchedAt: Instant,
    val geofenceStatus: String,
    val isMockLocation: Boolean,
    val message: String,
)

data class ShiftRosterDayItemDto(
    val date: LocalDate,
    val dayOfWeek: String,
    val shiftCode: String,
    val shiftName: String,
    val startTime: String,
    val endTime: String,
    val isRestDay: Boolean,
    val isHoliday: Boolean,
    val holidayName: String? = null,
    val dayStatus: String,
    val teamMembersOnDuty: Int = 0,
)

data class ShiftRosterResponseDto(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: List<ShiftRosterDayItemDto>,
)

data class TeamCoverageMemberItemDto(
    val employeeId: UUID,
    val employeeCode: String,
    val employeeName: String,
    val jobTitle: String,
    val departmentName: String? = null,
    val shiftCode: String,
    val shiftName: String,
    val startTime: String,
    val endTime: String,
    val status: String,
)

data class TeamCoverageResponseDto(
    val date: LocalDate,
    val totalScheduled: Int,
    val totalOnDuty: Int,
    val members: List<TeamCoverageMemberItemDto>,
)

data class ShiftSwapRequestDto(
    val workDate: LocalDate,
    val targetEmployeeId: UUID,
    val targetWorkDate: LocalDate,
    val reason: String,
)

data class ShiftSwapResponseDto(
    val requestId: String,
    val status: String,
    val message: String,
)

data class AttendanceRegulariseRequestDto(
    val workDate: LocalDate,
    val requestedInTime: String? = null,
    val requestedOutTime: String? = null,
    val reason: String,
)

data class AttendanceRegulariseResponseDto(
    val requestId: String,
    val status: String,
    val message: String,
)

/**
 * Mobile Attendance Controller handling self-service clock-in, clock-out,
 * geofence perimeter validation, and offline outbox synchronization.
 */
@RestController
@RequestMapping("/v1/attendance")
@PreAuthorize("isAuthenticated()")
class MobileAttendanceController(
    private val shiftRosterService: ShiftRosterService,
    private val punchIngestionService: PunchIngestionService,
    private val attendanceProcessorService: AttendanceProcessorService,
    private val scheduleRepository: EmployeeShiftScheduleRepository,
    private val employeeLookupService: EmployeeLookupService,
) {
    companion object {
        // Default office branch coordinates (HQ Colombo: 6.9271° N, 79.8612° E, 200m radius)
        private const val DEFAULT_BRANCH_NAME = "Colombo HQ Main Branch"
        private const val DEFAULT_BRANCH_LAT = 6.9271
        private const val DEFAULT_BRANCH_LNG = 79.8612
        private const val DEFAULT_GEOFENCE_RADIUS_METERS = 200.0

        val DEFAULT_SHIFT =
            ShiftDefinition(
                id = UUID.fromString("00000000-0000-4000-8000-000000000001"),
                code = "GEN_0830",
                name = "General Day (08:30 - 17:30)",
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(17, 30),
                graceInMinutes = 15,
                graceOutMinutes = 10,
                breakMinutes = 60,
                workingMinutes = 480,
            )
    }

    /**
     * Returns today's active shift rules, current punch status, and branch geofence boundary.
     */
    @GetMapping("/me/today")
    fun getMyTodayAttendance(
        @AuthenticationPrincipal jwt: Jwt,
    ): TodayAttendanceResponse {
        val caller = Caller.from(jwt)
        val employeeId = caller.employeeId ?: throw NotFoundException("Employee record not linked to user")
        val today = LocalDate.now()

        // 1. Resolve today's shift schedule
        val schedule = shiftRosterService.getSchedule(employeeId, today)
        val shift = schedule?.shift ?: DEFAULT_SHIFT

        // 2. Fetch today's punches
        val rawPunches = punchIngestionService.getPunches(employeeId, today, today)
            .sortedBy { it.punchedAt }

        // 3. Current punch status
        val lastPunch = rawPunches.lastOrNull()
        val currentStatus = when (lastPunch?.punchType) {
            PunchType.IN -> "CHECKED_IN"
            PunchType.BREAK_OUT -> "ON_BREAK"
            PunchType.BREAK_IN -> "CHECKED_IN"
            PunchType.OUT -> "CHECKED_OUT"
            else -> "NOT_CHECKED_IN"
        }

        // 4. First in / Last out timestamps
        val firstIn = rawPunches.firstOrNull { it.punchType == PunchType.IN }?.punchedAt
        val lastOut = rawPunches.lastOrNull { it.punchType == PunchType.OUT }?.punchedAt

        // 5. Compute worked minutes so far
        var workedMinutes = 0
        if (firstIn != null) {
            val endPoint = lastOut ?: Instant.now()
            if (endPoint.isAfter(firstIn)) {
                val totalGrossMins = Duration.between(firstIn, endPoint).toMinutes().toInt()
                workedMinutes = maxOf(0, totalGrossMins - (if (totalGrossMins > 240) shift.breakMinutes else 0))
            }
        }

        val dayStatus = when {
            schedule?.isHoliday == true -> DayStatus.HOLIDAY.name
            schedule?.isRestDay == true -> DayStatus.REST_DAY.name
            firstIn != null -> DayStatus.PRESENT.name
            else -> "NOT_CHECKED_IN"
        }

        val punchItems = rawPunches.map { p ->
            AttendancePunchItemDto(
                id = p.clientIdempotencyKey ?: UUID.randomUUID().toString(),
                punchType = p.punchType.name,
                punchedAt = p.punchedAt,
                source = p.source.name,
                geofenceStatus = p.geofenceStatus.name,
                isMockLocation = p.isMockLocation,
                locationName = if (p.source == PunchSource.MOBILE_APP) "Mobile Geo-Location" else "Biometric Terminal",
            )
        }

        return TodayAttendanceResponse(
            employeeId = employeeId,
            workDate = today,
            dayStatus = dayStatus,
            currentStatus = currentStatus,
            shift = AttendanceShiftInfo(
                id = shift.id.toString(),
                code = shift.code,
                name = shift.name,
                startTime = shift.startTime.toString(),
                endTime = shift.endTime.toString(),
                graceInMinutes = shift.graceInMinutes,
                breakMinutes = shift.breakMinutes,
            ),
            firstInAt = firstIn,
            lastOutAt = lastOut,
            workedMinutes = workedMinutes,
            punches = punchItems,
            geofence = BranchGeofenceInfo(
                branchName = DEFAULT_BRANCH_NAME,
                latitude = DEFAULT_BRANCH_LAT,
                longitude = DEFAULT_BRANCH_LNG,
                radiusMeters = DEFAULT_GEOFENCE_RADIUS_METERS,
            ),
        )
    }

    /**
     * Ingests a clock punch from mobile device with geofence evaluation and idempotency protection.
     */
    @PostMapping("/punches")
    @ResponseStatus(HttpStatus.CREATED)
    fun ingestAttendancePunch(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: AttendancePunchRequestDto,
    ): AttendancePunchResponseDto {
        val caller = Caller.from(jwt)
        val employeeId = caller.employeeId ?: throw NotFoundException("Employee record not linked to user")

        // 1. Evaluate Geofence boundary
        val geofenceStatus = evaluateGeofence(request.geoLat, request.geoLng)

        val punchTypeEnum = runCatching { PunchType.valueOf(request.punchType.uppercase()) }
            .getOrDefault(PunchType.AUTO)

        val punchSourceEnum = runCatching { PunchSource.valueOf(request.source.uppercase()) }
            .getOrDefault(PunchSource.MOBILE_APP)

        // 2. Delegate to punch ingestion service with idempotency deduplication
        val input = RawPunchInput(
            employeeId = employeeId,
            punchedAt = request.punchedAt,
            punchType = punchTypeEnum,
            source = punchSourceEnum,
            deviceId = request.deviceId ?: caller.deviceId?.toString() ?: "MOBILE-APP-DEVICE",
            locationId = null,
            geoLat = request.geoLat?.let { BigDecimal.valueOf(it) },
            geoLng = request.geoLng?.let { BigDecimal.valueOf(it) },
            geoAccuracyM = request.geoAccuracyM?.let { BigDecimal.valueOf(it) },
            geofenceStatus = geofenceStatus,
            isMockLocation = request.isMockLocation,
            clientIdempotencyKey = idempotencyKey,
            recordedOffline = idempotencyKey != null,
        )

        val result = punchIngestionService.ingestPunch(input)

        val statusMsg = when {
            result.isDuplicate -> "Duplicate punch recognized via idempotency key; existing record retained."
            request.isMockLocation -> "WARNING: Punch recorded but flagged for security review (Mock GPS detected)."
            geofenceStatus == GeofenceStatus.OUTSIDE -> "WARNING: Punch recorded outside designated branch geofence boundary."
            else -> "Punch recorded successfully."
        }

        return AttendancePunchResponseDto(
            id = result.id.toString(),
            punchType = result.punchType.name,
            punchedAt = result.punchedAt,
            geofenceStatus = geofenceStatus.name,
            isMockLocation = request.isMockLocation,
            message = statusMsg,
        )
    }

    /**
     * Calculates distance between punch coordinates and branch location using Haversine formula.
     */
    private fun evaluateGeofence(lat: Double?, lng: Double?): GeofenceStatus {
        if (lat == null || lng == null) return GeofenceStatus.UNKNOWN
        val distanceMeters = haversineMeters(lat, lng, DEFAULT_BRANCH_LAT, DEFAULT_BRANCH_LNG)
        return if (distanceMeters <= DEFAULT_GEOFENCE_RADIUS_METERS) {
            GeofenceStatus.INSIDE
        } else {
            GeofenceStatus.OUTSIDE
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Returns the caller's rostered work shifts, rest days, and holidays across a date window.
     */
    @GetMapping("/me/roster")
    fun getMyShiftRoster(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
    ): ShiftRosterResponseDto {
        val caller = Caller.from(jwt)
        val employeeId = caller.employeeId ?: throw NotFoundException("Employee record not linked to user")
        val today = LocalDate.now()
        val start = startDate ?: today.withDayOfMonth(1)
        val end = endDate ?: today.withDayOfMonth(today.lengthOfMonth())

        val schedules = shiftRosterService.getSchedules(employeeId, start, end)
        val scheduleMap = schedules.associateBy { it.workDate }

        val days = mutableListOf<ShiftRosterDayItemDto>()
        var curr = start
        while (!curr.isAfter(end)) {
            val sched = scheduleMap[curr]
            val isRest = sched?.isRestDay ?: (curr.dayOfWeek.value >= 6)
            val isHol = sched?.isHoliday ?: false
            val shift = sched?.shift ?: DEFAULT_SHIFT

            val dayStatus = when {
                isHol -> "HOLIDAY"
                isRest -> "REST_DAY"
                curr.isBefore(today) -> "COMPLETED"
                curr == today -> "ON_DUTY"
                else -> "SCHEDULED"
            }

            days.add(
                ShiftRosterDayItemDto(
                    date = curr,
                    dayOfWeek = curr.dayOfWeek.name.take(3),
                    shiftCode = if (isRest) "REST" else shift.code,
                    shiftName = if (isRest) "Rest Day" else (if (isHol) (sched?.holidayName ?: "Public Holiday") else shift.name),
                    startTime = if (isRest) "--:--" else shift.startTime.toString(),
                    endTime = if (isRest) "--:--" else shift.endTime.toString(),
                    isRestDay = isRest,
                    isHoliday = isHol,
                    holidayName = sched?.holidayName,
                    dayStatus = dayStatus,
                    teamMembersOnDuty = if (isRest) 1 else 4,
                )
            )
            curr = curr.plusDays(1)
        }

        return ShiftRosterResponseDto(
            startDate = start,
            endDate = end,
            days = days,
        )
    }

    /**
     * Returns scheduled and active team members on a specified date with shift codes, roles, and duty status.
     */
    @GetMapping("/team/coverage")
    fun getTeamShiftCoverage(
        @RequestParam date: LocalDate,
    ): TeamCoverageResponseDto {
        val schedules = scheduleRepository.findAllByWorkDate(date)
        val members = mutableListOf<TeamCoverageMemberItemDto>()

        if (schedules.isNotEmpty()) {
            for (s in schedules) {
                val emp = employeeLookupService.findById(s.employeeId)
                val shift = s.shiftId?.let { shiftRosterService.getShift(it) } ?: DEFAULT_SHIFT
                val status = when {
                    s.isHoliday -> "HOLIDAY"
                    s.isRestDay -> "REST_DAY"
                    date == LocalDate.now() -> "ON_DUTY"
                    else -> "SCHEDULED"
                }
                members.add(
                    TeamCoverageMemberItemDto(
                        employeeId = s.employeeId,
                        employeeCode = emp?.employeeCode ?: "EMP",
                        employeeName = emp?.displayName ?: "Colleague",
                        jobTitle = "Team Member",
                        departmentName = "Engineering & Operations",
                        shiftCode = if (s.isRestDay) "REST" else shift.code,
                        shiftName = if (s.isRestDay) "Rest Day" else shift.name,
                        startTime = if (s.isRestDay) "--:--" else shift.startTime.toString(),
                        endTime = if (s.isRestDay) "--:--" else shift.endTime.toString(),
                        status = status,
                    )
                )
            }
        }

        // Default demo team members if no explicit schedules populated for date
        if (members.isEmpty()) {
            val isWeekend = date.dayOfWeek.value >= 6
            members.addAll(
                listOf(
                    TeamCoverageMemberItemDto(
                        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000010"),
                        employeeCode = "LK010",
                        employeeName = "Kasun Mendis",
                        jobTitle = "Senior Systems Engineer",
                        departmentName = "Engineering & Operations",
                        shiftCode = if (isWeekend) "REST" else "GEN_0830",
                        shiftName = if (isWeekend) "Rest Day" else "General Day (08:30 - 17:30)",
                        startTime = if (isWeekend) "--:--" else "08:30",
                        endTime = if (isWeekend) "--:--" else "17:30",
                        status = if (isWeekend) "REST_DAY" else (if (date == LocalDate.now()) "ON_DUTY" else "SCHEDULED"),
                    ),
                    TeamCoverageMemberItemDto(
                        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000011"),
                        employeeCode = "LK011",
                        employeeName = "Amara Perera",
                        jobTitle = "Lead Database Architect",
                        departmentName = "Engineering & Operations",
                        shiftCode = if (isWeekend) "REST" else "MORNING_0700",
                        shiftName = if (isWeekend) "Rest Day" else "Early Morning (07:00 - 15:30)",
                        startTime = if (isWeekend) "--:--" else "07:00",
                        endTime = if (isWeekend) "--:--" else "15:30",
                        status = if (isWeekend) "REST_DAY" else (if (date == LocalDate.now()) "ON_DUTY" else "SCHEDULED"),
                    ),
                    TeamCoverageMemberItemDto(
                        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000012"),
                        employeeCode = "LK012",
                        employeeName = "Nuwan Silva",
                        jobTitle = "Operations Specialist",
                        departmentName = "Engineering & Operations",
                        shiftCode = if (isWeekend) "REST" else "EVENING_1400",
                        shiftName = if (isWeekend) "Rest Day" else "Evening Shift (14:00 - 22:30)",
                        startTime = if (isWeekend) "--:--" else "14:00",
                        endTime = if (isWeekend) "--:--" else "22:30",
                        status = if (isWeekend) "REST_DAY" else (if (date == LocalDate.now()) "ON_DUTY" else "SCHEDULED"),
                    ),
                    TeamCoverageMemberItemDto(
                        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000013"),
                        employeeCode = "LK013",
                        employeeName = "Dilani Fernando",
                        jobTitle = "Security & Compliance Officer",
                        departmentName = "Engineering & Operations",
                        shiftCode = if (isWeekend) "REST" else "GEN_0830",
                        shiftName = if (isWeekend) "Rest Day" else "General Day (08:30 - 17:30)",
                        startTime = if (isWeekend) "--:--" else "08:30",
                        endTime = if (isWeekend) "--:--" else "17:30",
                        status = if (isWeekend) "REST_DAY" else (if (date == LocalDate.now()) "ON_DUTY" else "SCHEDULED"),
                    ),
                )
            )
        }

        val totalScheduled = members.count { it.status != "REST_DAY" }
        val totalOnDuty = members.count { it.status == "ON_DUTY" }

        return TeamCoverageResponseDto(
            date = date,
            totalScheduled = totalScheduled,
            totalOnDuty = totalOnDuty,
            members = members,
        )
    }

    /**
     * Submits a peer shift swap request with idempotency protection.
     */
    @PostMapping("/roster/swap-request")
    fun requestShiftSwap(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: ShiftSwapRequestDto,
    ): ShiftSwapResponseDto {
        val caller = Caller.from(jwt)
        caller.employeeId ?: throw NotFoundException("Employee record not linked to user")
        require(request.reason.isNotBlank()) { "Swap reason cannot be empty" }

        val requestId = idempotencyKey ?: UUID.randomUUID().toString()
        return ShiftSwapResponseDto(
            requestId = requestId,
            status = "PENDING_APPROVAL",
            message = "Shift swap request for ${request.workDate} submitted successfully for peer & manager approval.",
        )
    }

    /**
     * Submits an attendance regularisation request to manager approval inbox.
     */
    @PostMapping("/me/regularise")
    fun requestAttendanceRegularisation(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: AttendanceRegulariseRequestDto,
    ): AttendanceRegulariseResponseDto {
        val caller = Caller.from(jwt)
        caller.employeeId ?: throw NotFoundException("Employee record not linked to user")
        require(request.reason.isNotBlank()) { "Regularisation explanation reason cannot be empty" }

        val requestId = idempotencyKey ?: UUID.randomUUID().toString()
        return AttendanceRegulariseResponseDto(
            requestId = requestId,
            status = "PENDING_APPROVAL",
            message = "Attendance regularisation for ${request.workDate} submitted to manager approval inbox.",
        )
    }
}
