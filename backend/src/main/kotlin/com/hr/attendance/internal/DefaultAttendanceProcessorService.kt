package com.hr.attendance.internal

import com.hr.attendance.AttendanceProcessorService
import com.hr.attendance.DailyAttendanceRecord
import com.hr.attendance.DayStatus
import com.hr.attendance.GeofenceStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional
class DefaultAttendanceProcessorService(
    private val shiftRepository: ShiftRepository,
    private val scheduleRepository: EmployeeShiftScheduleRepository,
    private val rawPunchRepository: RawPunchRepository,
    private val dailyAttendanceRepository: DailyAttendanceRepository,
) : AttendanceProcessorService {

    private val defaultZone: ZoneId = ZoneId.of("Asia/Colombo")

    override fun computeDayAttendance(employeeId: UUID, workDate: LocalDate): DailyAttendanceRecord {
        val schedule = scheduleRepository.findByEmployeeIdAndWorkDate(employeeId, workDate)
        val shift = schedule?.shiftId?.let { shiftRepository.findById(it).orElse(null) }

        // Time window for punches on this work date:
        // Encompasses early morning arrivals (e.g. from 04:00) to late evening departures (up to 03:00 next day)
        val dayStart = workDate.atStartOfDay(defaultZone).toInstant()
        val dayEnd = workDate.plusDays(1).atStartOfDay(defaultZone).plusHours(3).toInstant()

        val punches = rawPunchRepository.findAllByEmployeeIdAndPunchedAtBetweenOrderByPunchedAtAsc(
            employeeId,
            dayStart,
            dayEnd,
        )

        val firstInPunch = punches.firstOrNull()
        val lastOutPunch = if (punches.size > 1) punches.last() else null

        val firstInAt = firstInPunch?.punchedAt
        val lastOutAt = lastOutPunch?.punchedAt

        val traceSteps = mutableListOf<String>()
        val anomalyFlags = mutableSetOf<String>()

        // Check for punch anomalies
        if (punches.size == 1 && firstInPunch != null) {
            anomalyFlags.add("MISSING_OUT_PUNCH")
            traceSteps.add("⚠️ Single clock event detected (${firstInPunch.punchedAt}). Missing paired checkout punch.")
        }
        if (punches.any { it.isMockLocation }) {
            anomalyFlags.add("MOCK_LOCATION")
            traceSteps.add("⚠️ Mock/spoofed GPS provider flagged on one or more punches.")
        }
        if (punches.any { it.geofenceStatus == GeofenceStatus.OUTSIDE }) {
            anomalyFlags.add("GEOFENCE_VIOLATION")
            traceSteps.add("⚠️ Punch recorded outside designated branch geofence boundary.")
        }

        var grossMinutes = 0
        var breakMinutes = 0
        var netWorkedMinutes = 0
        var lateMinutes = 0
        var earlyLeaveMinutes = 0
        var otNormalMinutes = 0
        var otRestDayMinutes = 0
        var otHolidayMinutes = 0
        val dayStatus: DayStatus

        // Case 1: Scheduled Rest Day (Weekend or Roster Rest)
        if (schedule?.isRestDay == true) {
            if (firstInAt != null && lastOutAt != null) {
                grossMinutes = Duration.between(firstInAt, lastOutAt).toMinutes().toInt()
                breakMinutes = if (grossMinutes >= 240) 60 else 0
                netWorkedMinutes = maxOf(0, grossMinutes - breakMinutes)
                otRestDayMinutes = netWorkedMinutes
                dayStatus = DayStatus.REST_DAY
                traceSteps.add("🗓️ Scheduled Rest Day worked: $netWorkedMinutes net minutes credited to Rest Day Overtime (2.0x rate).")
            } else {
                dayStatus = DayStatus.REST_DAY
                traceSteps.add("🗓️ Scheduled Rest Day. No work required.")
            }
        }
        // Case 2: Public Holiday
        else if (schedule?.isHoliday == true) {
            if (firstInAt != null && lastOutAt != null) {
                grossMinutes = Duration.between(firstInAt, lastOutAt).toMinutes().toInt()
                breakMinutes = if (grossMinutes >= 240) 60 else 0
                netWorkedMinutes = maxOf(0, grossMinutes - breakMinutes)
                otHolidayMinutes = netWorkedMinutes
                dayStatus = DayStatus.HOLIDAY
                traceSteps.add("🎉 Public Holiday [${schedule.holidayName ?: "Holiday"}] worked: $netWorkedMinutes net minutes credited to Holiday Overtime (2.5x rate).")
            } else {
                dayStatus = DayStatus.HOLIDAY
                traceSteps.add("🎉 Public Holiday [${schedule.holidayName ?: "Holiday"}]. Standard paid public holiday.")
            }
        }
        // Case 3: Standard Working Day
        else {
            if (firstInAt == null) {
                dayStatus = DayStatus.ABSENT
                traceSteps.add("❌ No clock events recorded for scheduled working day. Marked ABSENT.")
            } else {
                val effectiveShift = shift ?: Shift(
                    code = "STD_DEFAULT",
                    name = "Standard 8h Shift",
                    startTime = LocalTime.of(8, 30),
                    endTime = LocalTime.of(17, 30),
                    workingMinutes = 480,
                    breakMinutes = 60,
                    graceInMinutes = 15,
                    graceOutMinutes = 10,
                )

                val inLocalTime = firstInAt.atZone(defaultZone).toLocalTime()
                val graceLimitIn = effectiveShift.startTime.plusMinutes(effectiveShift.graceInMinutes.toLong())

                // Lateness evaluation
                if (inLocalTime.isAfter(graceLimitIn)) {
                    lateMinutes = Duration.between(effectiveShift.startTime, inLocalTime).toMinutes().toInt()
                    traceSteps.add("⏰ Late check-in: Arrived at $inLocalTime (Shift start: ${effectiveShift.startTime}, Grace limit: $graceLimitIn). Late duration: $lateMinutes minutes.")
                } else {
                    lateMinutes = 0
                    traceSteps.add("✅ On-time check-in: Arrived at $inLocalTime (within ${effectiveShift.graceInMinutes}m grace window).")
                }

                // Early departure evaluation
                if (lastOutAt != null) {
                    val outLocalTime = lastOutAt.atZone(defaultZone).toLocalTime()
                    val graceLimitOut = effectiveShift.endTime.minusMinutes(effectiveShift.graceOutMinutes.toLong())

                    if (outLocalTime.isBefore(graceLimitOut)) {
                        earlyLeaveMinutes = Duration.between(outLocalTime, effectiveShift.endTime).toMinutes().toInt()
                        traceSteps.add("🚪 Early departure: Checked out at $outLocalTime (Shift end: ${effectiveShift.endTime}, Grace limit: $graceLimitOut). Early duration: $earlyLeaveMinutes minutes.")
                    } else {
                        earlyLeaveMinutes = 0
                        traceSteps.add("✅ Shift completion: Checked out at $outLocalTime.")
                    }

                    grossMinutes = Duration.between(firstInAt, lastOutAt).toMinutes().toInt()
                    breakMinutes = if (grossMinutes >= effectiveShift.halfDayThresholdMinutes) effectiveShift.breakMinutes else 0
                    netWorkedMinutes = maxOf(0, grossMinutes - breakMinutes)
                    traceSteps.add("⏱️ Duration: $grossMinutes gross minutes - $breakMinutes break minutes = $netWorkedMinutes net worked minutes.")

                    // Overtime calculation
                    if (effectiveShift.otEligible && netWorkedMinutes > effectiveShift.otStartAfterMinutes) {
                        val qualifyingOt = netWorkedMinutes - effectiveShift.otStartAfterMinutes
                        if (qualifyingOt >= effectiveShift.minOtMinutes) {
                            otNormalMinutes = qualifyingOt
                            traceSteps.add("⚡ Overtime: $otNormalMinutes minutes exceeded shift duration (>= ${effectiveShift.minOtMinutes}m threshold). Credited at 1.5x regular rate.")
                        } else {
                            traceSteps.add("ℹ️ Worked $qualifyingOt min beyond shift, but under minimum ${effectiveShift.minOtMinutes}m OT qualifying threshold.")
                        }
                    }

                    // Status resolution
                    val graceAllowance = if (lateMinutes == 0 && earlyLeaveMinutes == 0) effectiveShift.graceInMinutes else 0
                    dayStatus = if (netWorkedMinutes + graceAllowance >= effectiveShift.workingMinutes) {
                        DayStatus.PRESENT
                    } else if (netWorkedMinutes >= effectiveShift.halfDayThresholdMinutes) {
                        DayStatus.HALF_DAY
                    } else {
                        DayStatus.ABSENT
                    }
                } else {
                    // Only 1 punch: estimated duration
                    dayStatus = DayStatus.PRESENT
                    grossMinutes = effectiveShift.workingMinutes
                    netWorkedMinutes = effectiveShift.workingMinutes
                    traceSteps.add("ℹ️ Single punch: defaulting to scheduled shift duration pending regularization.")
                }
            }
        }

        // Persist or update DailyAttendance
        val existing = dailyAttendanceRepository.findByEmployeeIdAndWorkDate(employeeId, workDate)
        val entity = existing ?: DailyAttendance(
            employeeId = employeeId,
            workDate = workDate,
        )

        entity.shiftId = shift?.id
        entity.firstInAt = firstInAt
        entity.lastOutAt = lastOutAt
        entity.grossDurationMinutes = grossMinutes
        entity.breakMinutes = breakMinutes
        entity.netWorkedMinutes = netWorkedMinutes
        entity.lateMinutes = lateMinutes
        entity.earlyLeaveMinutes = earlyLeaveMinutes
        entity.overtimeMinutesNormal = otNormalMinutes
        entity.overtimeMinutesRestDay = otRestDayMinutes
        entity.overtimeMinutesHoliday = otHolidayMinutes
        entity.dayStatus = dayStatus
        entity.anomalyFlags = anomalyFlags.joinToString(",")
        entity.calculationTrace = traceSteps.joinToString("\n")
        entity.computedAt = Instant.now()

        val saved = dailyAttendanceRepository.save(entity)
        return saved.toDto()
    }

    override fun recomputePeriodAttendance(
        employeeId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<DailyAttendanceRecord> {
        val results = mutableListOf<DailyAttendanceRecord>()
        var curr = startDate
        while (!curr.isAfter(endDate)) {
            results.add(computeDayAttendance(employeeId, curr))
            curr = curr.plusDays(1)
        }
        return results
    }

    @Transactional(readOnly = true)
    override fun getAttendanceRecord(employeeId: UUID, workDate: LocalDate): DailyAttendanceRecord? =
        dailyAttendanceRepository.findByEmployeeIdAndWorkDate(employeeId, workDate)?.toDto()

    @Transactional(readOnly = true)
    override fun getAttendanceRecords(employeeId: UUID, startDate: LocalDate, endDate: LocalDate): List<DailyAttendanceRecord> =
        dailyAttendanceRepository.findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, startDate, endDate).map { it.toDto() }

    private fun DailyAttendance.toDto() = DailyAttendanceRecord(
        id = id ?: UUID.randomUUID(),
        employeeId = employeeId,
        workDate = workDate,
        shiftId = shiftId,
        firstInAt = firstInAt,
        lastOutAt = lastOutAt,
        grossDurationMinutes = grossDurationMinutes,
        breakMinutes = breakMinutes,
        netWorkedMinutes = netWorkedMinutes,
        lateMinutes = lateMinutes,
        earlyLeaveMinutes = earlyLeaveMinutes,
        overtimeMinutesNormal = overtimeMinutesNormal,
        overtimeMinutesRestDay = overtimeMinutesRestDay,
        overtimeMinutesHoliday = overtimeMinutesHoliday,
        dayStatus = dayStatus,
        leaveTypeId = leaveTypeId,
        leaveDays = leaveDays,
        anomalyFlags = if (anomalyFlags.isBlank()) emptyList() else anomalyFlags.split(","),
        calculationTrace = calculationTrace,
        computedAt = computedAt,
    )
}
