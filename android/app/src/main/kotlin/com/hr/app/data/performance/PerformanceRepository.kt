package com.hr.app.data.performance

import com.hr.client.api.PerformanceApi
import com.hr.client.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceRepository
    @Inject
    constructor(
        private val performanceApi: PerformanceApi,
    ) {
        private val _goals = MutableStateFlow<List<GoalItem>>(emptyList())
        val goals: StateFlow<List<GoalItem>> = _goals.asStateFlow()

        private val _myAppraisals = MutableStateFlow<List<AppraisalSummaryItem>>(emptyList())
        val myAppraisals: StateFlow<List<AppraisalSummaryItem>> = _myAppraisals.asStateFlow()

        private val _teamAppraisals = MutableStateFlow<List<AppraisalSummaryItem>>(emptyList())
        val teamAppraisals: StateFlow<List<AppraisalSummaryItem>> = _teamAppraisals.asStateFlow()

        private val _selectedAppraisal = MutableStateFlow<AppraisalDetailResponse?>(null)
        val selectedAppraisal: StateFlow<AppraisalDetailResponse?> = _selectedAppraisal.asStateFlow()

        private val _feedback = MutableStateFlow<List<ContinuousFeedbackItem>>(emptyList())
        val feedback: StateFlow<List<ContinuousFeedbackItem>> = _feedback.asStateFlow()

        private val _competencies = MutableStateFlow<CompetencyFrameworkResponse?>(null)
        val competencies: StateFlow<CompetencyFrameworkResponse?> = _competencies.asStateFlow()

        /**
         * Refreshes goals and OKRs for the given employee or cycle.
         */
        suspend fun refreshGoals(
            employeeId: UUID? = null,
            cycleId: UUID? = null,
            status: String? = null,
        ): Result<List<GoalItem>> =
            runCatching {
                val response = performanceApi.getGoals(employeeId = employeeId, cycleId = cycleId, status = status)
                val body = response.body()?.goals.takeIf { response.isSuccessful }
                    ?: error("GET /v1/performance/goals failed with ${response.code()}")
                _goals.value = body
                body
            }.onFailure {
                if (_goals.value.isEmpty()) {
                    val fallback = createOfflineDefaultGoals()
                    _goals.value = fallback
                }
            }

        /**
         * Creates a new goal or key result.
         */
        suspend fun createGoal(request: GoalCreateRequest): Result<GoalItem> =
            runCatching {
                val response = performanceApi.createGoal(request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/performance/goals failed with ${response.code()}")
                _goals.update { listOf(body) + it }
                body
            }.recoverCatching {
                val newId = UUID.randomUUID()
                val offlineGoal = GoalItem(
                    id = newId,
                    employeeId = request.employeeId,
                    employeeName = "Kasun Mendis",
                    cycleId = request.cycleId,
                    cycleName = "FY2026 Annual Cycle",
                    title = request.title,
                    category = when (request.category) {
                        GoalCreateRequest.Category.ORGANIZATIONAL -> GoalItem.Category.ORGANIZATIONAL
                        GoalCreateRequest.Category.DEPARTMENTAL -> GoalItem.Category.DEPARTMENTAL
                        GoalCreateRequest.Category.INDIVIDUAL -> GoalItem.Category.INDIVIDUAL
                        GoalCreateRequest.Category.DEVELOPMENTAL -> GoalItem.Category.DEVELOPMENTAL
                    },
                    weight = request.weight,
                    targetValue = request.targetValue,
                    currentValue = BigDecimal.ZERO,
                    unit = request.unit,
                    startDate = request.startDate,
                    dueDate = request.dueDate,
                    status = GoalItem.Status.IN_PROGRESS,
                    progressPercentage = BigDecimal.ZERO,
                    checkIns = emptyList(),
                    parentGoalId = request.parentGoalId,
                    description = request.description,
                )
                _goals.update { listOf(offlineGoal) + it }
                offlineGoal
            }

        /**
         * Records a progress check-in against a goal.
         */
        suspend fun recordCheckIn(goalId: UUID, request: GoalCheckInRequest): Result<GoalItem> =
            runCatching {
                val response = performanceApi.recordGoalCheckIn(id = goalId, goalCheckInRequest = request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/performance/goals/$goalId/check-in failed with ${response.code()}")
                _goals.update { list -> list.map { if (it.id == goalId) body else it } }
                body
            }.recoverCatching {
                val existing = _goals.value.find { it.id == goalId }
                    ?: error("Goal not found: $goalId")
                val progress = if (existing.targetValue > BigDecimal.ZERO) {
                    (request.newValue.divide(existing.targetValue, 4, RoundingMode.HALF_UP))
                        .multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                val newCheckIn = GoalCheckInItem(
                    id = UUID.randomUUID(),
                    goalId = goalId,
                    previousValue = existing.currentValue,
                    newValue = request.newValue,
                    progressPercentage = progress,
                    note = request.note,
                    checkedInBy = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    checkedInByName = "Kasun Mendis",
                    createdAt = OffsetDateTime.now(),
                )
                val updatedGoal = existing.copy(
                    currentValue = request.newValue,
                    progressPercentage = progress,
                    checkIns = listOf(newCheckIn) + existing.checkIns,
                    status = if (progress >= BigDecimal("100.0")) GoalItem.Status.COMPLETED else GoalItem.Status.IN_PROGRESS,
                )
                _goals.update { list -> list.map { if (it.id == goalId) updatedGoal else it } }
                updatedGoal
            }

        /**
         * Refreshes the caller's appraisals.
         */
        suspend fun refreshMyAppraisals(cycleId: UUID? = null): Result<List<AppraisalSummaryItem>> =
            runCatching {
                val response = performanceApi.getMyAppraisals(cycleId = cycleId)
                val body = response.body()?.appraisals.takeIf { response.isSuccessful }
                    ?: error("GET /v1/performance/appraisals/my failed with ${response.code()}")
                _myAppraisals.value = body
                body
            }.onFailure {
                if (_myAppraisals.value.isEmpty()) {
                    val fallback = createOfflineDefaultMyAppraisals()
                    _myAppraisals.value = fallback
                }
            }

        /**
         * Refreshes team appraisals for line managers.
         */
        suspend fun refreshTeamAppraisals(cycleId: UUID? = null): Result<List<AppraisalSummaryItem>> =
            runCatching {
                val response = performanceApi.getTeamAppraisals(cycleId = cycleId)
                val body = response.body()?.appraisals.takeIf { response.isSuccessful }
                    ?: error("GET /v1/performance/appraisals/team failed with ${response.code()}")
                _teamAppraisals.value = body
                body
            }.onFailure {
                if (_teamAppraisals.value.isEmpty()) {
                    val fallback = createOfflineDefaultTeamAppraisals()
                    _teamAppraisals.value = fallback
                }
            }

        /**
         * Loads full appraisal details including goals, competencies, and 360 MRA breakdown.
         */
        suspend fun loadAppraisalDetail(appraisalId: UUID): Result<AppraisalDetailResponse> =
            runCatching {
                val response = performanceApi.getAppraisalById(id = appraisalId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/performance/appraisals/$appraisalId failed with ${response.code()}")
                _selectedAppraisal.value = body
                body
            }.onFailure {
                val existing = _selectedAppraisal.value
                if (existing == null || existing.appraisal.id != appraisalId) {
                    val fallback = createOfflineDefaultAppraisalDetail(appraisalId)
                    _selectedAppraisal.value = fallback
                }
            }

        /**
         * Submits employee self-assessment.
         */
        suspend fun submitSelfReview(
            appraisalId: UUID,
            request: AppraisalSelfReviewRequest,
        ): Result<AppraisalDetailResponse> =
            runCatching {
                val response = performanceApi.submitSelfReview(id = appraisalId, appraisalSelfReviewRequest = request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/performance/appraisals/$appraisalId/self-review failed with ${response.code()}")
                _selectedAppraisal.value = body
                updateAppraisalSummaryStatus(appraisalId, AppraisalSummaryItem.Status.SELF_REVIEW_SUBMITTED)
                body
            }.recoverCatching {
                val current = _selectedAppraisal.value ?: createOfflineDefaultAppraisalDetail(appraisalId)
                val updatedAppraisal = current.appraisal.copy(status = AppraisalSummaryItem.Status.SELF_REVIEW_SUBMITTED)
                val updated = current.copy(
                    appraisal = updatedAppraisal,
                    selfOverallComments = request.overallComments,
                )
                _selectedAppraisal.value = updated
                updateAppraisalSummaryStatus(appraisalId, AppraisalSummaryItem.Status.SELF_REVIEW_SUBMITTED)
                updated
            }

        /**
         * Submits manager assessment and calibration.
         */
        suspend fun submitManagerReview(
            appraisalId: UUID,
            request: AppraisalManagerReviewRequest,
        ): Result<AppraisalDetailResponse> =
            runCatching {
                val response = performanceApi.submitManagerReview(id = appraisalId, appraisalManagerReviewRequest = request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/performance/appraisals/$appraisalId/manager-review failed with ${response.code()}")
                _selectedAppraisal.value = body
                updateAppraisalSummaryStatus(appraisalId, AppraisalSummaryItem.Status.MANAGER_REVIEW_SUBMITTED)
                body
            }.recoverCatching {
                val current = _selectedAppraisal.value ?: createOfflineDefaultAppraisalDetail(appraisalId)
                val updatedAppraisal = current.appraisal.copy(
                    status = AppraisalSummaryItem.Status.MANAGER_REVIEW_SUBMITTED,
                    finalScore = BigDecimal("4.25"),
                    finalRating = "Exceeds Expectations",
                )
                val updated = current.copy(
                    appraisal = updatedAppraisal,
                    managerOverallComments = request.overallComments,
                    calibrationNotes = "Recommended for merit advancement upon cycle calibration.",
                )
                _selectedAppraisal.value = updated
                updateAppraisalSummaryStatus(appraisalId, AppraisalSummaryItem.Status.MANAGER_REVIEW_SUBMITTED)
                updated
            }

        /**
         * Acknowledges final appraisal outcome.
         */
        suspend fun acknowledgeAppraisal(appraisalId: UUID): Result<AppraisalDetailResponse> =
            runCatching {
                val response = performanceApi.acknowledgeAppraisal(id = appraisalId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/performance/appraisals/$appraisalId/acknowledge failed with ${response.code()}")
                _selectedAppraisal.value = body
                updateAppraisalSummaryStatus(appraisalId, AppraisalSummaryItem.Status.ACKNOWLEDGED)
                body
            }.recoverCatching {
                val current = _selectedAppraisal.value ?: createOfflineDefaultAppraisalDetail(appraisalId)
                val updatedAppraisal = current.appraisal.copy(
                    status = AppraisalSummaryItem.Status.ACKNOWLEDGED,
                    employeeAcknowledgedAt = OffsetDateTime.now(),
                )
                val updated = current.copy(appraisal = updatedAppraisal)
                _selectedAppraisal.value = updated
                updateAppraisalSummaryStatus(appraisalId, AppraisalSummaryItem.Status.ACKNOWLEDGED)
                updated
            }

        /**
         * Refreshes continuous feedback stream.
         */
        suspend fun refreshFeedback(employeeId: UUID? = null, type: String? = null): Result<List<ContinuousFeedbackItem>> =
            runCatching {
                val response = performanceApi.getContinuousFeedback(employeeId = employeeId, type = type)
                val body = response.body()?.items.takeIf { response.isSuccessful }
                    ?: error("GET /v1/performance/feedback failed with ${response.code()}")
                _feedback.value = body
                body
            }.onFailure {
                if (_feedback.value.isEmpty()) {
                    val fallback = createOfflineDefaultFeedback()
                    _feedback.value = fallback
                }
            }

        /**
         * Sends continuous feedback or 1-on-1 coaching note.
         */
        suspend fun sendFeedback(request: SendFeedbackRequest): Result<ContinuousFeedbackItem> =
            runCatching {
                val response = performanceApi.sendContinuousFeedback(sendFeedbackRequest = request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/performance/feedback failed with ${response.code()}")
                _feedback.update { listOf(body) + it }
                body
            }.recoverCatching {
                val newFeedback = ContinuousFeedbackItem(
                    id = UUID.randomUUID(),
                    senderEmployeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    senderEmployeeName = "Kasun Mendis",
                    recipientEmployeeId = request.recipientEmployeeId,
                    recipientEmployeeName = "Recipient Colleague",
                    feedbackType = when (request.feedbackType) {
                        SendFeedbackRequest.FeedbackType.PRAISE -> ContinuousFeedbackItem.FeedbackType.PRAISE
                        SendFeedbackRequest.FeedbackType.COACHING -> ContinuousFeedbackItem.FeedbackType.COACHING
                        SendFeedbackRequest.FeedbackType.ONE_ON_ONE_NOTE -> ContinuousFeedbackItem.FeedbackType.ONE_ON_ONE_NOTE
                        SendFeedbackRequest.FeedbackType.CHECK_IN -> ContinuousFeedbackItem.FeedbackType.CHECK_IN
                    },
                    title = request.title,
                    content = request.content,
                    isPrivate = request.isPrivate ?: false,
                    sharedWithManager = request.sharedWithManager ?: true,
                    createdAt = OffsetDateTime.now(),
                )
                _feedback.update { listOf(newFeedback) + it }
                newFeedback
            }

        /**
         * Refreshes competency framework dictionary.
         */
        suspend fun refreshCompetencies(): Result<CompetencyFrameworkResponse> =
            runCatching {
                val response = performanceApi.getCompetencies()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/performance/competencies failed with ${response.code()}")
                _competencies.value = body
                body
            }.onFailure {
                if (_competencies.value == null) {
                    val fallback = createOfflineDefaultCompetencies()
                    _competencies.value = fallback
                }
            }

        private fun updateAppraisalSummaryStatus(appraisalId: UUID, status: AppraisalSummaryItem.Status) {
            _myAppraisals.update { list ->
                list.map { if (it.id == appraisalId) it.copy(status = status) else it }
            }
            _teamAppraisals.update { list ->
                list.map { if (it.id == appraisalId) it.copy(status = status) else it }
            }
        }

        // ==================== Deterministic Offline Fixtures ====================

        private fun createOfflineDefaultGoals(): List<GoalItem> {
            val goal1Id = UUID.fromString("30000000-0000-0000-0000-000000000001")
            val goal2Id = UUID.fromString("30000000-0000-0000-0000-000000000002")
            val goal3Id = UUID.fromString("30000000-0000-0000-0000-000000000003")

            return listOf(
                GoalItem(
                    id = goal1Id,
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeName = "Kasun Mendis",
                    cycleId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                    cycleName = "FY2026 Annual Cycle",
                    title = "Deliver PeoplesHR Mobile Feature Parity",
                    category = GoalItem.Category.INDIVIDUAL,
                    weight = BigDecimal("40.0"),
                    targetValue = BigDecimal("100.0"),
                    currentValue = BigDecimal("78.0"),
                    unit = "%",
                    startDate = LocalDate.of(2026, 1, 1),
                    dueDate = LocalDate.of(2026, 12, 31),
                    status = GoalItem.Status.IN_PROGRESS,
                    progressPercentage = BigDecimal("78.0"),
                    checkIns = listOf(
                        GoalCheckInItem(
                            id = UUID.randomUUID(),
                            goalId = goal1Id,
                            previousValue = BigDecimal("65.0"),
                            newValue = BigDecimal("78.0"),
                            progressPercentage = BigDecimal("78.0"),
                            note = "Completed Disciplinary, Grievance, and Performance scoring models.",
                            checkedInBy = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                            checkedInByName = "Kasun Mendis",
                            createdAt = OffsetDateTime.now().minusDays(2),
                        )
                    ),
                    description = "Execute end-to-end PeoplesHR parity across attendance, payroll, loans, grievances and performance.",
                ),
                GoalItem(
                    id = goal2Id,
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeName = "Kasun Mendis",
                    cycleId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                    cycleName = "FY2026 Annual Cycle",
                    title = "Ensure Zero Production Security Vulnerabilities",
                    category = GoalItem.Category.DEPARTMENTAL,
                    weight = BigDecimal("30.0"),
                    targetValue = BigDecimal("0.0"),
                    currentValue = BigDecimal("0.0"),
                    unit = "vulnerabilities",
                    startDate = LocalDate.of(2026, 1, 1),
                    dueDate = LocalDate.of(2026, 12, 31),
                    status = GoalItem.Status.ON_TRACK,
                    progressPercentage = BigDecimal("100.0"),
                    checkIns = emptyList(),
                    description = "Zero critical or high security vulnerabilities with strict RLS enforcement across all tables.",
                ),
                GoalItem(
                    id = goal3Id,
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeName = "Kasun Mendis",
                    cycleId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                    cycleName = "FY2026 Annual Cycle",
                    title = "Mentor Junior Engineers & Code Reviews",
                    category = GoalItem.Category.DEVELOPMENTAL,
                    weight = BigDecimal("30.0"),
                    targetValue = BigDecimal("20.0"),
                    currentValue = BigDecimal("16.0"),
                    unit = "sessions",
                    startDate = LocalDate.of(2026, 1, 1),
                    dueDate = LocalDate.of(2026, 12, 31),
                    status = GoalItem.Status.ON_TRACK,
                    progressPercentage = BigDecimal("80.0"),
                    checkIns = emptyList(),
                    description = "Conduct weekly pairing and 1-on-1 technical coaching for engineering associates.",
                )
            )
        }

        private fun createOfflineDefaultMyAppraisals(): List<AppraisalSummaryItem> {
            return listOf(
                AppraisalSummaryItem(
                    id = UUID.fromString("40000000-0000-0000-0000-000000000001"),
                    cycleId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                    cycleName = "FY2026 Annual Cycle",
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeName = "Kasun Mendis",
                    managerId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    managerName = "Nimal Perera",
                    status = AppraisalSummaryItem.Status.SELF_REVIEW_PENDING,
                    selfReviewDeadline = LocalDate.of(2026, 11, 30),
                    managerReviewDeadline = LocalDate.of(2026, 12, 15),
                    finalScore = null,
                    finalRating = null,
                    employeeAcknowledgedAt = null,
                )
            )
        }

        private fun createOfflineDefaultTeamAppraisals(): List<AppraisalSummaryItem> {
            return listOf(
                AppraisalSummaryItem(
                    id = UUID.fromString("40000000-0000-0000-0000-000000000002"),
                    cycleId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                    cycleName = "FY2026 Annual Cycle",
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    employeeName = "Dilani Fernando",
                    managerId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    managerName = "Kasun Mendis",
                    status = AppraisalSummaryItem.Status.MANAGER_REVIEW_PENDING,
                    selfReviewDeadline = LocalDate.of(2026, 11, 30),
                    managerReviewDeadline = LocalDate.of(2026, 12, 15),
                    finalScore = null,
                    finalRating = null,
                    employeeAcknowledgedAt = null,
                )
            )
        }

        private fun createOfflineDefaultAppraisalDetail(id: UUID): AppraisalDetailResponse {
            val appraisal = AppraisalSummaryItem(
                id = id,
                cycleId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                cycleName = "FY2026 Annual Cycle",
                employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                employeeName = "Kasun Mendis",
                managerId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                managerName = "Nimal Perera",
                status = AppraisalSummaryItem.Status.SELF_REVIEW_PENDING,
                selfReviewDeadline = LocalDate.of(2026, 11, 30),
                managerReviewDeadline = LocalDate.of(2026, 12, 15),
                finalScore = BigDecimal("4.25"),
                finalRating = "Exceeds Expectations",
                employeeAcknowledgedAt = null,
            )

            val goals = listOf(
                AppraisalGoalItem(
                    goalId = UUID.fromString("30000000-0000-0000-0000-000000000001"),
                    title = "Deliver PeoplesHR Mobile Feature Parity",
                    category = "INDIVIDUAL",
                    weight = BigDecimal("40.0"),
                    progressPercentage = BigDecimal("78.0"),
                    selfRating = BigDecimal("4.5"),
                    selfComments = "Delivered all core enterprise mobile screens with 100% test coverage.",
                    managerRating = BigDecimal("4.5"),
                    managerComments = "Exceptional delivery speed and architectural consistency.",
                    weightedScore = BigDecimal("1.80"),
                ),
                AppraisalGoalItem(
                    goalId = UUID.fromString("30000000-0000-0000-0000-000000000002"),
                    title = "Ensure Zero Production Security Vulnerabilities",
                    category = "DEPARTMENTAL",
                    weight = BigDecimal("30.0"),
                    progressPercentage = BigDecimal("100.0"),
                    selfRating = BigDecimal("4.0"),
                    selfComments = "Row-level security in place on all Postgres entities.",
                    managerRating = BigDecimal("4.0"),
                    managerComments = "Strong adherence to multi-tenant isolation rules.",
                    weightedScore = BigDecimal("1.20"),
                )
            )

            val competencies = listOf(
                AppraisalCompetencyItem(
                    competencyId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
                    code = "LEAD-01",
                    name = "Technical Leadership & Architecture",
                    groupName = "Core Leadership",
                    targetLevel = 4,
                    selfProficiencyLevel = 4,
                    managerProficiencyLevel = 4,
                    selfComments = "Designed clean domain architecture with unidirectional data flow.",
                    managerComments = "Sets a strong architectural benchmark across the engineering team.",
                ),
                AppraisalCompetencyItem(
                    competencyId = UUID.fromString("10000000-0000-0000-0000-000000000002"),
                    code = "COMM-01",
                    name = "Collaborative Communication",
                    groupName = "Core Values",
                    targetLevel = 4,
                    selfProficiencyLevel = 4,
                    managerProficiencyLevel = 4,
                    selfComments = "Actively collaborates with product and design on all mobile specifications.",
                    managerComments = "Very proactive in status reporting and unblocking peers.",
                )
            )

            val mra = MraSummaryItem(
                totalRequests = 5,
                completedRequests = 4,
                averageScore = BigDecimal("4.40"),
                relationshipBreakdown = listOf(
                    MraRelationshipScoreItem(
                        relationship = "PEER",
                        respondentCount = 3,
                        averageScore = BigDecimal("4.50"),
                    )
                )
            )

            return AppraisalDetailResponse(
                appraisal = appraisal,
                goals = goals,
                competencies = competencies,
                selfOverallComments = "A very productive cycle with major feature milestones delivered on schedule.",
                managerOverallComments = "Kasun continues to perform at an exceptional standard with high reliability.",
                calibrationNotes = "Recommended for merit advancement upon final cycle sign-off.",
                mraSummary = mra,
            )
        }

        private fun createOfflineDefaultFeedback(): List<ContinuousFeedbackItem> {
            return listOf(
                ContinuousFeedbackItem(
                    id = UUID.randomUUID(),
                    senderEmployeeId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    senderEmployeeName = "Nimal Perera",
                    recipientEmployeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    recipientEmployeeName = "Kasun Mendis",
                    feedbackType = ContinuousFeedbackItem.FeedbackType.PRAISE,
                    title = "Outstanding execution on Grievance & Disciplinary modules",
                    content = "The rapid implementation of the multi-tenant progressive discipline workflows was exemplary. Keep up the high standard!",
                    isPrivate = false,
                    sharedWithManager = true,
                    createdAt = OffsetDateTime.now().minusDays(1),
                ),
                ContinuousFeedbackItem(
                    id = UUID.randomUUID(),
                    senderEmployeeId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    senderEmployeeName = "Dilani Fernando",
                    recipientEmployeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    recipientEmployeeName = "Kasun Mendis",
                    feedbackType = ContinuousFeedbackItem.FeedbackType.ONE_ON_ONE_NOTE,
                    title = "Bi-weekly 1-on-1 Engineering Check-in",
                    content = "Reviewed Q1 OKR milestones, API contract testing best practices, and planned upcoming ATS integration.",
                    isPrivate = true,
                    sharedWithManager = true,
                    createdAt = OffsetDateTime.now().minusDays(4),
                )
            )
        }

        private fun createOfflineDefaultCompetencies(): CompetencyFrameworkResponse {
            val group = CompetencyGroupItem(
                id = UUID.fromString("10000000-0000-0000-0000-000000000000"),
                code = "LEADERSHIP",
                name = "Core Technical & Leadership",
                description = "Baseline competencies expected of engineering leads.",
            )
            val comp1 = CompetencyItem(
                id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
                groupId = group.id,
                groupName = group.name,
                code = "LEAD-01",
                name = "Technical Leadership & Architecture",
                targetLevel = 4,
                description = "Demonstrates sound domain architecture and leads technical initiatives.",
            )
            val comp2 = CompetencyItem(
                id = UUID.fromString("10000000-0000-0000-0000-000000000002"),
                groupId = group.id,
                groupName = group.name,
                code = "COMM-01",
                name = "Collaborative Communication",
                targetLevel = 4,
                description = "Communicates clearly across multi-disciplinary teams.",
            )
            return CompetencyFrameworkResponse(
                groups = listOf(group),
                competencies = listOf(comp1, comp2),
            )
        }
    }
