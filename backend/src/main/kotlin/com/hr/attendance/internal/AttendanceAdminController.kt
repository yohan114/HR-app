package com.hr.attendance.internal

import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/v1/attendance")
@PreAuthorize("hasAnyAuthority('attendance.record.view', 'attendance.record.manage', 'attendance.shift.view', 'attendance.shift.manage')")
class AttendanceAdminController(
    private val service: AttendanceAdminService,
) {
    @GetMapping("/shifts")
    fun listShifts(): ShiftListResponseDto =
        ShiftListResponseDto(service.listShifts())

    @GetMapping("/roster")
    fun getRoster(
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
        @RequestParam(required = false) department: String?,
    ): RosterResponseDto =
        service.getRoster(startDate, endDate, department)

    @PostMapping("/shifts/assign")
    fun assignShift(
        @RequestBody payload: AssignShiftPayloadDto,
    ): ShiftScheduleItemDto =
        service.assignShift(payload)

    @GetMapping("/punches")
    fun listPunches(
        @RequestParam(required = false) date: LocalDate?,
        @RequestParam(required = false) employeeId: UUID?,
        @RequestParam(required = false) flag: String?,
        @RequestParam(required = false) source: String?,
    ): RawPunchListResponseDto =
        RawPunchListResponseDto(service.listPunches(date, employeeId, flag, source))

    @PostMapping("/punches/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    fun ingestPunchAdmin(
        @RequestBody payload: IngestPunchAdminPayloadDto,
    ): RawPunchAdminDto =
        service.ingestPunch(payload)

    @GetMapping("/daily")
    fun listDailyAttendance(
        @RequestParam(required = false) date: LocalDate?,
        @RequestParam(required = false) department: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) anomaly: String?,
    ): DailyAttendanceListResponseDto =
        service.listDailyAttendance(date, department, status, anomaly)

    @GetMapping("/daily/{id}")
    fun getDailyAttendanceDetails(
        @PathVariable id: UUID,
    ): DailyAttendanceAdminDto =
        service.getDailyAttendanceDetails(id)

    @PostMapping("/recompute")
    fun recomputeAttendance(
        @RequestBody(required = false) payload: RecomputeAttendancePayloadDto?,
    ): RecomputeAttendanceResponseDto =
        RecomputeAttendanceResponseDto(service.recomputeAttendance(payload?.date, payload?.employeeId))

    @GetMapping("/payroll-variable-summary")
    fun getPayrollVariableSummary(
        @RequestParam(required = false) payPeriodId: String?,
    ): AttendancePayrollSummaryResponseDto =
        service.getPayrollVariableSummary(payPeriodId)
}
