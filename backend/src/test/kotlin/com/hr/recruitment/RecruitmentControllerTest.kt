package com.hr.recruitment

import com.hr.recruitment.internal.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

@DisplayName("Recruitment Controller Unit Tests")
class RecruitmentControllerTest {

    private val vacancyService = mockk<VacancyService>()
    private val applicationPipelineService = mockk<ApplicationPipelineService>()
    private val interviewService = mockk<InterviewService>()

    private lateinit var controller: RecruitmentController

    private val tenantId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()
    private val vacancyId = UUID.randomUUID()
    private val candidateId = UUID.randomUUID()
    private val applicationId = UUID.randomUUID()
    private val interviewId = UUID.randomUUID()

    private val mockJwt = mockk<Jwt> {
        every { getClaimAsString("tenant_id") } returns tenantId.toString()
        every { getClaimAsString("employee_id") } returns employeeId.toString()
    }

    @BeforeEach
    fun setUp() {
        controller = RecruitmentController(
            vacancyService = vacancyService,
            applicationPipelineService = applicationPipelineService,
            interviewService = interviewService,
        )
    }

    @Test
    fun `getVacancies delegates to vacancyService`() {
        val expected = VacancyListResponseDto(items = emptyList(), totalCount = 0)
        every { vacancyService.getVacancies(status = any(), departmentId = any(), tenantId = any()) } returns expected

        val res = controller.getVacancies(status = null, departmentId = null, jwt = mockJwt)
        assertEquals(0, res.totalCount)
        verify(exactly = 1) { vacancyService.getVacancies(status = null, departmentId = null, tenantId = tenantId) }
    }

    @Test
    fun `createVacancy delegates to vacancyService`() {
        val req = VacancyCreateRequestDto(
            jobCode = "VAC-001",
            title = "Lead Architect",
            description = "Lead Architecture",
        )
        val expected = VacancyItemDto(
            id = vacancyId,
            jobCode = req.jobCode,
            title = req.title,
            departmentId = null,
            departmentName = null,
            location = "Colombo",
            employmentType = "FULL_TIME",
            experienceLevel = "LEAD",
            minSalary = null,
            maxSalary = null,
            currency = "LKR",
            description = req.description,
            requirements = null,
            openPositions = 1,
            filledPositions = 0,
            targetHireDate = null,
            closingDate = null,
            status = VacancyStatus.OPEN,
            createdAt = Instant.now(),
        )
        every { vacancyService.createVacancy(req, tenantId) } returns expected

        val res = controller.createVacancy(req, mockJwt)
        assertEquals("VAC-001", res.jobCode)
        verify(exactly = 1) { vacancyService.createVacancy(req, tenantId) }
    }

    @Test
    fun `getVacancyById delegates to vacancyService`() {
        val expected = VacancyItemDto(
            id = vacancyId,
            jobCode = "VAC-001",
            title = "Test",
            departmentId = null,
            departmentName = null,
            location = "Colombo",
            employmentType = "FULL_TIME",
            experienceLevel = "LEAD",
            minSalary = null,
            maxSalary = null,
            currency = "LKR",
            description = "Desc",
            requirements = null,
            openPositions = 1,
            filledPositions = 0,
            targetHireDate = null,
            closingDate = null,
            status = VacancyStatus.OPEN,
            createdAt = Instant.now(),
        )
        every { vacancyService.getVacancyById(vacancyId, tenantId) } returns expected

        val res = controller.getVacancyById(vacancyId, mockJwt)
        assertEquals(vacancyId, res.id)
    }

    @Test
    fun `getCandidates delegates to applicationPipelineService`() {
        val expected = CandidateListResponseDto(items = emptyList(), totalCount = 0)
        every { applicationPipelineService.getCandidates(any(), tenantId) } returns expected

        val res = controller.getCandidates("kotlin", mockJwt)
        assertEquals(0, res.totalCount)
    }

    @Test
    fun `createCandidate delegates to applicationPipelineService`() {
        val req = CandidateCreateRequestDto(firstName = "Dulani", lastName = "J", email = "dulani@test.com")
        val expected = CandidateItemDto(
            id = candidateId,
            firstName = req.firstName,
            lastName = req.lastName,
            email = req.email,
            phone = null,
            currentCompany = null,
            currentTitle = null,
            yearsOfExperience = null,
            resumeUrl = null,
            linkedinUrl = null,
            skills = emptyList(),
            notes = null,
            createdAt = Instant.now(),
        )
        every { applicationPipelineService.createCandidate(req, tenantId) } returns expected

        val res = controller.createCandidate(req, mockJwt)
        assertEquals("dulani@test.com", res.email)
    }

    @Test
    fun `getApplications delegates to applicationPipelineService`() {
        val expected = ApplicationListResponseDto(items = emptyList(), totalCount = 0)
        every { applicationPipelineService.getApplications(any(), any(), any(), tenantId) } returns expected

        val res = controller.getApplications(null, null, null, mockJwt)
        assertEquals(0, res.totalCount)
    }

    @Test
    fun `createApplication delegates to applicationPipelineService`() {
        val req = ApplicationCreateRequestDto(vacancyId = vacancyId, candidateId = candidateId)
        val expected = ApplicationItemDto(
            id = applicationId,
            applicationNumber = "APP-001",
            vacancyId = vacancyId,
            vacancyTitle = "Backend Dev",
            candidateId = candidateId,
            candidateName = "Dulani J",
            candidateEmail = "dulani@test.com",
            stage = ApplicationStage.APPLIED,
            status = ApplicationStatus.ACTIVE,
            rating = null,
            source = "CAREERS_PORTAL",
            appliedDate = LocalDate.now(),
            rejectionReason = null,
            createdAt = Instant.now(),
        )
        every { applicationPipelineService.createApplication(req, tenantId) } returns expected

        val res = controller.createApplication(req, mockJwt)
        assertEquals("APP-001", res.applicationNumber)
    }

