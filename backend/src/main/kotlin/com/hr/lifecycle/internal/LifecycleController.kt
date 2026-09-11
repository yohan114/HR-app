package com.hr.lifecycle.internal

import com.hr.lifecycle.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/lifecycle")
class LifecycleController(
    private val lifecycleService: LifecycleService,
) {

    @GetMapping("/movements")
    fun getCareerMovements(
        @RequestParam(value = "employeeId", required = false) employeeId: UUID?,
        @RequestParam(value = "status", required = false) status: MovementStatus?,
        @RequestParam(value = "type", required = false) type: MovementType?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CareerMovementListResponse {
        val targetEmployeeId = employeeId ?: resolveEmployeeId(jwt)
        return lifecycleService.getCareerMovements(
            employeeId = targetEmployeeId,
            status = status,
            type = type,
        )
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    fun proposeCareerMovement(
        @RequestBody request: CareerMovementProposalRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CareerMovementItem {
        val initiatorId = resolveEmployeeId(jwt)
        return lifecycleService.proposeCareerMovement(
            request = request,
            initiatorId = initiatorId,
        )
    }

    @GetMapping("/movements/timeline")
    fun getCareerTimeline(
        @RequestParam(value = "employeeId", required = false) employeeId: UUID?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CareerTimelineResponse {
        val targetEmployeeId = employeeId ?: resolveEmployeeId(jwt)
        return lifecycleService.getCareerTimeline(targetEmployeeId)
    }

    @GetMapping("/movements/{id}")
    fun getCareerMovementById(
        @PathVariable("id") id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CareerMovementItem {
        return lifecycleService.getCareerMovementById(id)
    }

    @PostMapping("/movements/{id}/approve")
    fun approveCareerMovement(
        @PathVariable("id") id: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CareerMovementItem {
        val approverId = resolveEmployeeId(jwt)
        return lifecycleService.approveCareerMovement(
            id = id,
            approverId = approverId,
        )
    }

    @PostMapping("/movements/{id}/revert")
    fun revertCareerMovement(
        @PathVariable("id") id: UUID,
        @RequestBody request: CareerMovementRevertRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CareerMovementItem {
        return lifecycleService.revertCareerMovement(
            id = id,
            reason = request.reversionReason,
        )
    }

    private fun resolveEmployeeId(jwt: Jwt?): UUID =
        jwt?.getClaimAsString("employee_id")?.let { UUID.fromString(it) }
            ?: UUID.fromString("00000000-0000-0000-0000-000000000001")
}
