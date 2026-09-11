package com.hr.performance

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

enum class GoalCategory {
    ORGANIZATIONAL,
    DEPARTMENTAL,
    INDIVIDUAL,
    DEVELOPMENTAL,
}

enum class GoalStatus {
    NOT_STARTED,
    IN_PROGRESS,
    ON_TRACK,
    AT_RISK,
    COMPLETED,
    CANCELLED,
}

enum class EvaluationCycleStatus {
    SETUP,
    ACTIVE,
    IN_EVALUATION,
    CALIBRATION,
    CLOSED,
}

enum class AppraisalStatus {
    NOT_STARTED,
    SELF_REVIEW_PENDING,
    SELF_REVIEW_SUBMITTED,
    MANAGER_REVIEW_PENDING,
    MANAGER_REVIEW_SUBMITTED,
    IN_CALIBRATION,
    ACKNOWLEDGED,
    CLOSED,
}

enum class FeedbackType {
    PRAISE,
    COACHING,
    ONE_ON_ONE_NOTE,
    CHECK_IN,
}

enum class MraRelationship {
    PEER,
    SUBORDINATE,
    STAKEHOLDER,
    CROSS_FUNCTIONAL,
}

enum class MraStatus {
    REQUESTED,
    COMPLETED,
    DECLINED,
}

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

data class GoalCheckInItemDto(
    val id: UUID,
    val goalId: UUID,
    val previousValue: BigDecimal,
    val newValue: BigDecimal,
    val progressPercentage: BigDecimal,
    val note: String?,
    val checkedInBy: UUID,
    val checkedInByName: String,
    val createdAt: Instant,
)

data class GoalItemDto(
    val id: UUID,
    val employeeId: UUID,
    val employeeName: String,
    val cycleId: UUID,
    val cycleName: String,
    val parentGoalId: UUID?,
    val title: String,
    val description: String?,
    val category: GoalCategory,
    val weight: BigDecimal,
    val targetValue: BigDecimal,
    val currentValue: BigDecimal,
    val unit: String,
    val startDate: LocalDate,
    val dueDate: LocalDate,
    val status: GoalStatus,
    val progressPercentage: BigDecimal,
    val checkIns: List<GoalCheckInItemDto> = emptyList(),
)

data class GoalListResponseDto(
    val goals: List<GoalItemDto>,
    val totalCount: Int,
    val completedCount: Int,
)

data class GoalCreateRequestDto(
    val employeeId: UUID,
    val cycleId: UUID,
    val parentGoalId: UUID? = null,
    val title: String,
    val description: String? = null,
    val category: GoalCategory,
    val weight: BigDecimal,
    val targetValue: BigDecimal,
    val unit: String = "%",
    val startDate: LocalDate,
    val dueDate: LocalDate,
)

data class GoalCheckInRequestDto(
    val newValue: BigDecimal,
    val note: String? = null,
)

data class CompetencyGroupItemDto(
    val id: UUID,
    val code: String,
    val name: String,
    val description: String?,
)

data class CompetencyItemDto(
    val id: UUID,
    val groupId: UUID,
    val groupName: String,
    val code: String,
    val name: String,
    val description: String?,
    val targetLevel: Int,
)

data class CompetencyFrameworkResponseDto(
    val groups: List<CompetencyGroupItemDto>,
    val competencies: List<CompetencyItemDto>,
)

data class AppraisalSummaryItemDto(
    val id: UUID,
    val cycleId: UUID,
    val cycleName: String,
    val employeeId: UUID,
    val employeeName: String,
    val managerId: UUID,
    val managerName: String,
    val status: AppraisalStatus,
    val finalScore: BigDecimal?,
    val finalRating: String?,
    val selfReviewDeadline: LocalDate,
    val managerReviewDeadline: LocalDate,
    val employeeAcknowledgedAt: Instant?,
)

data class AppraisalListResponseDto(
    val appraisals: List<AppraisalSummaryItemDto>,
)

data class AppraisalGoalItemDto(
    val goalId: UUID,
    val title: String,
    val category: String,
    val weight: BigDecimal,
    val progressPercentage: BigDecimal,
    val selfRating: BigDecimal?,
    val selfComments: String?,
    val managerRating: BigDecimal?,
    val managerComments: String?,
    val weightedScore: BigDecimal?,
)

data class AppraisalCompetencyItemDto(
    val competencyId: UUID,
    val code: String,
    val name: String,
    val groupName: String,
    val targetLevel: Int,
    val selfProficiencyLevel: Int?,
    val managerProficiencyLevel: Int?,
    val selfComments: String?,
    val managerComments: String?,
)

data class MraRelationshipScoreItemDto(
    val relationship: String,
    val respondentCount: Int,
    val averageScore: BigDecimal,
)

data class MraSummaryItemDto(
    val totalRequests: Int,
    val completedRequests: Int,
    val averageScore: BigDecimal?,
    val relationshipBreakdown: List<MraRelationshipScoreItemDto>,
)

data class AppraisalDetailResponseDto(
    val appraisal: AppraisalSummaryItemDto,
    val goals: List<AppraisalGoalItemDto>,
    val competencies: List<AppraisalCompetencyItemDto>,
    val selfOverallComments: String?,
    val managerOverallComments: String?,
    val calibrationNotes: String?,
    val mraSummary: MraSummaryItemDto?,
)

data class GoalRatingInputDto(
    val goalId: UUID,
    val rating: BigDecimal,
    val comments: String? = null,
)

data class CompetencyRatingInputDto(
    val competencyId: UUID,
    val proficiencyLevel: Int,
    val comments: String? = null,
)

data class AppraisalSelfReviewRequestDto(
    val overallComments: String? = null,
    val goalRatings: List<GoalRatingInputDto>,
    val competencyRatings: List<CompetencyRatingInputDto>,
)

data class AppraisalManagerReviewRequestDto(
    val overallComments: String? = null,
    val goalRatings: List<GoalRatingInputDto>,
    val competencyRatings: List<CompetencyRatingInputDto>,
)

data class ContinuousFeedbackItemDto(
    val id: UUID,
    val senderEmployeeId: UUID,
    val senderEmployeeName: String,
    val recipientEmployeeId: UUID,
    val recipientEmployeeName: String,
    val feedbackType: FeedbackType,
    val title: String,
    val content: String,
    val isPrivate: Boolean,
    val sharedWithManager: Boolean,
    val createdAt: Instant,
)

data class ContinuousFeedbackListResponseDto(
    val items: List<ContinuousFeedbackItemDto>,
)

data class SendFeedbackRequestDto(
    val recipientEmployeeId: UUID,
    val feedbackType: FeedbackType,
    val title: String,
    val content: String,
    val isPrivate: Boolean? = false,
    val sharedWithManager: Boolean? = true,
)