    @Test
    fun `updateApplicationStage delegates to applicationPipelineService`() {
        val req = ApplicationStageUpdateRequestDto(stage = ApplicationStage.INTERVIEW_ROUND_1)
        val expected = ApplicationItemDto(
            id = applicationId,
            applicationNumber = "APP-001",
            vacancyId = vacancyId,
            vacancyTitle = "Backend Dev",
            candidateId = candidateId,
            candidateName = "Dulani J",
            candidateEmail = "dulani@test.com",
            stage = ApplicationStage.INTERVIEW_ROUND_1,
            status = ApplicationStatus.ACTIVE,
            rating = null,
            source = "CAREERS_PORTAL",
            appliedDate = LocalDate.now(),
            rejectionReason = null,
            createdAt = Instant.now(),
        )
        every { applicationPipelineService.updateApplicationStage(applicationId, req, tenantId) } returns expected

        val res = controller.updateApplicationStage(applicationId, req, mockJwt)
        assertEquals(ApplicationStage.INTERVIEW_ROUND_1, res.stage)
    }

    @Test
    fun `getInterviews delegates to interviewService`() {
        val expected = InterviewListResponseDto(items = emptyList(), totalCount = 0)
        every { interviewService.getInterviews(any(), any(), any(), tenantId) } returns expected

        val res = controller.getInterviews(null, null, null, mockJwt)
        assertEquals(0, res.totalCount)
    }

    @Test
    fun `scheduleInterview delegates to interviewService`() {
        val req = InterviewScheduleRequestDto(
            applicationId = applicationId,
            interviewRound = 1,
            title = "HR Screening",
            interviewType = InterviewType.HR,
            scheduledStart = Instant.now(),
            scheduledEnd = Instant.now().plusSeconds(1800),
        )
        val expected = InterviewItemDto(
            id = interviewId,
            applicationId = applicationId,
            candidateName = "Dulani J",
            vacancyTitle = "Mobile Lead",
            interviewRound = 1,
            title = req.title,
            interviewType = req.interviewType,
            scheduledStart = req.scheduledStart,
            scheduledEnd = req.scheduledEnd,
            locationOrLink = null,
            meetingLink = null,
            status = InterviewStatus.SCHEDULED,
            interviewerEmployeeIds = emptyList(),
            notes = null,
            scorecards = emptyList(),
        )
        every { interviewService.scheduleInterview(req, tenantId) } returns expected

        val res = controller.scheduleInterview(req, mockJwt)
        assertEquals("HR Screening", res.title)
    }

    @Test
    fun `submitInterviewScorecard delegates to interviewService with actor employee id`() {
        val req = ScorecardSubmitRequestDto(overallRecommendation = ScorecardRecommendation.HIRE)
        val expected = InterviewScorecardItemDto(
            id = UUID.randomUUID(),
            interviewId = interviewId,
            interviewerEmployeeId = employeeId,
            interviewerName = "Kasun Mendis",
            overallRecommendation = ScorecardRecommendation.HIRE,
            technicalSkillRating = BigDecimal("4.5"),
            communicationRating = BigDecimal("4.5"),
            problemSolvingRating = BigDecimal("4.5"),
            culturalFitRating = BigDecimal("4.5"),
            strengths = "Good",
            weaknesses = null,
            summaryNotes = "Recommend",
            submittedAt = Instant.now(),
        )
        every { interviewService.submitScorecard(interviewId, req, employeeId, tenantId) } returns expected

        val res = controller.submitInterviewScorecard(interviewId, req, mockJwt)
        assertEquals(ScorecardRecommendation.HIRE, res.overallRecommendation)
    }

    @Test
    fun `getApplicationOffer and createApplicationOffer delegate to applicationPipelineService`() {
        val expectedOffer = OfferDetailDto(
            id = UUID.randomUUID(),
            applicationId = applicationId,
            offerNumber = "OFF-001",
            baseSalary = BigDecimal("400000"),
            variableBonus = null,
            currency = "LKR",
            startDate = LocalDate.now().plusMonths(1),
            expiryDate = LocalDate.now().plusWeeks(2),
            offerLetterUrl = null,
            status = OfferStatus.EXTENDED,
            approvedBy = null,
            approvedAt = null,
            decisionNotes = null,
            createdAt = Instant.now(),
        )

        every { applicationPipelineService.getApplicationOffer(applicationId, tenantId) } returns expectedOffer
        val getRes = controller.getApplicationOffer(applicationId, mockJwt)
        assertEquals("OFF-001", getRes.offerNumber)

        val createReq = OfferCreateRequestDto(
            baseSalary = BigDecimal("400000"),
            currency = "LKR",
            startDate = LocalDate.now().plusMonths(1),
            expiryDate = LocalDate.now().plusWeeks(2),
        )
        every { applicationPipelineService.createApplicationOffer(applicationId, createReq, tenantId) } returns expectedOffer
        val postRes = controller.createApplicationOffer(applicationId, createReq, mockJwt)
        assertEquals("OFF-001", postRes.offerNumber)
    }
}
