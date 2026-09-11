package com.hr.app.data.performance

import com.hr.client.api.PerformanceApi
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class PerformanceRepositoryTest {

    private val performanceApi = mockk<PerformanceApi>()
    private lateinit var repository: PerformanceRepository

    private val cycleId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()
    private val goalId = UUID.randomUUID()
    private val appraisalId = UUID.randomUUID()

    private val sampleGoal = GoalItem(
        id = goalId,
        employeeId = employeeId,
        employeeName = "Kasun Mendis",
        cycleId = cycleId,
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
        id = appraisalId,
        cycleId = cycleId,
        cycleName = "FY2026 Annual Cycle",
        employeeId = employeeId,
        employeeName = "Kasun Mendis",
        managerId = UUID.randomUUID(),
        managerName = "Nimal Perera",
        status = AppraisalSummaryItem.Status.SELF_REVIEW_PENDING,
        selfReviewDeadline = LocalDate.of(2026, 11, 30),
        managerReviewDeadline = LocalDate.of(2026, 12, 15),
    )

    @Before
    fun setUp() {
        repository = PerformanceRepository(performanceApi)
    }

    @Test
    fun `refreshGoals fetches goals from API on network success`() = runTest {
        coEvery { performanceApi.getGoals(any(), any(), any()) } returns Response.success(
            GoalListResponse(goals = listOf(sampleGoal), totalCount = 1, completedCount = 0)
        )

        val result = repository.refreshGoals()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.goals.value.size)
        assertEquals("Deliver Mobile Feature Parity", repository.goals.value.first().title)
    }

    @Test
    fun `refreshGoals falls back to offline fixtures on network failure`() = runTest {
        coEvery { performanceApi.getGoals(any(), any(), any()) } throws RuntimeException("Network unreachable")

        val result = repository.refreshGoals()
        assertTrue(result.isFailure)
        assertTrue(repository.goals.value.isNotEmpty())
        assertEquals("Kasun Mendis", repository.goals.value.first().employeeName)
    }

    @Test
    fun `recordCheckIn updates current progress percentage and appends check-in history`() = runTest {
        val updatedGoal = sampleGoal.copy(
            currentValue = BigDecimal("85.0"),
            progressPercentage = BigDecimal("85.0"),
        )
        coEvery { performanceApi.recordGoalCheckIn(eq(goalId), any()) } returns Response.success(updatedGoal)

        // Seed initial
        coEvery { performanceApi.getGoals(any(), any(), any()) } returns Response.success(
            GoalListResponse(goals = listOf(sampleGoal), totalCount = 1, completedCount = 0)
        )
        repository.refreshGoals()

        val request = GoalCheckInRequest(newValue = BigDecimal("85.0"), note = "Milestone achieved")
        val result = repository.recordCheckIn(goalId, request)

        assertTrue(result.isSuccess)
        assertEquals(BigDecimal("85.0"), repository.goals.value.find { it.id == goalId }?.currentValue)
    }

    @Test
    fun `submitSelfReview transitions appraisal status to SELF_REVIEW_SUBMITTED`() = runTest {
        val detail = AppraisalDetailResponse(
            appraisal = sampleAppraisalSummary.copy(status = AppraisalSummaryItem.Status.SELF_REVIEW_SUBMITTED),
            goals = emptyList(),
            competencies = emptyList(),
            selfOverallComments = "Achieved outstanding results",
        )
        coEvery { performanceApi.submitSelfReview(eq(appraisalId), any()) } returns Response.success(detail)

        val request = AppraisalSelfReviewRequest(
            goalRatings = emptyList(),
            competencyRatings = emptyList(),
            overallComments = "Achieved outstanding results",
        )

        val result = repository.submitSelfReview(appraisalId, request)
        assertTrue(result.isSuccess)
        assertEquals(AppraisalSummaryItem.Status.SELF_REVIEW_SUBMITTED, repository.selectedAppraisal.value?.appraisal?.status)
        assertEquals("Achieved outstanding results", repository.selectedAppraisal.value?.selfOverallComments)
    }

    @Test
    fun `submitManagerReview records score and advances status to MANAGER_REVIEW_SUBMITTED`() = runTest {
        val detail = AppraisalDetailResponse(
            appraisal = sampleAppraisalSummary.copy(
                status = AppraisalSummaryItem.Status.MANAGER_REVIEW_SUBMITTED,
                finalScore = BigDecimal("4.50"),
                finalRating = "Exceeds Expectations",
            ),
            goals = emptyList(),
            competencies = emptyList(),
            managerOverallComments = "Excellent leadership",
        )
        coEvery { performanceApi.submitManagerReview(eq(appraisalId), any()) } returns Response.success(detail)

        val request = AppraisalManagerReviewRequest(
            goalRatings = emptyList(),
            competencyRatings = emptyList(),
            overallComments = "Excellent leadership",
        )

        val result = repository.submitManagerReview(appraisalId, request)
        assertTrue(result.isSuccess)
        assertEquals(AppraisalSummaryItem.Status.MANAGER_REVIEW_SUBMITTED, repository.selectedAppraisal.value?.appraisal?.status)
        assertEquals(BigDecimal("4.50"), repository.selectedAppraisal.value?.appraisal?.finalScore)
    }

    @Test
    fun `acknowledgeAppraisal updates status to ACKNOWLEDGED and timestamps`() = runTest {
        val detail = AppraisalDetailResponse(
            appraisal = sampleAppraisalSummary.copy(
                status = AppraisalSummaryItem.Status.ACKNOWLEDGED,
                employeeAcknowledgedAt = OffsetDateTime.now(),
            ),
            goals = emptyList(),
            competencies = emptyList(),
        )
        coEvery { performanceApi.acknowledgeAppraisal(eq(appraisalId)) } returns Response.success(detail)

        val result = repository.acknowledgeAppraisal(appraisalId)
        assertTrue(result.isSuccess)
        assertEquals(AppraisalSummaryItem.Status.ACKNOWLEDGED, repository.selectedAppraisal.value?.appraisal?.status)
        assertNotNull(repository.selectedAppraisal.value?.appraisal?.employeeAcknowledgedAt)
    }

    @Test
    fun `sendFeedback records praise and updates feedback StateFlow`() = runTest {
        val feedbackItem = ContinuousFeedbackItem(
            id = UUID.randomUUID(),
            senderEmployeeId = employeeId,
            senderEmployeeName = "Kasun Mendis",
            recipientEmployeeId = UUID.randomUUID(),
            recipientEmployeeName = "Nimal Perera",
            feedbackType = ContinuousFeedbackItem.FeedbackType.PRAISE,
            title = "Great Architecture",
            content = "Exceptional system design on new performance modules",
            isPrivate = false,
            sharedWithManager = true,
            createdAt = OffsetDateTime.now(),
        )
        coEvery { performanceApi.sendContinuousFeedback(any()) } returns Response.success(feedbackItem)

        val request = SendFeedbackRequest(
            recipientEmployeeId = feedbackItem.recipientEmployeeId,
            feedbackType = SendFeedbackRequest.FeedbackType.PRAISE,
            title = "Great Architecture",
            content = "Exceptional system design on new performance modules",
        )

        val result = repository.sendFeedback(request)
        assertTrue(result.isSuccess)
        assertEquals(1, repository.feedback.value.size)
        assertEquals("Great Architecture", repository.feedback.value.first().title)
    }
}
