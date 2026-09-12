package com.hr.onboarding.internal

import com.hr.onboarding.*
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/offboarding")
@PreAuthorize("hasAnyAuthority('offboarding.task.view', 'offboarding.task.manage', 'ADMIN', 'ROLE_ADMIN', 'ROLE_HR_ADMIN')")
class OffboardingController(
    private val offboardingService: OffboardingService,
) {

    @GetMapping("/exit-types")
    fun getExitTypes(): ExitTypeListResponse {
        return offboardingService.getExitTypes()
    }

    @GetMapping("/exit-notices")
    fun getExitNotices(
        @RequestParam(name = "employeeId", required = false) employeeId: UUID?,
        @RequestParam(name = "status", required = false) status: ExitNoticeStatus?,
    ): ExitNoticeListResponse {
        return offboardingService.getExitNotices(employeeId = employeeId, status = status)
    }

    @PostMapping("/exit-notices")
    @ResponseStatus(HttpStatus.CREATED)
    fun createExitNotice(
        @RequestBody request: ExitNoticeCreateRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ExitNoticeItem {
        val employeeId = resolveEmployeeId(jwt)
        return offboardingService.createExitNotice(employeeId = employeeId, request = request)
    }

    @PostMapping("/exit-notices/{id}/approve")
    fun approveExitNotice(
        @PathVariable("id") id: UUID,
        @RequestBody request: ExitNoticeApproveRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ExitNoticeItem {
        val approverId = resolveEmployeeId(jwt)
        return offboardingService.approveExitNotice(id = id, request = request, actorEmployeeId = approverId)
    }

    @PostMapping("/exit-notices/{id}/reject")
    fun rejectExitNotice(
        @PathVariable("id") id: UUID,
        @RequestBody(required = false) request: Map<String, String>?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ExitNoticeItem {
        val approverId = resolveEmployeeId(jwt)
        val reason = request?.get("reason") ?: request?.get("remarks") ?: "Resignation notice rejected/rescinded by manager"
        return offboardingService.rejectExitNotice(id = id, reason = reason, actorEmployeeId = approverId)
    }

    @GetMapping("/exit-notices/{id}/clearance")
    fun getExitNoticeClearance(
        @PathVariable("id") id: UUID,
    ): ClearanceDetailResponse {
        return offboardingService.getClearance(exitNoticeId = id)
    }

    @PostMapping("/clearance-tasks/{id}/status")
    fun updateClearanceTaskStatus(
        @PathVariable("id") id: UUID,
        @RequestBody request: ClearanceTaskStatusUpdateRequest,
    ): ClearanceTaskItem {
        return offboardingService.updateClearanceTaskStatus(taskId = id, request = request)
    }

    @GetMapping("/exit-notices/{id}/interview")
    fun getExitInterview(
        @PathVariable("id") id: UUID,
    ): ExitInterviewItem {
        return offboardingService.getExitInterview(exitNoticeId = id)
    }

    @PostMapping("/exit-notices/{id}/interview")
    @ResponseStatus(HttpStatus.CREATED)
    fun submitExitInterview(
        @PathVariable("id") id: UUID,
        @RequestBody request: ExitInterviewSubmitRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ExitInterviewItem {
        val actorId = resolveEmployeeId(jwt)
        return offboardingService.submitExitInterview(exitNoticeId = id, request = request, actorEmployeeId = actorId)
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
