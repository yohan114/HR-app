package com.hr.attendance

import com.hr.attendance.internal.DailyAttendance
import com.hr.attendance.internal.DailyAttendanceRepository
import com.hr.attendance.internal.DefaultAttendanceProcessorService
import com.hr.attendance.internal.EmployeeShiftSchedule
import com.hr.attendance.internal.EmployeeShiftScheduleRepository
import com.hr.attendance.internal.RawPunch
import com.hr.attendance.internal.RawPunchRepository
import com.hr.attendance.internal.Shift
import com.hr.attendance.internal.ShiftRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Optional
import java.util.UUID

@DisplayName("Attendance Processor Engine")
class AttendanceProcessorTest {

    private val shiftRepository = mockk<ShiftRepository>(relaxed = true)
    private val scheduleRepository = mockk<EmployeeShiftScheduleRepository>(relaxed = true)
    private val rawPunchRepository = mockk<RawPunchRepository>(relaxed = true)
    private val dailyAttendanceRepository = mockk<DailyAttendanceRepository>(relaxed = true)

    private val processor = DefaultAttendanceProcessorService(
        shiftRepository,
        scheduleRepository,
        rawPunchRepository,
        dailyAttendanceRepository,
    )

    private val employeeId = UUID.randomUUID()
    private val defaultZone = ZoneId.of("Asia/Colombo")

    private val standardShift = Shift(
        code = "GEN_0830",
        name = "General Day (08:30 - 17:30)",
        shiftType = ShiftType.FIXED,
        startTime = LocalTime.of(8, 30),
        endTime = LocalTime.of(17, 30),
        breakMinutes = 60,
        workingMinutes = 480,
        graceInMinutes = 15,
        graceOutMinutes = 10,
        halfDayThresholdMinutes = 240,
        otEligible = true,
        otStartAfterMinutes = 480,
        minOtMinutes = 30,
    )
    private val shiftId = standardShift.id

