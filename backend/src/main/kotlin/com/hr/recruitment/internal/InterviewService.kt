package com.hr.recruitment.internal

import com.hr.employee.EmployeeLookupService
import com.hr.recruitment.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class InterviewService(
    private val interviewRepository: RecruitmentInterviewRepository,
    private val panelRepository: RecruitmentInterviewPanelRepository,
    private val scorecardRepository: RecruitmentInterviewScorecardRepository,
    private val applicationRepository: RecruitmentApplicationRepository,
    private val candidateRepository: RecruitmentCandidateRepository,
    private val vacancyRepository: RecruitmentVacancyRepository,
    private val employeeLookupService: EmployeeLookupService,
) {
    private val defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Transactional(readOnly = true)
    fun getInterviews(
        applicationId: UUID?,
        interviewerEmployeeId: UUID?,
        status: InterviewStatus?,
        tenantId: UUID = defaultTenantId,
    ): InterviewListResponseDto {
        val all = interviewRepository.findAllByTenantId(tenantId)
        val panelList = panelRepository.findAll()
        val panelsByInterview = panelList.groupBy { it.interviewId }

        val filtered = all.filter { interview ->
            val matchApp = (applicationId == null || interview.applicationId == applicationId)
            val matchStatus = (status == null || interview.status == status)
            val matchInterviewer = if (interviewerEmployeeId == null) {
                true
            } else {
                panelsByInterview[interview.id]?.any { it.interviewerEmployeeId == interviewerEmployeeId } == true
            }
            matchApp && matchStatus && matchInterviewer
        }

        val items = filtered.map { mapInterviewToDto(it, panelsByInterview[it.id] ?: emptyList(), tenantId) }
        return InterviewListResponseDto(items = items, totalCount = items.size)
    }

    @Transactional
    fun scheduleInterview(req: InterviewScheduleRequestDto, tenantId: UUID = defaultTenantId): InterviewItemDto {
        val interview = RecruitmentInterview(
            applicationId = req.applicationId,
            interviewRound = req.interviewRound,
            title = req.title,
            interviewType = req.interviewType,
            scheduledStart = req.scheduledStart,
            scheduledEnd = req.scheduledEnd,
            locationOrLink = req.locationOrLink,
            meetingLink = req.meetingLink,
            status = InterviewStatus.SCHEDULED,
            notes = req.notes,
        )
        interview.tenantId = tenantId
        val saved = interviewRepository.save(interview)

        // Save panel members
        val panels = req.interviewerEmployeeIds.mapIndexed { idx, empId ->
            val panel = RecruitmentInterviewPanel(
                interviewId = saved.id,
                interviewerEmployeeId = empId,
                isLead = (idx == 0),
            )
            panel.tenantId = tenantId
            panelRepository.save(panel)
        }

        return mapInterviewToDto(saved, panels, tenantId)
    }

    @Transactional
    fun submitScorecard(
        interviewId: UUID,
        req: ScorecardSubmitRequestDto,
        interviewerEmployeeId: UUID,
        tenantId: UUID = defaultTenantId,
    ): InterviewScorecardItemDto {
        val interview = interviewRepository.findById(interviewId).orElse(null)
            ?: throw NoSuchElementException("Interview not found with id $interviewId")

        val existing = scorecardRepository.findByTenantIdAndInterviewIdAndInterviewerEmployeeId(
            tenantId = tenantId,
            interviewId = interviewId,
            interviewerEmployeeId = interviewerEmployeeId,
        )

        val scorecard = existing ?: RecruitmentInterviewScorecard(
            interviewId = interviewId,
            interviewerEmployeeId = interviewerEmployeeId,
            overallRecommendation = req.overallRecommendation,
            technicalSkillRating = req.technicalSkillRating,
            communicationRating = req.communicationRating,
            problemSolvingRating = req.problemSolvingRating,
            culturalFitRating = req.culturalFitRating,
            strengths = req.strengths,
            weaknesses = req.weaknesses,
            summaryNotes = req.summaryNotes,
            submittedAt = Instant.now(),
        ).apply { this.tenantId = tenantId }

        if (existing != null) {
            existing.overallRecommendation = req.overallRecommendation
            existing.technicalSkillRating = req.technicalSkillRating
            existing.communicationRating = req.communicationRating
            existing.problemSolvingRating = req.problemSolvingRating
            existing.culturalFitRating = req.culturalFitRating
            existing.strengths = req.strengths
            existing.weaknesses = req.weaknesses
            existing.summaryNotes = req.summaryNotes
            existing.submittedAt = Instant.now()
        }

        val saved = scorecardRepository.save(scorecard)

        // Mark interview as COMPLETED
        interview.status = InterviewStatus.COMPLETED
        interviewRepository.save(interview)

        val empName = employeeLookupService.findDisplayName(interviewerEmployeeId) ?: "Interviewer"

        return InterviewScorecardItemDto(
            id = saved.id,
            interviewId = saved.interviewId,
            interviewerEmployeeId = saved.interviewerEmployeeId,
            interviewerName = empName,
            overallRecommendation = saved.overallRecommendation,
            technicalSkillRating = saved.technicalSkillRating,
            communicationRating = saved.communicationRating,
            problemSolvingRating = saved.problemSolvingRating,
            culturalFitRating = saved.culturalFitRating,
            strengths = saved.strengths,
            weaknesses = saved.weaknesses,
            summaryNotes = saved.summaryNotes,
            submittedAt = saved.submittedAt,
        )
    }

    private fun mapInterviewToDto(
        interview: RecruitmentInterview,
        panels: List<RecruitmentInterviewPanel>,
        tenantId: UUID,
    ): InterviewItemDto {
        val app = applicationRepository.findById(interview.applicationId).orElse(null)
        val candidate = app?.let { candidateRepository.findById(it.candidateId).orElse(null) }
        val vacancy = app?.let { vacancyRepository.findById(it.vacancyId).orElse(null) }

        val candidateName = candidate?.let { "${it.firstName} ${it.lastName}" } ?: "Candidate"
        val vacancyTitle = vacancy?.title ?: "Open Vacancy"

        val scorecards = scorecardRepository.findByTenantIdAndInterviewId(tenantId, interview.id).map { sc ->
            val empName = employeeLookupService.findDisplayName(sc.interviewerEmployeeId) ?: "Interviewer"
            InterviewScorecardItemDto(
                id = sc.id,
                interviewId = sc.interviewId,
                interviewerEmployeeId = sc.interviewerEmployeeId,
                interviewerName = empName,
                overallRecommendation = sc.overallRecommendation,
                technicalSkillRating = sc.technicalSkillRating,
                communicationRating = sc.communicationRating,
                problemSolvingRating = sc.problemSolvingRating,
                culturalFitRating = sc.culturalFitRating,
                strengths = sc.strengths,
                weaknesses = sc.weaknesses,
                summaryNotes = sc.summaryNotes,
                submittedAt = sc.submittedAt,
            )
        }

        return InterviewItemDto(
            id = interview.id,
            applicationId = interview.applicationId,
            candidateName = candidateName,
            vacancyTitle = vacancyTitle,
            interviewRound = interview.interviewRound,
            title = interview.title,
            interviewType = interview.interviewType,
            scheduledStart = interview.scheduledStart,
            scheduledEnd = interview.scheduledEnd,
            locationOrLink = interview.locationOrLink,
            meetingLink = interview.meetingLink,
            status = interview.status,
            interviewerEmployeeIds = panels.map { it.interviewerEmployeeId },
            notes = interview.notes,
            scorecards = scorecards,
        )
    }
}
