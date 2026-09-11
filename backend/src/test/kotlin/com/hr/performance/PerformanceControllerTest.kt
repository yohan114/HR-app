package com.hr.performance

import com.hr.performance.internal.*
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
import java.util.UUID

@DisplayName("Performance Controller Unit Tests")
class PerformanceControllerTest {

    private val goalService = mockk<GoalService>()
    private val appraisalService = mockk<AppraisalService>()
    private val continuousFeedbackService = mockk<ContinuousFeedbackService>()

    private lateinit var controller: PerformanceController

    private val employeeId = UUID.randomUUID()
    private val managerId = UUID.randomUUID()
    private val appraisalId = UUID.randomUUID()
    private val goalId = UUID.randomUUID()

    private val mockJwt = mockk<Jwt> {
        every { getClaimAsString("employee_id") } returns employeeId.toString()
    }

    @BeforeEach
    fun setUp() {
        controller = PerformanceController(
            goalService = goalService,
            appraisalService = appraisalService,
            continuousFeedbackService = continuousFeedbackService,
        )
    }

    @Test
    fun `getGoals delegates to goalService`() {
        val expected = GoalListResponseDto(goals = emptyList(), totalCount = 0, completedCount = 0)
        every { goalService.getGoals(employeeId = any(), cycleId = any(), status = any(), tenantId = any()) } returns expected

        val response = controller.getGoals(employeeId = null, cycleId = null, status = null, jwt = mockJwt)

        assertEquals(0, response.totalCount)
        verify(exactly = 1) { goalService.getGoals(employeeId = employeeId, cycleId = null, status = null, tenantId = any()) }
    }

    @Test
    fun `createGoal delegates to goalService`() {
        val request = GoalCreateRequestDto(
            employeeId = employeeId,
            cycleId = UUID.randomUUID(),
            title = "Test Goal",
            category = GoalCategory.INDIVIDUAL,
            weight = BigDecimal("100.00"),
            targetValue = BigDecimal("100.00"),
            unit = "%",
            startDate = LocalDate.now(),
            dueDate = LocalDate.now().plusMonths(3),
        )
        val expected = GoalItemDto(
            id = goalId,
            employeeId = employeeId,
            employeeName = "Kasun",
            cycleId = request.cycleId,
            cycleName = "Cycle",
            parentGoalId = null,
            title = request.title,
            description = null,
            category = request.category,
            weight = request.weight,
            targetValue = request.targetValue,
            currentValue = BigDecimal.ZERO,
            unit = request.unit,
            startDate = request.startDate,
            dueDate = request.dueDate,
            status = GoalStatus.NOT_STARTED,
            progressPercentage = BigDecimal.ZERO,
            checkIns = emptyList(),
        )
        every { goalService.createGoal(request, any()) } returns expected

        val result = controller.createGoal(request)

        assertEquals("Test Goal", result.title)
        verify(exactly = 1) { goalService.createGoal(request, any()) }
    }

    @Test
    fun `recordGoalCheckIn delegates to goalService with caller ID`() {
        val request = GoalCheckInRequestDto(newValue = BigDecimal("75.00"), note = "Good progress")
        val expected = GoalItemDto(
            id = goalId,
            employeeId = employeeId,
            employeeName = "Kasun",
            cycleId = UUID.randomUUID(),
            cycleName = "Cycle",
            parentGoalId = null,
            title = "Goal",
            description = null,
            category = GoalCategory.INDIVIDUAL,
            weight = BigDecimal("100.00"),
            targetValue = BigDecimal("100.00"),
            currentValue = BigDecimal("75.00"),
            unit = "%",
            startDate = LocalDate.now(),
            dueDate = LocalDate.now().plusMonths(3),
            status = GoalStatus.ON_TRACK,
            progressPercentage = BigDecimal("75.00"),
            checkIns = emptyList(),
        )
        every { goalService.recordGoalCheckIn(goalId, request, employeeId, any()) } returns expected

        val result = controller.recordGoalCheckIn(goalId, request, mockJwt)

        assertEquals(BigDecimal("75.00"), result.currentValue)
        verify(exactly = 1) { goalService.recordGoalCheckIn(goalId, request, employeeId, any()) }
    }

    @Test
    fun `getCompetencies delegates to appraisalService`() {
        val expected = CompetencyFrameworkResponseDto(groups = emptyList(), competencies = emptyList())
        every { appraisalService.getCompetencies(any()) } returns expected

        val result = controller.getCompetencies()

        assertNotNull(result)
        verify(exactly = 1) { appraisalService.getCompetencies(any()) }
    }

    @Test
    fun `getMyAppraisals delegates to appraisalService`() {
        val expected = AppraisalListResponseDto(appraisals = emptyList())
        every { appraisalService.getMyAppraisals(employeeId, null, any()) } returns expected

        val result = controller.getMyAppraisals(null, mockJwt)

        assertNotNull(result)
        verify(exactly = 1) { appraisalService.getMyAppraisals(employeeId, null, any()) }
    }

    @Test
    fun `getTeamAppraisals delegates to appraisalService`() {
        val expected = AppraisalListResponseDto(appraisals = emptyList())
        every { appraisalService.getTeamAppraisals(employeeId, null, any()) } returns expected

        val result = controller.getTeamAppraisals(null, mockJwt)

        assertNotNull(result)
        verify(exactly = 1) { appraisalService.getTeamAppraisals(employeeId, null, any()) }
    }