    @BeforeEach
    fun setUp() {
        every { shiftRepository.findById(shiftId) } returns Optional.of(standardShift)
        every { dailyAttendanceRepository.findByEmployeeIdAndWorkDate(any(), any()) } returns null
        every { dailyAttendanceRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `calculates on-time arrival within 15-minute grace period with zero late minutes`() {
        val workDate = LocalDate.of(2026, 3, 2)
        val schedule = EmployeeShiftSchedule(
            employeeId = employeeId,
            workDate = workDate,
            shiftId = shiftId,
        )
        every { scheduleRepository.findByEmployeeIdAndWorkDate(employeeId, workDate) } returns schedule

        // Arrived at 08:40 (Shift start 08:30, grace limit 08:45 -> On time)
        // Departed at 17:30
        val punchIn = ZonedDateTime.of(workDate, LocalTime.of(8, 40), defaultZone).toInstant()
        val punchOut = ZonedDateTime.of(workDate, LocalTime.of(17, 30), defaultZone).toInstant()

        val punches = listOf(
            RawPunch(employeeId, punchIn, PunchType.IN, PunchSource.BIOMETRIC_DEVICE),
            RawPunch(employeeId, punchOut, PunchType.OUT, PunchSource.BIOMETRIC_DEVICE),
        )
        every { rawPunchRepository.findAllByEmployeeIdAndPunchedAtBetweenOrderByPunchedAtAsc(any(), any(), any()) } returns punches

        val record = processor.computeDayAttendance(employeeId, workDate)

        assertThat(record.lateMinutes).isEqualTo(0)
        assertThat(record.earlyLeaveMinutes).isEqualTo(0)
        assertThat(record.dayStatus).isEqualTo(DayStatus.PRESENT)
        assertThat(record.netWorkedMinutes).isEqualTo(470) // 530 gross - 60 break
        assertThat(record.calculationTrace).contains("On-time check-in")
    }

    @Test
    fun `calculates late arrival beyond grace period with exact late minutes`() {
        val workDate = LocalDate.of(2026, 3, 3)
        val schedule = EmployeeShiftSchedule(
            employeeId = employeeId,
            workDate = workDate,
            shiftId = shiftId,
        )
        every { scheduleRepository.findByEmployeeIdAndWorkDate(employeeId, workDate) } returns schedule

        // Arrived at 09:05 (Shift start 08:30 -> 35 minutes late)
        val punchIn = ZonedDateTime.of(workDate, LocalTime.of(9, 5), defaultZone).toInstant()
        val punchOut = ZonedDateTime.of(workDate, LocalTime.of(17, 30), defaultZone).toInstant()

        val punches = listOf(
            RawPunch(employeeId, punchIn, PunchType.IN, PunchSource.BIOMETRIC_DEVICE),
            RawPunch(employeeId, punchOut, PunchType.OUT, PunchSource.BIOMETRIC_DEVICE),
        )
        every { rawPunchRepository.findAllByEmployeeIdAndPunchedAtBetweenOrderByPunchedAtAsc(any(), any(), any()) } returns punches

        val record = processor.computeDayAttendance(employeeId, workDate)

        assertThat(record.lateMinutes).isEqualTo(35)
        assertThat(record.calculationTrace).contains("Late duration: 35 minutes")
    }

    @Test
    fun `calculates early departure before grace limit`() {
        val workDate = LocalDate.of(2026, 3, 4)
        val schedule = EmployeeShiftSchedule(
            employeeId = employeeId,
            workDate = workDate,
            shiftId = shiftId,
        )
        every { scheduleRepository.findByEmployeeIdAndWorkDate(employeeId, workDate) } returns schedule

        // Arrived 08:25, Departed 16:30 (Shift end 17:30, grace out 17:20 -> 60 minutes early leave)
        val punchIn = ZonedDateTime.of(workDate, LocalTime.of(8, 25), defaultZone).toInstant()
        val punchOut = ZonedDateTime.of(workDate, LocalTime.of(16, 30), defaultZone).toInstant()

        val punches = listOf(
            RawPunch(employeeId, punchIn, PunchType.IN, PunchSource.BIOMETRIC_DEVICE),
            RawPunch(employeeId, punchOut, PunchType.OUT, PunchSource.BIOMETRIC_DEVICE),
        )
        every { rawPunchRepository.findAllByEmployeeIdAndPunchedAtBetweenOrderByPunchedAtAsc(any(), any(), any()) } returns punches

        val record = processor.computeDayAttendance(employeeId, workDate)

        assertThat(record.lateMinutes).isEqualTo(0)
        assertThat(record.earlyLeaveMinutes).isEqualTo(60)
        assertThat(record.calculationTrace).contains("Early departure: Checked out at 16:30")
    }

    @Test
    fun `calculates weekday overtime when net worked minutes exceeds shift duration`() {
        val workDate = LocalDate.of(2026, 3, 5)
        val schedule = EmployeeShiftSchedule(
            employeeId = employeeId,
            workDate = workDate,
            shiftId = shiftId,
        )
        every { scheduleRepository.findByEmployeeIdAndWorkDate(employeeId, workDate) } returns schedule

        // Arrived 08:00, Departed 19:30
        // Gross: 11h 30m = 690m. Break: 60m. Net: 630m.
        // Overtime: 630 - 480 = 150 minutes (2.5 hours)
        val punchIn = ZonedDateTime.of(workDate, LocalTime.of(8, 0), defaultZone).toInstant()
        val punchOut = ZonedDateTime.of(workDate, LocalTime.of(19, 30), defaultZone).toInstant()

        val punches = listOf(
            RawPunch(employeeId, punchIn, PunchType.IN, PunchSource.BIOMETRIC_DEVICE),
            RawPunch(employeeId, punchOut, PunchType.OUT, PunchSource.BIOMETRIC_DEVICE),
        )
        every { rawPunchRepository.findAllByEmployeeIdAndPunchedAtBetweenOrderByPunchedAtAsc(any(), any(), any()) } returns punches

        val record = processor.computeDayAttendance(employeeId, workDate)

        assertThat(record.netWorkedMinutes).isEqualTo(630)
        assertThat(record.overtimeMinutesNormal).isEqualTo(150)
        assertThat(record.calculationTrace).contains("Overtime: 150 minutes exceeded shift duration")
    }

    @Test
    fun `calculates rest day overtime at 2_0x for weekend work`() {
        val workDate = LocalDate.of(2026, 3, 7) // Saturday
        val schedule = EmployeeShiftSchedule(
            employeeId = employeeId,
            workDate = workDate,
            shiftId = null,
            isRestDay = true,
        )
        every { scheduleRepository.findByEmployeeIdAndWorkDate(employeeId, workDate) } returns schedule

        // Worked on Saturday 09:00 to 14:00 (5 hours = 300 gross, minus 60 break = 240 net minutes)
        val punchIn = ZonedDateTime.of(workDate, LocalTime.of(9, 0), defaultZone).toInstant()
        val punchOut = ZonedDateTime.of(workDate, LocalTime.of(14, 0), defaultZone).toInstant()

        val punches = listOf(
            RawPunch(employeeId, punchIn, PunchType.IN, PunchSource.BIOMETRIC_DEVICE),
            RawPunch(employeeId, punchOut, PunchType.OUT, PunchSource.BIOMETRIC_DEVICE),
        )
        every { rawPunchRepository.findAllByEmployeeIdAndPunchedAtBetweenOrderByPunchedAtAsc(any(), any(), any()) } returns punches

        val record = processor.computeDayAttendance(employeeId, workDate)

        assertThat(record.dayStatus).isEqualTo(DayStatus.REST_DAY)
        assertThat(record.netWorkedMinutes).isEqualTo(240)
        assertThat(record.overtimeMinutesRestDay).isEqualTo(240)
        assertThat(record.overtimeMinutesNormal).isEqualTo(0)
        assertThat(record.calculationTrace).contains("Scheduled Rest Day worked: 240 net minutes credited to Rest Day Overtime (2.0x rate)")
    }

    @Test
    fun `marks ABSENT on scheduled working day with zero clock punches`() {
        val workDate = LocalDate.of(2026, 3, 6)
        val schedule = EmployeeShiftSchedule(
            employeeId = employeeId,
            workDate = workDate,
            shiftId = shiftId,
        )
        every { scheduleRepository.findByEmployeeIdAndWorkDate(employeeId, workDate) } returns schedule
        every { rawPunchRepository.findAllByEmployeeIdAndPunchedAtBetweenOrderByPunchedAtAsc(any(), any(), any()) } returns emptyList()

        val record = processor.computeDayAttendance(employeeId, workDate)

        assertThat(record.dayStatus).isEqualTo(DayStatus.ABSENT)
        assertThat(record.netWorkedMinutes).isEqualTo(0)
        assertThat(record.calculationTrace).contains("Marked ABSENT")
    }
}
