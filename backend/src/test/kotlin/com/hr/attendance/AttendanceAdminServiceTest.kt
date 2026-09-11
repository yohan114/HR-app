package com.hr.attendance

import com.hr.attendance.internal.AttendanceAdminService
import com.hr.attendance.internal.AssignShiftPayloadDto
import com.hr.attendance.internal.DailyAttendanceRepository
import com.hr.attendance.internal.EmployeeShiftScheduleRepository
import com.hr.attendance.internal.RawPunchRepository
import com.hr.attendance.internal.ShiftRepository
import com.hr.employee.EmployeeLookupService
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.util.UUID

@DisplayName("Attendance Administration Service")
class AttendanceAdminServiceTest {

    private val shiftRepository = mockk<ShiftRepository>()
    private val employeeShiftScheduleRepository = mockk<EmployeeShiftScheduleRepository>()
    private val rawPunchRepository = mockk<RawPunchRepository>()
    private val dailyAttendanceRepository = mockk<DailyAttendanceRepository>()
    private val shiftRosterService = mockk<ShiftRosterService>()
    private val punchIngestionService = mockk<PunchIngestionService>()
    private val attendanceProcessorService = mockk<AttendanceProcessorService>(relaxed = true)
    private val employeeLookupService = mockk<EmployeeLookupService>()
    private val jdbc = mockk<JdbcTemplate>(relaxed = true)

    private val tenantId = UUID.randomUUID()
    private lateinit var service: AttendanceAdminService

    @BeforeEach
    fun setUp() {
        TenantContext.set(
            TenantHandle(
                id = tenantId,
                code = "test",
                name = "Test Tenant",
                dataRegion = "default",
                defaultCurrency = "USD",
                timezone = "UTC",
                locale = "en",
                isolationTier = IsolationTier.SHARED,
                status = TenantStatus.ACTIVE,
            )
        )
        service = AttendanceAdminService(
            shiftRepository = shiftRepository,
            employeeShiftScheduleRepository = employeeShiftScheduleRepository,
            rawPunchRepository = rawPunchRepository,
            dailyAttendanceRepository = dailyAttendanceRepository,
            shiftRosterService = shiftRosterService,
            punchIngestionService = punchIngestionService,
            attendanceProcessorService = attendanceProcessorService,
            employeeLookupService = employeeLookupService,
            jdbc = jdbc,
        )
    }

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `listShifts falls back to default system shift if none configured`() {
        every { shiftRepository.findAll() } returns emptyList()

        val shifts = service.listShifts()
        assertThat(shifts).isNotEmpty
        assertThat(shifts.first().code).isEqualTo("GEN_0830")
        assertThat(shifts.first().workingMinutes).isEqualTo(480)
    }

    @Test
    fun `assignShift updates schedule via jdbc upsert`() {
        val empId = UUID.randomUUID()
        val shiftId = UUID.randomUUID()
        val workDate = LocalDate.now()

        every { shiftRepository.findById(shiftId) } returns java.util.Optional.empty()

        val result = service.assignShift(
            AssignShiftPayloadDto(
                employeeId = empId,
                workDate = workDate,
                shiftId = shiftId,
                isRestDay = false,
            )
        )

        assertThat(result.employeeId).isEqualTo(empId)
        assertThat(result.workDate).isEqualTo(workDate)
        assertThat(result.source).isEqualTo("MANUAL")
        verify { jdbc.update(any<String>(), *anyVararg()) }
    }

    @Test
    fun `recomputeAttendance delegates to processor service`() {
        val empId = UUID.randomUUID()
        val today = LocalDate.now()

        val count = service.recomputeAttendance(today, empId)
        assertThat(count).isEqualTo(1)
        verify { attendanceProcessorService.computeDayAttendance(empId, today) }
    }
}
