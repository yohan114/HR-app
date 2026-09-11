package com.hr.onboarding.internal

import com.hr.onboarding.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/onboarding")
class OnboardingController(
    private val onboardingService: OnboardingService,
) {

    @GetMapping("/profiles")
    fun getOnboardingProfiles(): OnboardingProfileListResponse {
        return onboardingService.getProfiles()
    }

    @GetMapping("/instances")
    fun getOnboardingInstances(
        @RequestParam(name = "employeeId", required = false) employeeId: UUID?,
        @RequestParam(name = "status", required = false) status: OnboardingInstanceStatus?,
    ): OnboardingInstanceListResponse {
        return onboardingService.getInstances(employeeId = employeeId, status = status)
    }

    @PostMapping("/instances")
    @ResponseStatus(HttpStatus.CREATED)
    fun createOnboardingInstance(
        @RequestBody request: OnboardingInstanceCreateRequest,
    ): OnboardingInstanceDetail {
        return onboardingService.createInstance(request)
    }

    @GetMapping("/instances/{id}")
    fun getOnboardingInstanceById(
        @PathVariable("id") id: UUID,
    ): OnboardingInstanceDetail {
        return onboardingService.getInstanceById(id)
    }

    @PostMapping("/tasks/{id}/complete")
    fun completeOnboardingTask(
        @PathVariable("id") id: UUID,
        @RequestBody(required = false) request: OnboardingTaskCompleteRequest?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): OnboardingTaskItem {
        val actorId = resolveEmployeeId(jwt)
        return onboardingService.completeTask(taskId = id, request = request, actorEmployeeId = actorId)
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
