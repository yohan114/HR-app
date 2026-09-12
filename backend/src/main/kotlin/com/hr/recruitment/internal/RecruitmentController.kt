package com.hr.recruitment.internal

import com.hr.recruitment.*
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/recruitment")
@PreAuthorize("hasAnyAuthority('recruitment.job.view', 'recruitment.job.manage', 'recruitment.candidate.view', 'recruitment.candidate.manage')")
class RecruitmentController(
    private val vacancyService: VacancyService,
    private val applicationPipelineService: ApplicationPipelineService,
    private val interviewService: InterviewService,
) {

    // -------------------------------------------------------------------------
    // Vacancies
    // -------------------------------------------------------------------------

    @GetMapping("/vacancies")
    fun getVacancies(
        @RequestParam(value = "status", required = false) status: VacancyStatus?,
        @RequestParam(value = "departmentId", required = false) departmentId: UUID?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): VacancyListResponseDto {
        val tenantId = resolveTenantId(jwt)
        return vacancyService.getVacancies(status = status, departmentId = departmentId, tenantId = tenantId)
    }

    @PostMapping("/vacancies")
    @ResponseStatus(HttpStatus.CREATED)
    fun createVacancy(
        @RequestBody request: VacancyCreateRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): VacancyItemDto {
        val tenantId = resolveTenantId(jwt)
        return vacancyService.createVacancy(request, tenantId)
    }

    @GetMapping("/vacancies/{id}")
    fun getVacancyById(
        @PathVariable("id") id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): VacancyItemDto {
        val tenantId = resolveTenantId(jwt)
        return vacancyService.getVacancyById(id, tenantId)
    }

    // -------------------------------------------------------------------------
    // Candidates
    // -------------------------------------------------------------------------

    @GetMapping("/candidates")
    fun getCandidates(
        @RequestParam(value = "query", required = false) query: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CandidateListResponseDto {
        val tenantId = resolveTenantId(jwt)
        return applicationPipelineService.getCandidates(query, tenantId)
    }

    @PostMapping("/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCandidate(
        @RequestBody request: CandidateCreateRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): CandidateItemDto {
        val tenantId = resolveTenantId(jwt)
        return applicationPipelineService.createCandidate(request, tenantId)
    }

    // -------------------------------------------------------------------------
    // Applications
    // -------------------------------------------------------------------------

    @GetMapping("/applications")
    fun getApplications(
        @RequestParam(value = "vacancyId", required = false) vacancyId: UUID?,
        @RequestParam(value = "stage", required = false) stage: ApplicationStage?,
        @RequestParam(value = "status", required = false) status: ApplicationStatus?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ApplicationListResponseDto {
        val tenantId = resolveTenantId(jwt)
        return applicationPipelineService.getApplications(
            vacancyId = vacancyId,
            stage = stage,
            status = status,
            tenantId = tenantId,
        )
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    fun createApplication(
        @RequestBody request: ApplicationCreateRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ApplicationItemDto {
        val tenantId = resolveTenantId(jwt)
        return applicationPipelineService.createApplication(request, tenantId)
    }

    @PostMapping("/applications/{id}/stage")
    fun updateApplicationStage(
        @PathVariable("id") id: UUID,
        @RequestBody request: ApplicationStageUpdateRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ApplicationItemDto {
        val tenantId = resolveTenantId(jwt)
        return applicationPipelineService.updateApplicationStage(id, request, tenantId)
    }

    // -------------------------------------------------------------------------
    // Interviews & Scorecards
    // -------------------------------------------------------------------------

    @GetMapping("/interviews")
    fun getInterviews(
        @RequestParam(value = "applicationId", required = false) applicationId: UUID?,
        @RequestParam(value = "interviewerEmployeeId", required = false) interviewerEmployeeId: UUID?,
        @RequestParam(value = "status", required = false) status: InterviewStatus?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): InterviewListResponseDto {
        val tenantId = resolveTenantId(jwt)
        return interviewService.getInterviews(
            applicationId = applicationId,
            interviewerEmployeeId = interviewerEmployeeId,
            status = status,
            tenantId = tenantId,
        )
    }

    @PostMapping("/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    fun scheduleInterview(
        @RequestBody request: InterviewScheduleRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): InterviewItemDto {
        val tenantId = resolveTenantId(jwt)
        return interviewService.scheduleInterview(request, tenantId)
    }

    @PostMapping("/interviews/{id}/scorecard")
    @ResponseStatus(HttpStatus.CREATED)
    fun submitInterviewScorecard(
        @PathVariable("id") id: UUID,
        @RequestBody request: ScorecardSubmitRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): InterviewScorecardItemDto {
        val tenantId = resolveTenantId(jwt)
        val actorEmployeeId = resolveEmployeeId(jwt)
        return interviewService.submitScorecard(
            interviewId = id,
            req = request,
            interviewerEmployeeId = actorEmployeeId,
            tenantId = tenantId,
        )
    }

    // -------------------------------------------------------------------------
    // Offers
    // -------------------------------------------------------------------------

    @GetMapping("/applications/{id}/offer")
    fun getApplicationOffer(
        @PathVariable("id") id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): OfferDetailDto {
        val tenantId = resolveTenantId(jwt)
        return applicationPipelineService.getApplicationOffer(id, tenantId)
    }

    @PostMapping("/applications/{id}/offer")
    @ResponseStatus(HttpStatus.CREATED)
    fun createApplicationOffer(
        @PathVariable("id") id: UUID,
        @RequestBody request: OfferCreateRequestDto,
        @AuthenticationPrincipal jwt: Jwt?,
    ): OfferDetailDto {
        val tenantId = resolveTenantId(jwt)
        return applicationPipelineService.createApplicationOffer(id, request, tenantId)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun resolveEmployeeId(jwt: Jwt?): UUID {
        val claim = jwt?.getClaimAsString("employee_id")
        return if (!claim.isNullOrBlank()) {
            UUID.fromString(claim)
        } else {
            UUID.fromString("00000000-0000-0000-0000-000000000001")
        }
    }

    private fun resolveTenantId(jwt: Jwt?): UUID {
        val claim = jwt?.getClaimAsString("tenant_id")
        return if (!claim.isNullOrBlank()) {
            UUID.fromString(claim)
        } else {
            UUID.fromString("00000000-0000-0000-0000-000000000001")
        }
    }
}
