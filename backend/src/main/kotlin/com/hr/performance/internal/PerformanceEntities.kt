package com.hr.performance.internal

import com.hr.performance.*
import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "goal_cycle")
class GoalCycle(
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: EvaluationCycleStatus = EvaluationCycleStatus.ACTIVE,
) : TenantScopedEntity()

@Entity
@Table(name = "goal")
class GoalEntity(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "cycle_id", nullable = false)
    var cycleId: UUID,
    @Column(name = "parent_goal_id")
    var parentGoalId: UUID? = null,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "description")
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    var category: GoalCategory = GoalCategory.INDIVIDUAL,
    @Column(name = "weight", nullable = false, precision = 5, scale = 2)
    var weight: BigDecimal = BigDecimal("100.00"),
    @Column(name = "target_value", nullable = false, precision = 12, scale = 2)
    var targetValue: BigDecimal = BigDecimal("100.00"),
    @Column(name = "current_value", nullable = false, precision = 12, scale = 2)
    var currentValue: BigDecimal = BigDecimal.ZERO,
    @Column(name = "unit", nullable = false, length = 32)
    var unit: String = "%",
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "due_date", nullable = false)
    var dueDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: GoalStatus = GoalStatus.NOT_STARTED,
    @Column(name = "progress_percentage", nullable = false, precision = 5, scale = 2)
    var progressPercentage: BigDecimal = BigDecimal.ZERO,
) : TenantScopedEntity()

@Entity
@Table(name = "goal_check_in")
class GoalCheckInEntity(
    @Column(name = "goal_id", nullable = false)
    var goalId: UUID,
    @Column(name = "previous_value", nullable = false, precision = 12, scale = 2)
    var previousValue: BigDecimal,
    @Column(name = "new_value", nullable = false, precision = 12, scale = 2)
    var newValue: BigDecimal,
    @Column(name = "progress_percentage", nullable = false, precision = 5, scale = 2)
    var progressPercentage: BigDecimal,
    @Column(name = "note")
    var note: String? = null,
    @Column(name = "checked_in_by", nullable = false)
    var checkedInBy: UUID,
) : TenantScopedEntity()

@Entity
@Table(name = "competency_group")
class CompetencyGroup(
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "competency")
class CompetencyEntity(
    @Column(name = "group_id", nullable = false)
    var groupId: UUID,
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
    @Column(name = "target_level", nullable = false)
    var targetLevel: Int = 3,
) : TenantScopedEntity()

@Entity
@Table(name = "evaluation_cycle")
class EvaluationCycleEntity(
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
    @Column(name = "goal_weight", nullable = false, precision = 5, scale = 2)
    var goalWeight: BigDecimal = BigDecimal("60.00"),
    @Column(name = "competency_weight", nullable = false, precision = 5, scale = 2)
    var competencyWeight: BigDecimal = BigDecimal("30.00"),
    @Column(name = "mra_weight", nullable = false, precision = 5, scale = 2)
    var mraWeight: BigDecimal = BigDecimal("10.00"),
    @Column(name = "self_review_deadline", nullable = false)
    var selfReviewDeadline: LocalDate,
    @Column(name = "manager_review_deadline", nullable = false)
    var managerReviewDeadline: LocalDate,
    @Column(name = "calibration_deadline", nullable = false)
    var calibrationDeadline: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: EvaluationCycleStatus = EvaluationCycleStatus.ACTIVE,
) : TenantScopedEntity()

