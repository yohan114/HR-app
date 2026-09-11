package com.hr.attendance

import com.hr.attendance.internal.AttendancePunchRequestDto
import com.hr.attendance.internal.AttendanceRegulariseRequestDto
import com.hr.attendance.internal.EmployeeShiftScheduleRepository
import com.hr.attendance.internal.MobileAttendanceController
import com.hr.attendance.internal.ShiftSwapRequestDto
import com.hr.employee.EmployeeLookupService
import com.hr.identity.internal.TokenService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@DisplayName("Mobile Attendance Controller Unit Tests")
class MobileAttendanceControllerTest {

    private val shiftRosterService = mockk<ShiftRosterService>(relaxed = true)
    private val punchIngestionService = mockk<PunchIngestionService>(relaxed = true)
    private val attendanceProcessorService = mockk<AttendanceProcessorService>(relaxed = true)
    private val scheduleRepository = mockk<EmployeeShiftScheduleRepository>(relaxed = true)
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)

    private val controller = MobileAttendanceController(
        shiftRosterService,
        punchIngestionService,
        attendanceProcessorService,
        scheduleRepository,
        employeeLookupService,
    )

    private val tenantId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()

    private lateinit var jwt: Jwt

    private val standardShift = ShiftDefinition(
        id = UUID.randomUUID(),
        code = "GEN_0830",
        name = "General Day (08:30 - 17:30)",
        startTime = LocalTime.of(8, 30),
        endTime = LocalTime.of(17, 30),
        breakMinutes = 60,
        workingMinutes = 480,
        graceInMinutes = 15,
        graceOutMinutes = 10,
    )

    @BeforeEach
    fun setUp() {
        jwt = mockk<Jwt>(relaxed = true) {
            every { subject } returns userId.toString()
            every { getClaimAsString(TokenService.CLAIM_TENANT_ID) } returns tenantId.toString()
            every { getClaimAsString(TokenService.CLAIM_EMPLOYEE_ID) } returns employeeId.toString()
            every { getClaimAsString(TokenService.CLAIM_DEVICE_ID) } returns deviceId.toString()
        }
    }

    @Test
    fun `getMyTodayAttendance returns shift rules and NOT_CHECKED_IN when no punches exist`() {
        val today = LocalDate.now()
        every { shiftRosterService.getSchedule(employeeId, today) } returns ShiftScheduleAssignment(
            id = UUID.randomUUID(),
            employeeId = employeeId,
            workDate = today,
            shiftId = standardShift.id,
            shift = standardShift,
        )
        every { punchIngestionService.getPunches(employeeId, today, today) } returns emptyList()

        val response = controller.getMyTodayAttendance(jwt)

        assertThat(response.employeeId).isEqualTo(employeeId)
        assertThat(response.currentStatus).isEqualTo("NOT_CHECKED_IN")
        assertThat(response.shift.code).isEqualTo("GEN_0830")
        assertThat(response.firstInAt).isNull()
        assertThat(response.punches).isEmpty()
        assertThat(response.geofence.radiusMeters).isEqualTo(200.0)
    }

    @Test
    fun `getMyTodayAttendance returns CHECKED_IN and firstIn timestamp when IN punch exists`() {
        val today = LocalDate.now()
        val punchTime = Instant.now().minusSeconds(1800) // 30 minutes ago
        every { shiftRosterService.getSchedule(employeeId, today) } returns ShiftScheduleAssignment(
            id = UUID.randomUUID(),
            employeeId = employeeId,
            workDate = today,
            shiftId = standardShift.id,
            shift = standardShift,
        )
        every { punchIngestionService.getPunches(employeeId, today, today) } returns listOf(
            RawPunchInput(
                employeeId = employeeId,
                punchedAt = punchTime,
                punchType = PunchType.IN,
                source = PunchSource.MOBILE_APP,
                geofenceStatus = GeofenceStatus.INSIDE,
            ),
        )

        val response = controller.getMyTodayAttendance(jwt)

        assertThat(response.currentStatus).isEqualTo("CHECKED_IN")
        assertThat(response.firstInAt).isEqualTo(punchTime)
        assertThat(response.punches).hasSize(1)
        assertThat(response.punches[0].punchType).isEqualTo("IN")
        assertThat(response.punches[0].geofenceStatus).isEqualTo("INSIDE")
    }

    @Test
    fun `ingestAttendancePunch marks INSIDE geofence when coordinates are within 200m radius`() {
        val punchInstant = Instant.now()
        val punchId = UUID.randomUUID()

        every { punchIngestionService.ingestPunch(any()) } returns PunchIngestResult(
            id = punchId,
            employeeId = employeeId,
            punchedAt = punchInstant,
            punchType = PunchType.IN,
            isDuplicate = false,
            statusMessage = "Punch recorded successfully",
        )

        // HQ coordinates: 6.9271, 79.8612. Very close point (~15m away: 6.9272, 79.8612)
        val request = AttendancePunchRequestDto(
            punchType = "IN",
            punchedAt = punchInstant,
            source = "MOBILE_APP",
            geoLat = 6.9272,
            geoLng = 79.8612,
            geoAccuracyM = 10.0,
            isMockLocation = false,
        )

        val response = controller.ingestAttendancePunch(jwt, "idemp-001", request)

        assertThat(response.punchType).isEqualTo("IN")
        assertThat(response.geofenceStatus).isEqualTo("INSIDE")
        assertThat(response.isMockLocation).isFalse()
        assertThat(response.message).contains("Punch recorded successfully")
    }

    @Test
    fun `ingestAttendancePunch marks OUTSIDE geofence when coordinates exceed 200m radius`() {
        val punchInstant = Instant.now()
        val punchId = UUID.randomUUID()

        every { punchIngestionService.ingestPunch(any()) } returns PunchIngestResult(
            id = punchId,
            employeeId = employeeId,
            punchedAt = punchInstant,
            punchType = PunchType.IN,
            isDuplicate = false,
            statusMessage = "Punch recorded successfully",
        )

        // Point ~1.8km away: 6.9114, 79.8821
        val request = AttendancePunchRequestDto(
            punchType = "IN",
            punchedAt = punchInstant,
            source = "MOBILE_APP",
            geoLat = 6.9114,
            geoLng = 79.8821,
            geoAccuracyM = 15.0,
            isMockLocation = false,
        )

        val response = controller.ingestAttendancePunch(jwt, "idemp-002", request)

        assertThat(response.geofenceStatus).isEqualTo("OUTSIDE")
        assertThat(response.message).contains("WARNING: Punch recorded outside designated branch geofence boundary")
    }

    @Test
    fun `ingestAttendancePunch flags security warning when isMockLocation is true`() {
        val punchInstant = Instant.now()
        val punchId = UUID.randomUUID()

        every { punchIngestionService.ingestPunch(any()) } returns PunchIngestResult(
            id = punchId,
            employeeId = employeeId,
            punchedAt = punchInstant,
            punchType = PunchType.IN,
            isDuplicate = false,
            statusMessage = "Punch recorded successfully",
        )

        val request = AttendancePunchRequestDto(
            punchType = "IN",
            punchedAt = punchInstant,
            source = "MOBILE_APP",
            geoLat = 6.9271,
            geoLng = 79.8612,
            isMockLocation = true,
        )

        val response = controller.ingestAttendancePunch(jwt, "idemp-003", request)

        assertThat(response.isMockLocation).isTrue()
        assertThat(response.message).contains("Mock GPS detected")
    }

    @Test
    fun `getMyShiftRoster returns structured roster schedule across window with rest days and shifts`() {
        val start = LocalDate.of(2026, 3, 2) // Monday
        val end = LocalDate.of(2026, 3, 8)   // Sunday

        every { shiftRosterService.getSchedules(employeeId, start, end) } returns listOf(
            ShiftScheduleAssignment(
                id = UUID.randomUUID(),
                employeeId = employeeId,
                workDate = LocalDate.of(2026, 3, 2),
                shiftId = standardShift.id,
                shift = standardShift,
                isRestDay = false,
                isHoliday = false,
            ),
            ShiftScheduleAssignment(
                id = UUID.randomUUID(),
                employeeId = employeeId,
                workDate = LocalDate.of(2026, 3, 7),
                shiftId = null,
                shift = null,
                isRestDay = true,
                isHoliday = false,
            ),
        )

        val roster = controller.getMyShiftRoster(jwt, start, end)

        assertThat(roster.startDate).isEqualTo(start)
        assertThat(roster.endDate).isEqualTo(end)
        assertThat(roster.days).hasSize(7)

        val monday = roster.days.find { it.date == LocalDate.of(2026, 3, 2) }
        assertThat(monday).isNotNull
        assertThat(monday!!.isRestDay).isFalse()
        assertThat(monday.shiftCode).isEqualTo("GEN_0830")
        assertThat(monday.startTime).isEqualTo("08:30")

        val saturday = roster.days.find { it.date == LocalDate.of(2026, 3, 7) }
        assertThat(saturday).isNotNull
        assertThat(saturday!!.isRestDay).isTrue()
        assertThat(saturday.dayStatus).isEqualTo("REST_DAY")
    }

    @Test
    fun `getTeamShiftCoverage returns team member shifts and duty indicators`() {
        val targetDate = LocalDate.of(2026, 3, 4)

        val coverage = controller.getTeamShiftCoverage(targetDate)

        assertThat(coverage.date).isEqualTo(targetDate)
        assertThat(coverage.members).isNotEmpty
        assertThat(coverage.totalScheduled).isGreaterThan(0)

        val kasun = coverage.members.find { it.employeeCode == "LK010" }
        assertThat(kasun).isNotNull
        assertThat(kasun!!.employeeName).isEqualTo("Kasun Mendis")
        assertThat(kasun.shiftCode).isEqualTo("GEN_0830")
    }

    @Test
    fun `requestShiftSwap records swap request and returns pending status`() {
        val targetColleagueId = UUID.randomUUID()
        val swapRequest = ShiftSwapRequestDto(
            workDate = LocalDate.of(2026, 3, 10),
            targetEmployeeId = targetColleagueId,
            targetWorkDate = LocalDate.of(2026, 3, 12),
            reason = "Family emergency coverage swap",
        )

        val response = controller.requestShiftSwap(jwt, "swap-idemp-001", swapRequest)

        assertThat(response.requestId).isEqualTo("swap-idemp-001")
        assertThat(response.status).isEqualTo("PENDING_APPROVAL")
        assertThat(response.message).contains("2026-03-10")
    }

    @Test
    fun `requestAttendanceRegularisation submits regularisation request with explanation`() {
        val regulariseRequest = AttendanceRegulariseRequestDto(
            workDate = LocalDate.of(2026, 3, 3),
            requestedInTime = "08:35",
            requestedOutTime = "17:35",
            reason = "Entrance gate biometric scanner failed to register morning RFID tag",
        )

        val response = controller.requestAttendanceRegularisation(jwt, "reg-idemp-001", regulariseRequest)

        assertThat(response.requestId).isEqualTo("reg-idemp-001")
        assertThat(response.status).isEqualTo("PENDING_APPROVAL")
        assertThat(response.message).contains("manager approval inbox")
    }
}
