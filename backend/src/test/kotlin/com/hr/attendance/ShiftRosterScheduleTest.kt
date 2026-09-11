package com.hr.attendance

import com.hr.attendance.internal.DefaultShiftRosterService
import com.hr.attendance.internal.EmployeeShiftSchedule
import com.hr.attendance.internal.EmployeeShiftScheduleRepository
import com.hr.attendance.internal.Shift
import com.hr.attendance.internal.ShiftRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@DisplayName("Shift Roster and Schedule Service")
class ShiftRosterScheduleTest {

    private val shiftRepository = mockk<ShiftRepository>(relaxed = true)
    private val scheduleRepository = mockk<EmployeeShiftScheduleRepository>(relaxed = true)
    private val service = DefaultShiftRosterService(shiftRepository, scheduleRepository)

    private val employeeId = UUID.randomUUID()
    private val shiftId = UUID.randomUUID()

    @Test
    fun `creates shift with grace periods, break minutes, and overtime thresholds`() {
        val shiftDef = ShiftDefinition(
            id = shiftId,
            code = "GENERAL_0830",
            name = "General Day Shift (08:30 - 17:30)",
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

        val entity = Shift(
            code = shiftDef.code,
            name = shiftDef.name,
            shiftType = shiftDef.shiftType,
            startTime = shiftDef.startTime,
            endTime = shiftDef.endTime,
            breakMinutes = shiftDef.breakMinutes,
            crossesMidnight = shiftDef.crossesMidnight,
            workingMinutes = shiftDef.workingMinutes,
            graceInMinutes = shiftDef.graceInMinutes,
            graceOutMinutes = shiftDef.graceOutMinutes,
            halfDayThresholdMinutes = shiftDef.halfDayThresholdMinutes,
            otEligible = shiftDef.otEligible,
            otStartAfterMinutes = shiftDef.otStartAfterMinutes,
            minOtMinutes = shiftDef.minOtMinutes,
            color = shiftDef.color,
            isActive = shiftDef.isActive,
        )

        every { shiftRepository.save(any()) } returns entity

        val created = service.createShift(shiftDef)
        assertThat(created.id).isEqualTo(entity.id)
        assertThat(created.code).isEqualTo("GENERAL_0830")
        assertThat(created.graceInMinutes).isEqualTo(15)
        assertThat(created.graceOutMinutes).isEqualTo(10)
        assertThat(created.workingMinutes).isEqualTo(480)
    }

    @Test
    fun `generates default schedule for week marking Saturdays and Sundays as rest days`() {
        val startDate = LocalDate.of(2026, 3, 2) // Monday
        val endDate = LocalDate.of(2026, 3, 8)   // Sunday (7 days)

        every { scheduleRepository.findByEmployeeIdAndWorkDate(any(), any()) } returns null

        val savedEntities = mutableListOf<EmployeeShiftSchedule>()
        every { scheduleRepository.save(any()) } answers {
            val entity = firstArg<EmployeeShiftSchedule>()
            savedEntities.add(entity)
            entity
        }

        val generatedCount = service.generateDefaultSchedules(
            employeeIds = listOf(employeeId),
            defaultShiftId = shiftId,
            startDate = startDate,
            endDate = endDate,
        )

        assertThat(generatedCount).isEqualTo(7)
        assertThat(savedEntities).hasSize(7)

        val weekdays = savedEntities.filter { !it.isRestDay }
        val restDays = savedEntities.filter { it.isRestDay }

        assertThat(weekdays).hasSize(5)
        assertThat(restDays).hasSize(2)
        assertThat(restDays.map { it.workDate.dayOfWeek.name }).containsExactlyInAnyOrder("SATURDAY", "SUNDAY")
    }

    @Test
    fun `assigns custom holiday schedule`() {
        val holidayDate = LocalDate.of(2026, 3, 11) // Maha Shivaratri
        val assignment = ShiftScheduleAssignment(
            id = UUID.randomUUID(),
            employeeId = employeeId,
            workDate = holidayDate,
            shiftId = null,
            isRestDay = false,
            isHoliday = true,
            holidayName = "Maha Shivaratri Day",
            source = ScheduleSource.MANUAL,
        )

        val entity = EmployeeShiftSchedule(
            employeeId = employeeId,
            workDate = holidayDate,
            shiftId = null,
            isRestDay = false,
            isHoliday = true,
            holidayName = "Maha Shivaratri Day",
            source = ScheduleSource.MANUAL,
        )

        every { scheduleRepository.findByEmployeeIdAndWorkDate(employeeId, holidayDate) } returns null
        every { scheduleRepository.save(any()) } returns entity

        val result = service.assignSchedule(assignment)
        assertThat(result.isHoliday).isTrue()
        assertThat(result.holidayName).isEqualTo("Maha Shivaratri Day")
        assertThat(result.shiftId).isNull()
    }
}