@Entity
@Table(name = "performance_appraisal")
class PerformanceAppraisalEntity(
    @Column(name = "cycle_id", nullable = false)
    var cycleId: UUID,
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "manager_id", nullable = false)
    var managerId: UUID,
    @Column(name = "second_reviewer_id")
    var secondReviewerId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: AppraisalStatus = AppraisalStatus.NOT_STARTED,

    @Column(name = "self_goals_score", precision = 5, scale = 2)
    var selfGoalsScore: BigDecimal? = null,
    @Column(name = "manager_goals_score", precision = 5, scale = 2)
    var managerGoalsScore: BigDecimal? = null,
    @Column(name = "self_competency_score", precision = 5, scale = 2)
    var selfCompetencyScore: BigDecimal? = null,
    @Column(name = "manager_competency_score", precision = 5, scale = 2)
    var managerCompetencyScore: BigDecimal? = null,
    @Column(name = "mra_score", precision = 5, scale = 2)
    var mraScore: BigDecimal? = null,
    @Column(name = "final_score", precision = 5, scale = 2)
    var finalScore: BigDecimal? = null,
    @Column(name = "final_rating", length = 64)
    var finalRating: String? = null,

    @Column(name = "self_overall_comments")
    var selfOverallComments: String? = null,
    @Column(name = "manager_overall_comments")
    var managerOverallComments: String? = null,
    @Column(name = "calibration_notes")
    var calibrationNotes: String? = null,
    @Column(name = "employee_acknowledged_at")
    var employeeAcknowledgedAt: Instant? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "appraisal_goal_rating")
class AppraisalGoalRatingEntity(
    @Column(name = "appraisal_id", nullable = false)
    var appraisalId: UUID,
    @Column(name = "goal_id", nullable = false)
    var goalId: UUID,
    @Column(name = "self_rating", precision = 5, scale = 2)
    var selfRating: BigDecimal? = null,
    @Column(name = "self_comments")
    var selfComments: String? = null,
    @Column(name = "manager_rating", precision = 5, scale = 2)
    var managerRating: BigDecimal? = null,
    @Column(name = "manager_comments")
    var managerComments: String? = null,
    @Column(name = "weighted_score", precision = 5, scale = 2)
    var weightedScore: BigDecimal? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "appraisal_competency_rating")
class AppraisalCompetencyRatingEntity(
    @Column(name = "appraisal_id", nullable = false)
    var appraisalId: UUID,
    @Column(name = "competency_id", nullable = false)
    var competencyId: UUID,
    @Column(name = "self_proficiency_level")
    var selfProficiencyLevel: Int? = null,
    @Column(name = "manager_proficiency_level")
    var managerProficiencyLevel: Int? = null,
    @Column(name = "self_comments")
    var selfComments: String? = null,
    @Column(name = "manager_comments")
    var managerComments: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "continuous_feedback")
class ContinuousFeedbackEntity(
    @Column(name = "sender_employee_id", nullable = false)
    var senderEmployeeId: UUID,
    @Column(name = "recipient_employee_id", nullable = false)
    var recipientEmployeeId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 32)
    var feedbackType: FeedbackType = FeedbackType.PRAISE,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "content", nullable = false)
    var content: String,
    @Column(name = "is_private", nullable = false)
    var isPrivate: Boolean = false,
    @Column(name = "shared_with_manager", nullable = false)
    var sharedWithManager: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "mra_request")
class MraRequestEntity(
    @Column(name = "appraisal_id", nullable = false)
    var appraisalId: UUID,
    @Column(name = "subject_employee_id", nullable = false)
    var subjectEmployeeId: UUID,
    @Column(name = "rater_employee_id", nullable = false)
    var raterEmployeeId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 32)
    var relationship: MraRelationship = MraRelationship.PEER,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: MraStatus = MraStatus.REQUESTED,
    @Column(name = "requested_at", nullable = false)
    var requestedAt: Instant = Instant.now(),
    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "mra_rating")
class MraRatingEntity(
    @Column(name = "mra_request_id", nullable = false)
    var mraRequestId: UUID,
    @Column(name = "competency_id", nullable = false)
    var competencyId: UUID,
    @Column(name = "score", nullable = false, precision = 3, scale = 1)
    var score: BigDecimal,
    @Column(name = "comments")
    var comments: String? = null,
) : TenantScopedEntity()
