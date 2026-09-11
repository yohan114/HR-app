package com.hr.attendance.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
interface ShiftRepository : JpaRepository<Shift, UUID> {
    fun findByCode(code: String): Shift?
    fun findAllByIsActiveTrue(): List<Shift>
}

@Repository
interface AttendancePolicyRepository : JpaRepository<AttendancePolicy, UUID>

@Repository
interface EmployeeShiftScheduleRepository : JpaRepository<EmployeeShiftSchedule, UUID> {
    fun findByEmployeeIdAndWorkDate(employeeId: UUID, workDate: LocalDate): EmployeeShiftSchedule?
    fun findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
        employeeId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<EmployeeShiftSchedule>
    fun findAllByWorkDate(workDate: LocalDate): List<EmployeeShiftSchedule>
}

@Repository
interface RawPunchRepository : JpaRepository<RawPunch, UUID> {
    fun findByClientIdempotencyKey(key: String): RawPunch?
    fun findAllByEmployeeIdAndPunchedAtBetweenOrderByPunchedAtAsc(
        employeeId: UUID,
        start: Instant,
        end: Instant,
    ): List<RawPunch>
}

@Repository
interface DailyAttendanceRepository : JpaRepository<DailyAttendance, UUID> {
    fun findByEmployeeIdAndWorkDate(employeeId: UUID, workDate: LocalDate): DailyAttendance?
    fun findAllByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
        employeeId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<DailyAttendance>
}
