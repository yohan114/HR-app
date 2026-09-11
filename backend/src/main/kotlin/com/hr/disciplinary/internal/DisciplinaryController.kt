package com.hr.disciplinary.internal

import com.hr.disciplinary.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/disciplinary")
class DisciplinaryController(
    private val disciplinaryService: DisciplinaryService,
) {

    @GetMapping("/types")
    fun getIncidentTypes(): IncidentTypesResponse {
        return disciplinaryService.getIncidentTypes()
    }

    @GetMapping("/incidents")
    fun getDisciplinaryIncidents(
        @RequestParam(value = "employeeId", required = false) employeeId: UUID?,
        @RequestParam(value = "status", required = false) status: IncidentStatus?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): DisciplinaryIncidentListResponse {
        val targetEmployeeId = employeeId ?: resolveEmployeeId(jwt)
        return disciplinaryService.getIncidents(
            employeeId = targetEmployeeId,
            status = status,
        )
    }

    @GetMapping("/incidents/{id}")
    fun getDisciplinaryIncidentById(
        @PathVariable("id") id: UUID,
    ): DisciplinaryIncidentDetailResponse {
        return disciplinaryService.getIncidentById(id = id)
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    fun reportDisciplinaryIncident(
        @RequestBody request: DisciplinaryIncidentReportRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): DisciplinaryIncidentItem {
        val reporterId = resolveEmployeeId(jwt)
        return disciplinaryService.reportIncident(
            reportedByEmployeeId = reporterId,
            request = request,
        )
    }

    @PostMapping("/incidents/{id}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    fun issueCorrectiveAction(
        @PathVariable("id") id: UUID,
        @RequestBody request: IssueCorrectiveActionRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CorrectiveActionItem {
        val authorityId = resolveEmployeeId(jwt)
        return disciplinaryService.issueCorrectiveAction(
            incidentId = id,
            issuedBy = authorityId,
            request = request,
        )
    }

    @PostMapping("/incidents/{id}/journal")
    @ResponseStatus(HttpStatus.CREATED)
    fun addIncidentJournalEntry(
        @PathVariable("id") id: UUID,
        @RequestBody request: AddJournalEntryRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): IncidentJournalEntryItem {
        val investigatorId = resolveEmployeeId(jwt)
        return disciplinaryService.addJournalEntry(
            incidentId = id,
            enteredBy = investigatorId,
            request = request,
        )
    }

    @PostMapping("/actions/{id}/respond")
    fun respondToCorrectiveAction(
        @PathVariable("id") id: UUID,
        @RequestBody request: CorrectiveActionResponseRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CorrectiveActionItem {
        val employeeId = resolveEmployeeId(jwt)
        return disciplinaryService.respondToCorrectiveAction(
            actionId = id,
            employeeId = employeeId,
            request = request,
        )
    }

    @PostMapping("/actions/{id}/appeal")
    @ResponseStatus(HttpStatus.CREATED)
    fun appealCorrectiveAction(
        @PathVariable("id") id: UUID,
        @RequestBody request: DisciplinaryAppealRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): DisciplinaryAppealItem {
        val employeeId = resolveEmployeeId(jwt)
        return disciplinaryService.appealCorrectiveAction(
            actionId = id,
            employeeId = employeeId,
            request = request,
        )
    }

    private fun resolveEmployeeId(jwt: Jwt?): UUID =
        jwt?.getClaimAsString("employee_id")?.let { UUID.fromString(it) }
            ?: UUID.fromString("00000000-0000-0000-0000-000000000001")
}
