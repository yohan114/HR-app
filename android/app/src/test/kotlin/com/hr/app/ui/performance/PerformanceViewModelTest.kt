package com.hr.app.ui.performance

import com.hr.app.data.performance.PerformanceRepository
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class PerformanceViewModelTest {

    private val repository = mockk<PerformanceRepository>(relaxed = true)

    private val goalsFlow = MutableStateFlow<List<GoalItem>>(emptyList())
    private val myAppraisalsFlow = MutableStateFlow<List<AppraisalSummaryItem>>(emptyList())
    private val teamAppraisalsFlow = MutableStateFlow<List<AppraisalSummaryItem>>(emptyList())
    private val selectedAppraisalFlow = MutableStateFlow<AppraisalDetailResponse?>(null)
    private val feedbackFlow = MutableStateFlow<List<ContinuousFeedbackItem>>(emptyList())
    private val competenciesFlow = MutableStateFlow<CompetencyFrameworkResponse?>(null)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PerformanceViewModel

    private val sampleGoal = GoalItem(
        id = UUID.randomUUID(),
        employeeId = UUID.randomUUID(),
        employeeName = "Kasun Mendis",
        cycleId = UUID.randomUUID(),
        cycleName = "FY2026 Annual Cycle",
        title = "Deliver Mobile Feature Parity",
        category = GoalItem.Category.INDIVIDUAL,
        weight = BigDecimal("40.0"),
        targetValue = BigDecimal("100.0"),
        currentValue = BigDecimal("75.0"),
        unit = "%",
        startDate = LocalDate.of(2026, 1, 1),
        dueDate = LocalDate.of(2026, 12, 31),
        status = GoalItem.Status.IN_PROGRESS,
        progressPercentage = BigDecimal("75.0"),
        checkIns = emptyList(),
    )

    private val sampleAppraisalSummary = AppraisalSummaryItem(
        id = UUID.randomUUID(),
        cycleId = UUID.randomUUID(),
        cycleName = "FY2026 Annual Cycle",
        employeeId = UUID.randomUUID(),
        employeeName = "Kasun Mendis",
        managerId = UUID.randomUUID(),
        managerName = "Nimal Perera",
        status = AppraisalSummaryItem.Status.SELF_REVIEW_PENDING,
        selfReviewDeadline = LocalDate.of(2026, 11, 30),
        managerReviewDeadline = LocalDate.of(2026, 12, 15),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { repository.goals } returns goalsFlow
        every { repository.myAppraisals } returns myAppraisalsFlow
        every { repository.teamAppraisals } returns teamAppraisalsFlow
        every { repository.selectedAppraisal } returns selectedAppraisalFlow
        every { repository.feedback } returns feedbackFlow
        every { repository.competencies } returns competenciesFlow

        viewModel = PerformanceViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads all performance data and updates state`() = runTest {
        advanceUntilIdle()

        coVerify {
            repository.refreshGoals()
            repository.refreshMyAppraisals()
            repository.refreshTeamAppraisals()
            repository.refreshFeedback()
            repository.refreshCompetencies()
        }
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `selectTab switches active tab and clears alerts`() {
        viewModel.selectTab(PerformanceTab.FEEDBACK)
        assertEquals(PerformanceTab.FEEDBACK, viewModel.state.value.currentTab)
        assertNull(viewModel.state.value.errorMessage)
        assertNull(viewModel.state.value.successMessage)
    }

    @Test
    fun `filterByCategory updates selectedCategoryFilter`() {
        viewModel.filterByCategory(GoalItem.Category.DEPARTMENTAL)
        assertEquals(GoalItem.Category.DEPARTMENTAL, viewModel.state.value.selectedCategoryFilter)

        viewModel.filterByCategory(null)
        assertNull(viewModel.state.value.selectedCategoryFilter)
    }

    @Test
    fun `openCheckInDialog and submitCheckIn records progress`() = runTest {
        coEvery { repository.recordCheckIn(sampleGoal.id, any()) } returns Result.success(
            sampleGoal.copy(currentValue = BigDecimal("90.0"))
        )

        viewModel.openCheckInDialog(sampleGoal)
        assertTrue(viewModel.state.value.isCheckInDialogOpen)
        assertEquals(sampleGoal, viewModel.state.value.selectedGoalForCheckIn)

        viewModel.updateCheckInNewValue("90.0")
        viewModel.updateCheckInNote("Reached 90% completion")
        viewModel.submitCheckIn()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isCheckInDialogOpen)
        assertEquals("Check-in recorded! Goal progress updated.", viewModel.state.value.successMessage)
    }

    @Test
    fun `submitCreateGoal validates empty title`() {
        viewModel.openCreateGoalDialog()
        viewModel.updateNewGoalTitle("   ")
        viewModel.submitCreateGoal()

        assertEquals("Goal title cannot be blank.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `submitCreateGoal successfully invokes repository`() = runTest {
        coEvery { repository.createGoal(any()) } returns Result.success(sampleGoal)

        viewModel.openCreateGoalDialog()
        viewModel.updateNewGoalTitle("Reduce bundle size")
        viewModel.updateNewGoalTarget("50.0")
        viewModel.updateNewGoalUnit("MB")
        viewModel.updateNewGoalWeight("25.0")
        viewModel.submitCreateGoal()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isCreateGoalDialogOpen)
        assertEquals("New goal created successfully.", viewModel.state.value.successMessage)
    }

    @Test
    fun `openAppraisalDetail loads detail and sets selected appraisal`() = runTest {
        val detail = AppraisalDetailResponse(
            appraisal = sampleAppraisalSummary,
            goals = emptyList(),
            competencies = emptyList(),
        )
        coEvery { repository.loadAppraisalDetail(sampleAppraisalSummary.id) } returns Result.success(detail)

        viewModel.openAppraisalDetail(sampleAppraisalSummary)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isDetailDialogOpen)
        assertEquals(detail, viewModel.state.value.selectedAppraisalDetail)
    }

    @Test
    fun `submitSelfReview invokes repository and updates state`() = runTest {
        val detail = AppraisalDetailResponse(
            appraisal = sampleAppraisalSummary,
            goals = emptyList(),
            competencies = emptyList(),
        )
        coEvery { repository.loadAppraisalDetail(sampleAppraisalSummary.id) } returns Result.success(detail)
        viewModel.openAppraisalDetail(sampleAppraisalSummary)
        advanceUntilIdle()

        val updatedDetail = detail.copy(
            appraisal = sampleAppraisalSummary.copy(status = AppraisalSummaryItem.Status.SELF_REVIEW_SUBMITTED)
        )
        coEvery { repository.submitSelfReview(sampleAppraisalSummary.id, any()) } returns Result.success(updatedDetail)

        viewModel.openSelfReviewDialog()
        viewModel.updateSelfOverallComments("Exceeded all delivery milestones.")
        viewModel.submitSelfReview()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSelfReviewDialogOpen)
        assertEquals("Self-review submitted successfully!", viewModel.state.value.successMessage)
    }

    @Test
    fun `submitManagerReview invokes repository and updates state`() = runTest {
        val detail = AppraisalDetailResponse(
            appraisal = sampleAppraisalSummary,
            goals = emptyList(),
            competencies = emptyList(),
        )
        coEvery { repository.loadAppraisalDetail(sampleAppraisalSummary.id) } returns Result.success(detail)
        viewModel.openAppraisalDetail(sampleAppraisalSummary)
        advanceUntilIdle()

        val updatedDetail = detail.copy(
            appraisal = sampleAppraisalSummary.copy(
                status = AppraisalSummaryItem.Status.MANAGER_REVIEW_SUBMITTED,
                finalScore = BigDecimal("4.8"),
            )
        )
        coEvery { repository.submitManagerReview(sampleAppraisalSummary.id, any()) } returns Result.success(updatedDetail)

        viewModel.openManagerReviewDialog()
        viewModel.updateManagerOverallComments("Exceptional ownership and architecture.")
        viewModel.updateManagerCalibrationNotes("Promote to Principal Lead.")
        viewModel.submitManagerReview()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isManagerReviewDialogOpen)
        assertEquals("Manager evaluation and calibration submitted!", viewModel.state.value.successMessage)
    }

    @Test
    fun `acknowledgeAppraisal invokes repository and updates state`() = runTest {
        val detail = AppraisalDetailResponse(
            appraisal = sampleAppraisalSummary,
            goals = emptyList(),
            competencies = emptyList(),
        )
        coEvery { repository.loadAppraisalDetail(sampleAppraisalSummary.id) } returns Result.success(detail)
        viewModel.openAppraisalDetail(sampleAppraisalSummary)
        advanceUntilIdle()

        val acknowledged = detail.copy(
            appraisal = sampleAppraisalSummary.copy(
                status = AppraisalSummaryItem.Status.ACKNOWLEDGED,
                employeeAcknowledgedAt = OffsetDateTime.now(),
            )
        )
        coEvery { repository.acknowledgeAppraisal(sampleAppraisalSummary.id) } returns Result.success(acknowledged)

        viewModel.acknowledgeAppraisal()
        advanceUntilIdle()

        assertEquals("Appraisal outcome signed and acknowledged.", viewModel.state.value.successMessage)
    }

    @Test
    fun `submitFeedback validates empty fields and sends feedback`() = runTest {
        viewModel.openSendFeedbackDialog()
        viewModel.updateFeedbackTitle("")
        viewModel.submitFeedback()
        assertEquals("Title and feedback content are required.", viewModel.state.value.errorMessage)

        val feedbackItem = ContinuousFeedbackItem(
            id = UUID.randomUUID(),
            senderEmployeeId = UUID.randomUUID(),
            senderEmployeeName = "Kasun Mendis",
            recipientEmployeeId = UUID.randomUUID(),
            recipientEmployeeName = "Nimal Perera",
            feedbackType = ContinuousFeedbackItem.FeedbackType.PRAISE,
            title = "Kudos on sprint",
            content = "Great support on the production deployment",
            isPrivate = false,
            sharedWithManager = true,
            createdAt = OffsetDateTime.now(),
        )
        coEvery { repository.sendFeedback(any()) } returns Result.success(feedbackItem)

        viewModel.updateFeedbackTitle("Kudos on sprint")
        viewModel.updateFeedbackContent("Great support on the production deployment")
        viewModel.submitFeedback()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSendFeedbackDialogOpen)
        assertEquals("Feedback sent successfully!", viewModel.state.value.successMessage)
    }
}
