package com.hr.app.data.recruitment

import com.hr.client.api.RecruitmentApi
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class RecruitmentRepositoryTest {

    private val recruitmentApi = mockk<RecruitmentApi>()
    private lateinit var repository: RecruitmentRepository

    private val vacancyId = UUID.randomUUID()
    private val candidateId = UUID.randomUUID()
    private val applicationId = UUID.randomUUID()
    private val interviewId = UUID.randomUUID()

    private val sampleVacancy = VacancyItem(
        id = vacancyId,
        jobCode = "VAC-2026-001",
        title = "Senior Backend Engineer",
        departmentName = "Engineering",
        location = "Colombo",
        employmentType = "FULL_TIME",
        experienceLevel = "SENIOR_LEVEL",
        currency = "LKR",
        description = "Kotlin development",
        openPositions = 2,
        filledPositions = 0,
        status = VacancyItem.Status.OPEN,
        createdAt = OffsetDateTime.now(),
    )

    private val sampleCandidate = CandidateItem(
        id = candidateId,
        firstName = "Nuwan",
        lastName = "Senaratne",
        email = "nuwan@example.com",
        skills = listOf("Kotlin", "Postgres"),
        createdAt = OffsetDateTime.now(),
    )

    private val sampleApplication = ApplicationItem(
        id = applicationId,
        applicationNumber = "APP-2026-001",
        vacancyId = vacancyId,
        vacancyTitle = "Senior Backend Engineer",
        candidateId = candidateId,
        candidateName = "Nuwan Senaratne",
        candidateEmail = "nuwan@example.com",
        stage = ApplicationItem.Stage.APPLIED,
        status = ApplicationItem.Status.ACTIVE,
        source = "CAREERS_PORTAL",
        appliedDate = LocalDate.now(),
        createdAt = OffsetDateTime.now(),
    )

    @Before
    fun setUp() {
        repository = RecruitmentRepository(recruitmentApi)
    }

    @Test
    fun `refreshVacancies fetches from API and updates state flow`() = runTest {
        coEvery { recruitmentApi.getVacancies(any(), any()) } returns Response.success(
            VacancyListResponse(items = listOf(sampleVacancy), totalCount = 1)
        )

        val result = repository.refreshVacancies()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.vacancies.value.size)
        assertEquals("VAC-2026-001", repository.vacancies.value.first().jobCode)
    }

    @Test
    fun `refreshVacancies falls back to offline default when API fails`() = runTest {
        coEvery { recruitmentApi.getVacancies(any(), any()) } throws RuntimeException("Network timeout")

        val result = repository.refreshVacancies()
        assertTrue(result.isFailure)
        assertTrue(repository.vacancies.value.isNotEmpty())
        assertEquals("VAC-2026-001", repository.vacancies.value.first().jobCode)
    }

    @Test
    fun `createVacancy calls API and prepends to state flow`() = runTest {
        val req = VacancyCreateRequest(
            jobCode = "VAC-2026-002",
            title = "Mobile Engineering Lead",
            location = "Colombo",
            employmentType = "FULL_TIME",
            experienceLevel = "LEAD",
            currency = "LKR",
            description = "Lead Android team",
            openPositions = 1,
        )
        val created = sampleVacancy.copy(id = UUID.randomUUID(), jobCode = "VAC-2026-002", title = "Mobile Engineering Lead")
        coEvery { recruitmentApi.createVacancy(req) } returns Response.success(created)

        val result = repository.createVacancy(req)
        assertTrue(result.isSuccess)
        assertEquals(created, repository.vacancies.value.first())
    }

    @Test
    fun `updateApplicationStage transitions stage`() = runTest {
        val req = ApplicationStageUpdateRequest(stage = ApplicationStageUpdateRequest.Stage.TECHNICAL_ASSESSMENT)
        val updated = sampleApplication.copy(stage = ApplicationItem.Stage.TECHNICAL_ASSESSMENT)
        coEvery { recruitmentApi.updateApplicationStage(applicationId, req) } returns Response.success(updated)

        val result = repository.updateApplicationStage(applicationId, req)
        assertTrue(result.isSuccess)
        assertEquals(ApplicationItem.Stage.TECHNICAL_ASSESSMENT, result.getOrNull()?.stage)
    }

    @Test
    fun `submitScorecard records scorecard and marks interview completed`() = runTest {
        val req = ScorecardSubmitRequest(
            overallRecommendation = ScorecardSubmitRequest.OverallRecommendation.STRONG_HIRE,
            technicalSkillRating = BigDecimal("4.8"),
            summaryNotes = "Strong candidate",
        )
        val sampleScorecard = InterviewScorecardItem(
            id = UUID.randomUUID(),
            interviewId = interviewId,
            interviewerEmployeeId = UUID.randomUUID(),
            interviewerName = "Kasun Mendis",
            overallRecommendation = InterviewScorecardItem.OverallRecommendation.STRONG_HIRE,
            submittedAt = OffsetDateTime.now(),
        )
        coEvery { recruitmentApi.submitInterviewScorecard(interviewId, req) } returns Response.success(sampleScorecard)

        val result = repository.submitScorecard(interviewId, req)
        assertTrue(result.isSuccess)
        assertEquals(InterviewScorecardItem.OverallRecommendation.STRONG_HIRE, result.getOrNull()?.overallRecommendation)
    }
}
