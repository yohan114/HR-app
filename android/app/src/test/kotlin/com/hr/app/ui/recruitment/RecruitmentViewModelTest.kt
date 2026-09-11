package com.hr.app.ui.recruitment

import com.hr.app.data.recruitment.RecruitmentRepository
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RecruitmentViewModelTest {

    private val repository = mockk<RecruitmentRepository>()
    private val testDispatcher = StandardTestDispatcher()

    private val vacanciesFlow = MutableStateFlow<List<VacancyItem>>(emptyList())
    private val candidatesFlow = MutableStateFlow<List<CandidateItem>>(emptyList())
    private val applicationsFlow = MutableStateFlow<List<ApplicationItem>>(emptyList())
    private val interviewsFlow = MutableStateFlow<List<InterviewItem>>(emptyList())
    private val offerFlow = MutableStateFlow<OfferDetail?>(null)

    private val vacancyId = UUID.randomUUID()
    private val applicationId = UUID.randomUUID()
    private val interviewId = UUID.randomUUID()

    private val sampleVacancy = VacancyItem(
        id = vacancyId,
        jobCode = "VAC-2026-001",
        title = "Senior Backend Engineer",
        location = "Colombo",
        employmentType = "FULL_TIME",
        experienceLevel = "SENIOR_LEVEL",
        currency = "LKR",
        description = "Kotlin Dev",
        openPositions = 1,
        filledPositions = 0,
        status = VacancyItem.Status.OPEN,
        createdAt = OffsetDateTime.now(),
    )

    private val sampleApplication = ApplicationItem(
        id = applicationId,
        applicationNumber = "APP-2026-001",
        vacancyId = vacancyId,
        vacancyTitle = "Senior Backend Engineer",
        candidateId = UUID.randomUUID(),
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
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.vacancies } returns vacanciesFlow
        coEvery { repository.candidates } returns candidatesFlow
        coEvery { repository.applications } returns applicationsFlow
        coEvery { repository.interviews } returns interviewsFlow
        coEvery { repository.selectedOffer } returns offerFlow

        coEvery { repository.refreshVacancies(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.refreshCandidates(any()) } returns Result.success(emptyList())
        coEvery { repository.refreshApplications(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { repository.refreshInterviews(any(), any(), any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectTab updates selectedTab in state`() = runTest {
        val viewModel = RecruitmentViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RecruitmentTab.VACANCIES, viewModel.state.value.selectedTab)
        viewModel.selectTab(RecruitmentTab.PIPELINE)
        assertEquals(RecruitmentTab.PIPELINE, viewModel.state.value.selectedTab)
    }

    @Test
    fun `setSearchQuery and setStageFilter update filters in state`() = runTest {
        val viewModel = RecruitmentViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSearchQuery("Kotlin")
        assertEquals("Kotlin", viewModel.state.value.searchQuery)

        viewModel.setStageFilter(ApplicationItem.Stage.INTERVIEW_ROUND_1)
        assertEquals(ApplicationItem.Stage.INTERVIEW_ROUND_1, viewModel.state.value.selectedStageFilter)
    }

    @Test
    fun `selectVacancy and selectApplication update selected items`() = runTest {
        val viewModel = RecruitmentViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectVacancy(sampleVacancy)
        assertEquals(sampleVacancy, viewModel.state.value.selectedVacancy)

        coEvery { repository.getApplicationOffer(applicationId) } returns Result.success(
            OfferDetail(
                id = UUID.randomUUID(),
                applicationId = applicationId,
                offerNumber = "OFF-001",
                baseSalary = BigDecimal("400000"),
                currency = "LKR",
                startDate = LocalDate.now(),
                expiryDate = LocalDate.now().plusWeeks(2),
                status = OfferDetail.Status.EXTENDED,
                createdAt = OffsetDateTime.now(),
            )
        )

        viewModel.selectApplication(sampleApplication)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(sampleApplication, viewModel.state.value.selectedApplication)
        coVerify(exactly = 1) { repository.getApplicationOffer(applicationId) }
    }

    @Test
    fun `createVacancy calls repository and updates success message`() = runTest {
        val viewModel = RecruitmentViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { repository.createVacancy(any()) } returns Result.success(sampleVacancy)

        viewModel.createVacancy(
            jobCode = "VAC-2026-001",
            title = "Senior Backend Engineer",
            description = "Kotlin Dev",
            minSalary = BigDecimal("350000"),
            maxSalary = BigDecimal("480000"),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.createVacancy(any()) }
        assertNotNull(viewModel.state.value.successMessage)
        assertFalse(viewModel.state.value.showCreateVacancyDialog)
    }

    @Test
    fun `updateApplicationStage calls repository`() = runTest {
        val viewModel = RecruitmentViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = sampleApplication.copy(stage = ApplicationItem.Stage.TECHNICAL_ASSESSMENT)
        coEvery { repository.updateApplicationStage(applicationId, any()) } returns Result.success(updated)

        viewModel.updateApplicationStage(applicationId, ApplicationStageUpdateRequest.Stage.TECHNICAL_ASSESSMENT)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateApplicationStage(applicationId, any()) }
        assertEquals(updated, viewModel.state.value.selectedApplication)
    }

    @Test
    fun `submitScorecard calls repository and closes dialog`() = runTest {
        val viewModel = RecruitmentViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val scorecard = InterviewScorecardItem(
            id = UUID.randomUUID(),
            interviewId = interviewId,
            interviewerEmployeeId = UUID.randomUUID(),
            interviewerName = "Kasun Mendis",
            overallRecommendation = InterviewScorecardItem.OverallRecommendation.STRONG_HIRE,
            submittedAt = OffsetDateTime.now(),
        )
        coEvery { repository.submitScorecard(interviewId, any()) } returns Result.success(scorecard)

        viewModel.submitScorecard(
            interviewId = interviewId,
            rec = ScorecardSubmitRequest.OverallRecommendation.STRONG_HIRE,
            tech = BigDecimal("4.8"),
            comm = BigDecimal("4.5"),
            problem = BigDecimal("4.9"),
            fit = BigDecimal("4.7"),
            strengths = "Good",
            weaknesses = null,
            notes = "Recommend",
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.submitScorecard(interviewId, any()) }
        assertNotNull(viewModel.state.value.successMessage)
        assertFalse(viewModel.state.value.showScorecardDialog)
    }
}
