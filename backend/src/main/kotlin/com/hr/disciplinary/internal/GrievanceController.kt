package com.hr.disciplinary.internal

import com.hr.disciplinary.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/grievance")
class GrievanceController(
    private val grievanceService: GrievanceService,
) {

    @GetMapping("/grounds")
    fun getGrievanceGrounds(): GrievanceGroundsResponse {
        return grievanceService.getGrounds()
    }

    @GetMapping("/channels")
    fun getGrievanceChannels(): GrievanceChannelsResponse {
        return grievanceService.getChannels()
    }

    @GetMapping("/my-grievances")
    fun getMyGrievances(
        @RequestParam(value = "status", required = false) status: GrievanceStatus?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): GrievanceListResponse {
        val employeeId = resolveEmployeeId(jwt)
        return grievanceService.getMyGrievances(
            employeeId = employeeId,
            status = status,
        )
    }

    @GetMapping("/{id}")
    fun getGrievanceById(
        @PathVariable("id") id: UUID,
    ): GrievanceDetailResponse {
        return grievanceService.getGrievanceById(id = id)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submitGrievance(
        @RequestBody request: GrievanceSubmitRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): GrievanceDetailResponse {
        val employeeId = resolveEmployeeId(jwt)
        return grievanceService.submitGrievance(
            employeeId = employeeId,
            request = request,
        )
    }

    @PostMapping("/{id}/appeal")
    fun appealGrievance(
        @PathVariable("id") id: UUID,
        @RequestBody request: GrievanceAppealRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): GrievanceDetailResponse {
        val employeeId = resolveEmployeeId(jwt)
        return grievanceService.appealGrievance(
            grievanceId = id,
            employeeId = employeeId,
            request = request,
        )
    }

    private fun resolveEmployeeId(jwt: Jwt?): UUID =
        jwt?.getClaimAsString("employee_id")?.let { UUID.fromString(it) }
            ?: UUID.fromString("00000000-0000-0000-0000-000000000001")
}
