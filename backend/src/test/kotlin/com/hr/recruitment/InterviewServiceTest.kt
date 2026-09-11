package com.hr.recruitment

import com.hr.employee.EmployeeLookupService
import com.hr.recruitment.internal.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Recruitment Interview Service Unit Tests")
class InterviewServiceTest {

    private val interviewRepository = mockk<RecruitmentInterviewRepository>()
    private val panelRepository = mockk<RecruitmentInterviewPanelRepository>()
    private val scorecardRepository = mockk<RecruitmentInterviewScorecardRepository>()
    private val applicationRepository = mockk<RecruitmentApplicationRepository>()
    private val candidateRepository = mockk<RecruitmentCandidateRepository>()
    private val vacancyRepository = mockk<RecruitmentVacancyRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)

    private lateinit var interviewService: InterviewService

    private val tenantId = UUID.randomUUID()
    private val applicationId = UUID.randomUUID()
    private val interviewerId = UUID.randomUUID()
    private val interviewId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        interviewService = InterviewService(
            interviewRepository = interviewRepository,
            panelRepository = panelRepository,
            scorecardRepository = scorecardRepository,
            applicationRepository = applicationRepository,
            candidateRepository = candidateRepository,
            vacancyRepository = vacancyRepository,
            employeeLookupService = employeeLookupService,
        )
    }

    @Test
    fun `scheduleInterview creates interview and panel records`() {
        val req = InterviewScheduleRequestDto(
            applicationId = applicationId,
            interviewRound = 1,
            title = "Technical Deep Dive",
            interviewType = InterviewType.TECHNICAL,
            scheduledStart = Instant.now(),
            scheduledEnd = Instant.now().plusSeconds(3600),
            locationOrLink = "Google Meet",
            meetingLink = "https://meet.google.com/test",
            interviewerEmployeeIds = listOf(interviewerId),
        )

        val savedInterview = RecruitmentInterview(
            applicationId = applicationId,
            interviewRound = 1,
            title = req.title,
            interviewType = req.interviewType,
            scheduledStart = req.scheduledStart,
            scheduledEnd = req.scheduledEnd,
            locationOrLink = req.locationOrLink,
            meetingLink = req.meetingLink,
        ).apply { this.tenantId = tenantId }

        every { interviewRepository.save(any()) } returns savedInterview
        every { panelRepository.save(any()) } answers { firstArg() }
        every { applicationRepository.findById(applicationId) } returns Optional.empty()
        every { scorecardRepository.findByTenantIdAndInterviewId(tenantId, any()) } returns emptyList()

        val result = interviewService.scheduleInterview(req, tenantId)

        assertNotNull(result)
        assertEquals("Technical Deep Dive", result.title)
        assertEquals(InterviewStatus.SCHEDULED, result.status)
        assertEquals(listOf(interviewerId), result.interviewerEmployeeIds)
        verify(exactly = 1) { interviewRepository.save(any()) }
        verify(exactly = 1) { panelRepository.save(any()) }
    }

    @Test
    fun `submitScorecard records rating and marks interview COMPLETED`() {
        val interview = RecruitmentInterview(
            applicationId = applicationId,
            interviewRound = 1,
            title = "Architecture Assessment",
            interviewType = InterviewType.SYSTEM_DESIGN,
            scheduledStart = Instant.now().minusSeconds(3600),
            scheduledEnd = Instant.now(),
            status = InterviewStatus.SCHEDULED,
        ).apply { this.tenantId = tenantId }

        val req = ScorecardSubmitRequestDto(
            overallRecommendation = ScorecardRecommendation.STRONG_HIRE,
            technicalSkillRating = BigDecimal("4.80"),
            communicationRating = BigDecimal("4.50"),
            problemSolvingRating = BigDecimal("5.00"),
            culturalFitRating = BigDecimal("4.75"),
            strengths = "Excellent distributed systems knowledge",
            summaryNotes = "Strongly recommend for lead position",
        )

        val savedScorecard = RecruitmentInterviewScorecard(
            interviewId = interviewId,
            interviewerEmployeeId = interviewerId,
            overallRecommendation = req.overallRecommendation,
            technicalSkillRating = req.technicalSkillRating,
            communicationRating = req.communicationRating,
            problemSolvingRating = req.problemSolvingRating,
            culturalFitRating = req.culturalFitRating,
            strengths = req.strengths,
            summaryNotes = req.summaryNotes,
        ).apply { this.tenantId = tenantId }

        every { interviewRepository.findById(interviewId) } returns Optional.of(interview)
        every { scorecardRepository.findByTenantIdAndInterviewIdAndInterviewerEmployeeId(tenantId, interviewId, interviewerId) } returns null
        every { scorecardRepository.save(any()) } returns savedScorecard
        every { interviewRepository.save(interview) } returns interview

        every { employeeLookupService.findDisplayName(interviewerId) } returns "Kasun Mendis"

        val result = interviewService.submitScorecard(interviewId, req, interviewerId, tenantId)

        assertNotNull(result)
        assertEquals(ScorecardRecommendation.STRONG_HIRE, result.overallRecommendation)
        assertEquals("Kasun Mendis", result.interviewerName)
        assertEquals(InterviewStatus.COMPLETED, interview.status)
        verify(exactly = 1) { scorecardRepository.save(any()) }
        verify(exactly = 1) { interviewRepository.save(interview) }
    }
}
