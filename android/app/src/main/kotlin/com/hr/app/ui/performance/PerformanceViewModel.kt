package com.hr.app.ui.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.performance.PerformanceRepository
import com.hr.client.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PerformanceViewModel
    @Inject
    constructor(
        private val repository: PerformanceRepository,
    ) : ViewModel() {

        private val _state = MutableStateFlow(PerformanceState())
        val state: StateFlow<PerformanceState> = _state.asStateFlow()

        val goals = repository.goals
        val myAppraisals = repository.myAppraisals
        val teamAppraisals = repository.teamAppraisals
        val selectedAppraisal = repository.selectedAppraisal
        val feedback = repository.feedback
        val competencies = repository.competencies

        init {
            refreshAll()
        }

        fun refreshAll() {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
                repository.refreshGoals()
                repository.refreshMyAppraisals()
                repository.refreshTeamAppraisals()
                repository.refreshFeedback()
                repository.refreshCompetencies()
                _state.update { it.copy(isLoading = false) }
            }
        }

        fun selectTab(tab: PerformanceTab) {
            _state.update {
                it.copy(
                    currentTab = tab,
                    errorMessage = null,
                    successMessage = null,
                )
            }
        }

        fun filterByCategory(category: GoalItem.Category?) {
            _state.update { it.copy(selectedCategoryFilter = category) }
        }

        // ==================== Goals & Check-ins ====================

        fun openCheckInDialog(goal: GoalItem) {
            _state.update {
                it.copy(
                    isCheckInDialogOpen = true,
                    selectedGoalForCheckIn = goal,
                    checkInNewValue = goal.currentValue.toPlainString(),
                    checkInNote = "",
                )
            }
        }

        fun closeCheckInDialog() {
            _state.update {
                it.copy(
                    isCheckInDialogOpen = false,
                    selectedGoalForCheckIn = null,
                    checkInNewValue = "",
                    checkInNote = "",
                )
            }
        }

        fun updateCheckInNewValue(value: String) {
            _state.update { it.copy(checkInNewValue = value) }
        }

        fun updateCheckInNote(note: String) {
            _state.update { it.copy(checkInNote = note) }
        }

        fun submitCheckIn() {
            val goal = _state.value.selectedGoalForCheckIn ?: return
            val newValue = _state.value.checkInNewValue.toBigDecimalOrNull() ?: return
            val note = _state.value.checkInNote.trim()

            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val request = GoalCheckInRequest(newValue = newValue, note = note.ifBlank { null })
                repository.recordCheckIn(goal.id, request)
                    .onSuccess {
                        closeCheckInDialog()
                        _state.update {
                            it.copy(
                                isLoading = false,
                                successMessage = "Check-in recorded! Goal progress updated.",
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to record check-in.",
                            )
                        }
                    }
            }
        }

        fun openCreateGoalDialog() {
            _state.update {
                it.copy(
                    isCreateGoalDialogOpen = true,
                    newGoalTitle = "",
                    newGoalDescription = "",
                    newGoalTargetValue = "100.0",
                    newGoalUnit = "%",
                    newGoalWeight = "20.0",
                    newGoalCategory = GoalCreateRequest.Category.INDIVIDUAL,
                )
            }
        }

        fun closeCreateGoalDialog() {
            _state.update { it.copy(isCreateGoalDialogOpen = false) }
        }

        fun updateNewGoalTitle(title: String) {
            _state.update { it.copy(newGoalTitle = title) }
        }

        fun updateNewGoalDescription(desc: String) {
            _state.update { it.copy(newGoalDescription = desc) }
        }

        fun updateNewGoalTarget(target: String) {
            _state.update { it.copy(newGoalTargetValue = target) }
        }

        fun updateNewGoalUnit(unit: String) {
            _state.update { it.copy(newGoalUnit = unit) }
        }

        fun updateNewGoalWeight(weight: String) {
            _state.update { it.copy(newGoalWeight = weight) }
        }

        fun updateNewGoalCategory(category: GoalCreateRequest.Category) {
            _state.update { it.copy(newGoalCategory = category) }
        }

        fun submitCreateGoal() {
            val title = _state.value.newGoalTitle.trim()
            if (title.isBlank()) {
                _state.update { it.copy(errorMessage = "Goal title cannot be blank.") }
                return
            }
            val target = _state.value.newGoalTargetValue.toBigDecimalOrNull() ?: BigDecimal("100.0")
            val weight = _state.value.newGoalWeight.toBigDecimalOrNull() ?: BigDecimal("20.0")

            val request = GoalCreateRequest(
                employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                cycleId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                title = title,
                description = _state.value.newGoalDescription.trim().ifBlank { null },
                category = _state.value.newGoalCategory,
                weight = weight,
                targetValue = target,
                unit = _state.value.newGoalUnit.ifBlank { "%" },
                startDate = LocalDate.now(),
                dueDate = LocalDate.now().plusMonths(3),
            )

            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                repository.createGoal(request)
                    .onSuccess {
                        closeCreateGoalDialog()
                        _state.update {
                            it.copy(
                                isLoading = false,
                                successMessage = "New goal created successfully.",
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to create goal.",
                            )
                        }
                    }
            }
        }

        // ==================== Appraisals ====================

        fun openAppraisalDetail(appraisal: AppraisalSummaryItem) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                repository.loadAppraisalDetail(appraisal.id)
                    .onSuccess { detail ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                selectedAppraisalDetail = detail,
                                isDetailDialogOpen = true,
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to load appraisal details.",
                            )
                        }
                    }
            }
        }

        fun closeAppraisalDetail() {
            _state.update {
                it.copy(
                    isDetailDialogOpen = false,
                    selectedAppraisalDetail = null,
                )
            }
        }

        fun openSelfReviewDialog() {
            _state.update {
                it.copy(
                    isSelfReviewDialogOpen = true,
                    selfOverallComments = it.selectedAppraisalDetail?.selfOverallComments ?: "",
                )
            }
        }

        fun closeSelfReviewDialog() {
            _state.update { it.copy(isSelfReviewDialogOpen = false) }
        }

        fun updateSelfOverallComments(comments: String) {
            _state.update { it.copy(selfOverallComments = comments) }
        }

        fun submitSelfReview() {
            val detail = _state.value.selectedAppraisalDetail ?: return
            val comments = _state.value.selfOverallComments.trim()

            val request = AppraisalSelfReviewRequest(
                goalRatings = detail.goals.map {
                    GoalRatingInput(
                        goalId = it.goalId,
                        rating = it.selfRating ?: BigDecimal("4.0"),
                        comments = it.selfComments ?: "Accomplished key objectives.",
                    )
                },
                competencyRatings = detail.competencies.map {
                    CompetencyRatingInput(
                        competencyId = it.competencyId,
                        proficiencyLevel = it.selfProficiencyLevel ?: 4,
                        comments = it.selfComments ?: "Demonstrated core competency behaviours.",
                    )
                },
                overallComments = comments.ifBlank { null },
            )

            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                repository.submitSelfReview(detail.appraisal.id, request)
                    .onSuccess { updated ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                selectedAppraisalDetail = updated,
                                isSelfReviewDialogOpen = false,
                                successMessage = "Self-review submitted successfully!",
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to submit self-review.",
                            )
                        }
                    }
            }
        }

        fun openManagerReviewDialog() {
            _state.update {
                it.copy(
                    isManagerReviewDialogOpen = true,
                    managerOverallComments = it.selectedAppraisalDetail?.managerOverallComments ?: "",
                    managerCalibrationNotes = it.selectedAppraisalDetail?.calibrationNotes ?: "",
                )
            }
        }

        fun closeManagerReviewDialog() {
            _state.update { it.copy(isManagerReviewDialogOpen = false) }
        }

        fun updateManagerOverallComments(comments: String) {
            _state.update { it.copy(managerOverallComments = comments) }
        }

        fun updateManagerCalibrationNotes(notes: String) {
            _state.update { it.copy(managerCalibrationNotes = notes) }
        }

        fun submitManagerReview() {
            val detail = _state.value.selectedAppraisalDetail ?: return
            val comments = _state.value.managerOverallComments.trim()
            val calib = _state.value.managerCalibrationNotes.trim()

            val request = AppraisalManagerReviewRequest(
                goalRatings = detail.goals.map {
                    GoalRatingInput(
                        goalId = it.goalId,
                        rating = it.managerRating ?: BigDecimal("4.5"),
                        comments = it.managerComments ?: "High-quality output delivered consistently.",
                    )
                },
                competencyRatings = detail.competencies.map {
                    CompetencyRatingInput(
                        competencyId = it.competencyId,
                        proficiencyLevel = it.managerProficiencyLevel ?: 4,
                        comments = it.managerComments ?: "Role model performance.",
                    )
                },
                overallComments = if (calib.isNotBlank()) "$comments\nCalibration: $calib" else comments.ifBlank { null },
            )

            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                repository.submitManagerReview(detail.appraisal.id, request)
                    .onSuccess { updated ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                selectedAppraisalDetail = updated,
                                isManagerReviewDialogOpen = false,
                                successMessage = "Manager evaluation and calibration submitted!",
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to submit manager review.",
                            )
                        }
                    }
            }
        }

        fun acknowledgeAppraisal() {
            val detail = _state.value.selectedAppraisalDetail ?: return

            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                repository.acknowledgeAppraisal(detail.appraisal.id)
                    .onSuccess { updated ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                selectedAppraisalDetail = updated,
                                successMessage = "Appraisal outcome signed and acknowledged.",
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to acknowledge appraisal.",
                            )
                        }
                    }
            }
        }

        // ==================== Continuous Feedback ====================

        fun openSendFeedbackDialog() {
            _state.update {
                it.copy(
                    isSendFeedbackDialogOpen = true,
                    feedbackType = SendFeedbackRequest.FeedbackType.PRAISE,
                    feedbackTitle = "",
                    feedbackContent = "",
                    feedbackRecipientId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    isFeedbackPrivate = false,
                    isFeedbackSharedWithManager = true,
                )
            }
        }

        fun closeSendFeedbackDialog() {
            _state.update { it.copy(isSendFeedbackDialogOpen = false) }
        }

        fun updateFeedbackType(type: SendFeedbackRequest.FeedbackType) {
            _state.update { it.copy(feedbackType = type) }
        }

        fun updateFeedbackTitle(title: String) {
            _state.update { it.copy(feedbackTitle = title) }
        }

        fun updateFeedbackContent(content: String) {
            _state.update { it.copy(feedbackContent = content) }
        }

        fun updateFeedbackPrivate(isPrivate: Boolean) {
            _state.update { it.copy(isFeedbackPrivate = isPrivate) }
        }

        fun submitFeedback() {
            val title = _state.value.feedbackTitle.trim()
            val content = _state.value.feedbackContent.trim()
            if (title.isBlank() || content.isBlank()) {
                _state.update { it.copy(errorMessage = "Title and feedback content are required.") }
                return
            }

            val request = SendFeedbackRequest(
                recipientEmployeeId = _state.value.feedbackRecipientId ?: UUID.fromString("00000000-0000-0000-0000-000000000002"),
                feedbackType = _state.value.feedbackType,
                title = title,
                content = content,
                isPrivate = _state.value.isFeedbackPrivate,
                sharedWithManager = _state.value.isFeedbackSharedWithManager,
            )

            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                repository.sendFeedback(request)
                    .onSuccess {
                        closeSendFeedbackDialog()
                        _state.update {
                            it.copy(
                                isLoading = false,
                                successMessage = "Feedback sent successfully!",
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to send feedback.",
                            )
                        }
                    }
            }
        }

        fun dismissMessages() {
            _state.update { it.copy(errorMessage = null, successMessage = null) }
        }
    }
