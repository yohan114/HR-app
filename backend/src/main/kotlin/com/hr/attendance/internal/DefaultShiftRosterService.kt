package com.hr.attendance.internal

import com.hr.attendance.ScheduleSource
import com.hr.attendance.ShiftDefinition
import com.hr.attendance.ShiftRosterService
import com.hr.attendance.ShiftScheduleAssignment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional
class DefaultShiftRosterService(
    private val shiftRepository: ShiftRepository,
    private val scheduleRepository: EmployeeShiftScheduleRepository,
) : ShiftRosterService {

    override fun createShift(shift: ShiftDefinition): ShiftDefinition {
        val entity = Shift(
            code = shift.code,
            name = shift.name,
            shiftType = shift.shiftType,
            startTime = shift.startTime,
            endTime = shift.endTime,
            breakMinutes = shift.breakMinutes,
            crossesMidnight = shift.crossesMidnight,
            workingMinutes = shift.workingMinutes,
            graceInMinutes = shift.graceInMinutes,
            graceOutMinutes = shift.graceOutMinutes,
            halfDayThresholdMinutes = shift.halfDayThresholdMinutes,
            otEligible = shift.otEligible,
            otStartAfterMinutes = shift.otStartAfterMinutes,
            minOtMinutes = shift.minOtMinutes,
            color = shift.color,
            isActive = shift.isActive,
        )
        val saved = shiftRepository.save(entity)
        return saved.toDto()
    }

    @Transactional(readOnly = true)
    override fun getShift(id: UUID): ShiftDefinition? =
        shiftRepository.findById(id).map { it.toDto() }.orElse(null)

    @Transactional(readOnly = true)
    override fun listShifts(): List<ShiftDefinition> =
        shiftRepository.findAllByIsActiveTrue().map { it.toDto() }

    override fun assignSchedule(assignment: ShiftScheduleAssignment): ShiftScheduleAssignment {
        val existing = scheduleRepository.findByEmployeeIdAndWorkDate(
            assignment.employeeId,
            assignment.workDate,
        )
        val entity = existing ?: EmployeeShiftSchedule(
            employeeId = assignment.employeeId,
            workDate = assignment.workDate,
            shiftId = assignment.shiftId,
            isRestDay = assignment.isRestDay,
            isHoliday = assignment.isHoliday,
            holidayName = assignment.holidayName,
            source = assignment.source,
        )

        entity.shiftId = assignment.shiftId
        entity.isRestDay = assignment.isRestDay
        entity.isHoliday = assignment.isHoliday
        entity.holidayName = assignment.holidayName
        entity.source = assignment.source

        val saved = scheduleRepository.save(entity)
        val shiftDto = saved.shiftId?.let { getShift(it) }
        return saved.toDto(shiftDto)
    }

    @Transactional(readOnly = true)
    override fun getSchedule(employeeId: UUID, date: LocalDate): ShiftScheduleAssignment? {
        val entity = scheduleRepository.findByEmployeeIdAndWorkDate(employeeId, date) ?: return null
        val shiftDto = entity.shiftId?.let { getShift(it) }
        return entity.toDto(shiftDto)
    }

    @Transactional(readOnly = true)
    override fun getSchedules(
        employeeId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ShiftScheduleAssignment> {
        val entities = scheduleRepository.findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            employeeId,
            startDate,
            endDate,
        )
        val shiftCache = mutableMapOf<UUID, ShiftDefinition?>()
        return entities.map { entity ->
            val shiftDto = entity.shiftId?.let { sId ->
                shiftCache.getOrPut(sId) { getShift(sId) }
            }
            entity.toDto(shiftDto)
        }
    }

    override fun generateDefaultSchedules(
        employeeIds: List<UUID>,
        defaultShiftId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Int {
        var count = 0
        for (empId in employeeIds) {
            var curr = startDate
            while (!curr.isAfter(endDate)) {
                val isWeekend = curr.dayOfWeek == DayOfWeek.SATURDAY || curr.dayOfWeek == DayOfWeek.SUNDAY
                val existing = scheduleRepository.findByEmployeeIdAndWorkDate(empId, curr)
                val entity = existing ?: EmployeeShiftSchedule(
                    employeeId = empId,
                    workDate = curr,
                    shiftId = if (isWeekend) null else defaultShiftId,
                    isRestDay = isWeekend,
                    isHoliday = false,
                    holidayName = null,
                    source = ScheduleSource.DEFAULT_SHIFT,
                )

                if (existing == null) {
                    scheduleRepository.save(entity)
                    count++
                }
                curr = curr.plusDays(1)
            }
        }
        return count
    }

    private fun Shift.toDto() = ShiftDefinition(
        id = id ?: UUID.randomUUID(),
        code = code,
        name = name,
        shiftType = shiftType,
        startTime = startTime,
        endTime = endTime,
        breakMinutes = breakMinutes,
        crossesMidnight = crossesMidnight,
        workingMinutes = workingMinutes,
        graceInMinutes = graceInMinutes,
        graceOutMinutes = graceOutMinutes,
        halfDayThresholdMinutes = halfDayThresholdMinutes,
        otEligible = otEligible,
        otStartAfterMinutes = otStartAfterMinutes,
        minOtMinutes = minOtMinutes,
        color = color,
        isActive = isActive,
    )

    private fun EmployeeShiftSchedule.toDto(shift: ShiftDefinition?) = ShiftScheduleAssignment(
        id = id ?: UUID.randomUUID(),
        employeeId = employeeId,
        workDate = workDate,
        shiftId = shiftId,
        shift = shift,
        isRestDay = isRestDay,
        isHoliday = isHoliday,
        holidayName = holidayName,
        source = source,
    )
}
