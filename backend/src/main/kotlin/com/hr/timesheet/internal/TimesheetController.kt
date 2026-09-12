package com.hr.timesheet.internal

import com.hr.timesheet.*
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/v1/timesheets")
@PreAuthorize("isAuthenticated()")
class TimesheetController(
    private val timesheetService: TimesheetService,
) {

    // --- Clients -------------------------------------------------------------

    @GetMapping("/clients")
    fun listClients(): TimesheetClientListResponse {
        return timesheetService.listClients()
    }

    // --- Projects ------------------------------------------------------------

    @GetMapping("/projects")
    fun listProjects(
        @RequestParam(name = "clientId", required = false) clientId: UUID?,
    ): TimesheetProjectListResponse {
        return timesheetService.listProjects(clientId)
    }

    @PostMapping("/projects")
    fun createProject(
        @RequestBody request: TimesheetProjectCreateRequest,
    ): ResponseEntity<TimesheetProjectItem> {
        val created = timesheetService.createProject(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    // --- Activities ----------------------------------------------------------

    @GetMapping("/projects/{id}/activities")
    fun listActivitiesForProject(
        @PathVariable("id") projectId: UUID,
    ): TimesheetActivityListResponse {
        return timesheetService.listActivities(projectId)
    }

    @GetMapping("/activities")
    fun listActivities(
        @RequestParam(name = "projectId", required = false) projectId: UUID?,
    ): TimesheetActivityListResponse {
        return timesheetService.listActivities(projectId)
    }

    // --- Timesheets ----------------------------------------------------------

    @GetMapping("/my")
    fun listMyTimesheets(
        @RequestParam(name = "employeeId", required = false) employeeId: UUID?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): TimesheetListResponse {
        val targetEmpId = employeeId ?: resolveEmployeeId(jwt)
        return timesheetService.listTimesheets(targetEmpId, null)
    }

    @GetMapping
    fun listTimesheets(
        @RequestParam(name = "employeeId", required = false) employeeId: UUID?,
        @RequestParam(name = "status", required = false) status: String?,
    ): TimesheetListResponse {
        return timesheetService.listTimesheets(employeeId, status)
    }

    @PostMapping
    fun saveTimesheet(
        @RequestBody request: TimesheetSaveRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<TimesheetDetailResponse> {
        val actorId = resolveEmployeeId(jwt)
        val saved = timesheetService.saveTimesheet(request, actorId)
        return ResponseEntity.ok(saved)
    }

    @GetMapping("/{id}")
    fun getTimesheet(
        @PathVariable("id") id: UUID,
    ): TimesheetDetailResponse {
        return timesheetService.getTimesheet(id)
    }

    @PostMapping("/{id}/submit")
    fun submitTimesheet(
        @PathVariable("id") id: UUID,
    ): TimesheetDetailResponse {
        return timesheetService.submitTimesheet(id)
    }

    @PostMapping("/{id}/approve")
    fun approveTimesheet(
        @PathVariable("id") id: UUID,
        @RequestBody(required = false) request: TimesheetApproveRequest?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): TimesheetDetailResponse {
        val approverId = resolveEmployeeId(jwt)
        return timesheetService.approveTimesheet(id, approverId, request?.comments)
    }

    @PostMapping("/{id}/reject")
    fun rejectTimesheet(
        @PathVariable("id") id: UUID,
        @RequestBody request: TimesheetRejectRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): TimesheetDetailResponse {
        val approverId = resolveEmployeeId(jwt)
        return timesheetService.rejectTimesheet(id, approverId, request.reason)
    }

    @PostMapping("/copy-previous")
    fun copyPreviousTimesheet(
        @RequestBody request: TimesheetCopyPreviousRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): TimesheetDetailResponse {
        val actorId = resolveEmployeeId(jwt)
        return timesheetService.copyPreviousTimesheet(request, actorId)
    }

    @GetMapping("/reconciliation")
    fun reconcileWithAttendance(
        @RequestParam(name = "employeeId", required = false) employeeId: UUID? = null,
        @RequestParam(name = "weekStart", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekStart: LocalDate? = null,
        @RequestParam(name = "weekStartDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekStartDate: LocalDate? = null,
        @RequestParam(name = "periodStart", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) periodStart: LocalDate? = null,
        @AuthenticationPrincipal jwt: Jwt? = null,
    ): TimesheetReconciliationResponse {
        val targetDate = weekStart ?: weekStartDate ?: periodStart ?: LocalDate.now()
        val targetEmpId = employeeId ?: resolveEmployeeId(jwt)
        return timesheetService.reconcileWithAttendance(targetEmpId, targetDate)
    }

    private fun resolveEmployeeId(jwt: Jwt?): UUID {
        val claim = jwt?.getClaimAsString("employee_id")
        return if (!claim.isNullOrBlank()) {
            UUID.fromString(claim)
        } else {
            UUID.fromString("00000000-0000-0000-0000-000000000010")
        }
    }
}