    @Test
    fun `submitSelfReview delegates to appraisalService`() {
        val request = AppraisalSelfReviewRequestDto(
            overallComments = "Comments",
            goalRatings = emptyList(),
            competencyRatings = emptyList(),
        )
        val expected = AppraisalDetailResponseDto(
            appraisal = AppraisalSummaryItemDto(
                id = appraisalId,
                cycleId = UUID.randomUUID(),
                cycleName = "Cycle",
                employeeId = employeeId,
                employeeName = "Kasun",
                managerId = managerId,
                managerName = "Nimal",
                status = AppraisalStatus.SELF_REVIEW_SUBMITTED,
                finalScore = null,
                finalRating = null,
                selfReviewDeadline = LocalDate.now(),
                managerReviewDeadline = LocalDate.now(),
                employeeAcknowledgedAt = null,
            ),
            goals = emptyList(),
            competencies = emptyList(),
            selfOverallComments = "Comments",
            managerOverallComments = null,
            calibrationNotes = null,
            mraSummary = null,
        )
        every { appraisalService.submitSelfReview(appraisalId, request, employeeId, any()) } returns expected

        val result = controller.submitSelfReview(appraisalId, request, mockJwt)

        assertEquals(AppraisalStatus.SELF_REVIEW_SUBMITTED, result.appraisal.status)
        verify(exactly = 1) { appraisalService.submitSelfReview(appraisalId, request, employeeId, any()) }
    }

    @Test
    fun `submitManagerReview delegates to appraisalService`() {
        val request = AppraisalManagerReviewRequestDto(
            overallComments = "Well done",
            goalRatings = emptyList(),
            competencyRatings = emptyList(),
        )
        val expected = AppraisalDetailResponseDto(
            appraisal = AppraisalSummaryItemDto(
                id = appraisalId,
                cycleId = UUID.randomUUID(),
                cycleName = "Cycle",
                employeeId = employeeId,
                employeeName = "Kasun",
                managerId = employeeId,
                managerName = "Nimal",
                status = AppraisalStatus.MANAGER_REVIEW_SUBMITTED,
                finalScore = BigDecimal("92.00"),
                finalRating = "Outstanding",
                selfReviewDeadline = LocalDate.now(),
                managerReviewDeadline = LocalDate.now(),
                employeeAcknowledgedAt = null,
            ),
            goals = emptyList(),
            competencies = emptyList(),
            selfOverallComments = null,
            managerOverallComments = "Well done",
            calibrationNotes = null,
            mraSummary = null,
        )
        every { appraisalService.submitManagerReview(appraisalId, request, employeeId, any()) } returns expected

        val result = controller.submitManagerReview(appraisalId, request, mockJwt)

        assertEquals(AppraisalStatus.MANAGER_REVIEW_SUBMITTED, result.appraisal.status)
        verify(exactly = 1) { appraisalService.submitManagerReview(appraisalId, request, employeeId, any()) }
    }

    @Test
    fun `acknowledgeAppraisal delegates to appraisalService`() {
        val expected = AppraisalDetailResponseDto(
            appraisal = AppraisalSummaryItemDto(
                id = appraisalId,
                cycleId = UUID.randomUUID(),
                cycleName = "Cycle",
                employeeId = employeeId,
                employeeName = "Kasun",
                managerId = managerId,
                managerName = "Nimal",
                status = AppraisalStatus.ACKNOWLEDGED,
                finalScore = BigDecimal("90.00"),
                finalRating = "Outstanding",
                selfReviewDeadline = LocalDate.now(),
                managerReviewDeadline = LocalDate.now(),
                employeeAcknowledgedAt = Instant.now(),
            ),
            goals = emptyList(),
            competencies = emptyList(),
            selfOverallComments = null,
            managerOverallComments = null,
            calibrationNotes = null,
            mraSummary = null,
        )
        every { appraisalService.acknowledgeAppraisal(appraisalId, employeeId, any()) } returns expected

        val result = controller.acknowledgeAppraisal(appraisalId, mockJwt)

        assertEquals(AppraisalStatus.ACKNOWLEDGED, result.appraisal.status)
        verify(exactly = 1) { appraisalService.acknowledgeAppraisal(appraisalId, employeeId, any()) }
    }

    @Test
    fun `sendContinuousFeedback delegates to continuousFeedbackService`() {
        val recipientId = UUID.randomUUID()
        val request = SendFeedbackRequestDto(
            recipientEmployeeId = recipientId,
            feedbackType = FeedbackType.PRAISE,
            title = "Great teamwork",
            content = "Helped resolve production bug",
        )
        val expected = ContinuousFeedbackItemDto(
            id = UUID.randomUUID(),
            senderEmployeeId = employeeId,
            senderEmployeeName = "Kasun",
            recipientEmployeeId = recipientId,
            recipientEmployeeName = "Colleague",
            feedbackType = FeedbackType.PRAISE,
            title = request.title,
            content = request.content,
            isPrivate = false,
            sharedWithManager = true,
            createdAt = Instant.now(),
        )
        every { continuousFeedbackService.sendFeedback(employeeId, request, any()) } returns expected

        val result = controller.sendContinuousFeedback(request, mockJwt)

        assertEquals("Great teamwork", result.title)
        verify(exactly = 1) { continuousFeedbackService.sendFeedback(employeeId, request, any()) }
    }
}
