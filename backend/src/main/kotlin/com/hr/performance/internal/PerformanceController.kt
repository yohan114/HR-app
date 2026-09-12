package com.hr.performance.internal

import com.hr.identity.Caller
import com.hr.performance.*
import com.hr.shared.api.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/performance")
@PreAuthorize("isAuthenticated()")
class PerformanceController(
    private val goalService: GoalService,
    private val appraisalService: AppraisalService,
    private val continuousFeedbackService: ContinuousFeedbackService,
) {

    @GetMapping("/goals")
    fun getGoals(
        @RequestParam(value = "employeeId", required = false) employeeId: UUID?,
        @RequestParam(value = "cycleId", required = false) cycleId: UUID?,
        @RequestParam(value = "status", required = false) status: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): GoalListResponseDto {
        val targetEmployeeId = employeeId ?: resolveEmployeeId(jwt)
        return goalService.getGoals(
            employeeId = targetEmployeeId,
            cycleId = cycleId,
            status = status,
        )
    }

    @PostMapping("/goals")
    @ResponseStatus(HttpStatus.CREATED)
    fun createGoal(
        @RequestBody request: GoalCreateRequestDto,
    ): GoalItemDto {
        return goalService.createGoal(request)
    }

    @PostMapping("/goals/{id}/check-in")
    fun recordGoalCheckIn(
        @PathVariable("id") id: UUID,
        @RequestBody request: GoalCheckInRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): GoalItemDto {
        val actorId = resolveEmployeeId(jwt)
        return goalService.recordGoalCheckIn(
            goalId = id,
            request = request,
            actorId = actorId,
        )
    }

    @GetMapping("/competencies")
    fun getCompetencies(): CompetencyFrameworkResponseDto {
        return appraisalService.getCompetencies()
    }

    @GetMapping("/appraisals/my")
    fun getMyAppraisals(
        @RequestParam(value = "cycleId", required = false) cycleId: UUID?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): AppraisalListResponseDto {
        val employeeId = resolveEmployeeId(jwt)
        return appraisalService.getMyAppraisals(
            employeeId = employeeId,
            cycleId = cycleId,
        )
    }

    @GetMapping("/appraisals/team")
    fun getTeamAppraisals(
        @RequestParam(value = "cycleId", required = false) cycleId: UUID?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): AppraisalListResponseDto {
        val managerId = resolveEmployeeId(jwt)
        return appraisalService.getTeamAppraisals(
            managerId = managerId,
            cycleId = cycleId,
        )
    }

    @GetMapping("/appraisals/{id}")
    fun getAppraisalById(
        @PathVariable("id") id: UUID,
    ): AppraisalDetailResponseDto {
        return appraisalService.getAppraisalById(id)
    }

    @PostMapping("/appraisals/{id}/self-review")
    fun submitSelfReview(
        @PathVariable("id") id: UUID,
        @RequestBody request: AppraisalSelfReviewRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): AppraisalDetailResponseDto {
        val employeeId = resolveEmployeeId(jwt)
        return appraisalService.submitSelfReview(
            id = id,
            request = request,
            employeeId = employeeId,
        )
    }

    @PostMapping("/appraisals/{id}/manager-review")
    fun submitManagerReview(
        @PathVariable("id") id: UUID,
        @RequestBody request: AppraisalManagerReviewRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): AppraisalDetailResponseDto {
        val managerId = resolveEmployeeId(jwt)
        return appraisalService.submitManagerReview(
            id = id,
            request = request,
            managerId = managerId,
        )
    }

    @PostMapping("/appraisals/{id}/acknowledge")
    fun acknowledgeAppraisal(
        @PathVariable("id") id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): AppraisalDetailResponseDto {
        val employeeId = resolveEmployeeId(jwt)
        return appraisalService.acknowledgeAppraisal(
            id = id,
            employeeId = employeeId,
        )
    }

    @GetMapping("/feedback")
    fun getContinuousFeedback(
        @RequestParam(value = "employeeId", required = false) employeeId: UUID?,
        @RequestParam(value = "type", required = false) type: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ContinuousFeedbackListResponseDto {
        val targetEmployeeId = employeeId ?: resolveEmployeeId(jwt)
        return continuousFeedbackService.getFeedback(
            employeeId = targetEmployeeId,
            type = type,
        )
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContinuousFeedback(
        @RequestBody request: SendFeedbackRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ContinuousFeedbackItemDto {
        val senderId = resolveEmployeeId(jwt)
        return continuousFeedbackService.sendFeedback(
            senderEmployeeId = senderId,
            request = request,
        )
    }

    private fun resolveEmployeeId(jwt: Jwt?): UUID {
        if (jwt != null) {
            val caller = runCatching { Caller.from(jwt) }.getOrNull()
            if (caller?.employeeId != null) {
                return caller.employeeId
            }
        }
        throw NotFoundException("NO_EMPLOYEE_RECORD", "This account is not linked to an employee record")
    }
}
