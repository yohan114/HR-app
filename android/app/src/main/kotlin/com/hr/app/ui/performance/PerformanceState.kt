package com.hr.app.ui.performance

import com.hr.client.model.*
import java.util.UUID

enum class PerformanceTab(val label: String) {
    GOALS_OKRS("Goals & OKRs"),
    MY_APPRAISAL("My Appraisal"),
    TEAM_APPRAISALS("Team Reviews"),
    FEEDBACK("Feedback & 1-on-1"),
}

data class PerformanceState(
    val currentTab: PerformanceTab = PerformanceTab.GOALS_OKRS,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    // Goals & OKRs
    val goals: List<GoalItem> = emptyList(),
    val selectedCategoryFilter: GoalItem.Category? = null,
    val isCheckInDialogOpen: Boolean = false,
    val selectedGoalForCheckIn: GoalItem? = null,
    val checkInNewValue: String = "",
    val checkInNote: String = "",
    val isCreateGoalDialogOpen: Boolean = false,
    val newGoalTitle: String = "",
    val newGoalDescription: String = "",
    val newGoalTargetValue: String = "100.0",
    val newGoalUnit: String = "%",
    val newGoalWeight: String = "20.0",
    val newGoalCategory: GoalCreateRequest.Category = GoalCreateRequest.Category.INDIVIDUAL,

    // Appraisals (Self)
    val myAppraisals: List<AppraisalSummaryItem> = emptyList(),
    val selectedAppraisalDetail: AppraisalDetailResponse? = null,
    val isDetailDialogOpen: Boolean = false,
    val isSelfReviewDialogOpen: Boolean = false,
    val selfOverallComments: String = "",

    // Appraisals (Team / Manager)
    val teamAppraisals: List<AppraisalSummaryItem> = emptyList(),
    val isManagerReviewDialogOpen: Boolean = false,
    val managerOverallComments: String = "",
    val managerCalibrationNotes: String = "",

    // Continuous Feedback
    val feedbackItems: List<ContinuousFeedbackItem> = emptyList(),
    val isSendFeedbackDialogOpen: Boolean = false,
    val feedbackType: SendFeedbackRequest.FeedbackType = SendFeedbackRequest.FeedbackType.PRAISE,
    val feedbackTitle: String = "",
    val feedbackContent: String = "",
    val feedbackRecipientId: UUID? = null,
    val isFeedbackPrivate: Boolean = false,
    val isFeedbackSharedWithManager: Boolean = true,
)
