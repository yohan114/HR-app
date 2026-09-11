package com.hr.onboarding.internal

import com.hr.onboarding.*
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
@Table(name = "onboarding_stage")
class OnboardingStageEntity(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "sequence", nullable = false)
    var sequence: Int = 1,
    @Column(name = "days_offset", nullable = false)
    var daysOffset: Int = 0,
) : TenantScopedEntity()

@Entity
@Table(name = "onboarding_profile")
class OnboardingProfileEntity(
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "department_id")
    var departmentId: UUID? = null,
    @Column(name = "description")
    var description: String? = null,
    @Column(name = "active", nullable = false)
    var active: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "onboarding_action")
class OnboardingActionEntity(
    @Column(name = "profile_id", nullable = false)
    var profileId: UUID,
    @Column(name = "stage_id", nullable = false)
    var stageId: UUID,
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_role", nullable = false, length = 32)
    var ownerRole: OnboardingOwnerRole = OnboardingOwnerRole.NEW_HIRE,
    @Column(name = "mandatory", nullable = false)
    var mandatory: Boolean = true,
    @Column(name = "due_days_offset", nullable = false)
    var dueDaysOffset: Int = 0,
) : TenantScopedEntity()

@Entity
@Table(name = "onboarding_instance")
class OnboardingInstanceEntity(
    @Column(name = "candidate_id")
    var candidateId: UUID? = null,
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "profile_id", nullable = false)
    var profileId: UUID,
    @Column(name = "join_date", nullable = false)
    var joinDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: OnboardingInstanceStatus = OnboardingInstanceStatus.IN_PROGRESS,
    @Column(name = "progress_pct", precision = 5, scale = 2, nullable = false)
    var progressPct: BigDecimal = BigDecimal.ZERO,
    @Column(name = "buddy_employee_id")
    var buddyEmployeeId: UUID? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "onboarding_task")
class OnboardingTaskEntity(
    @Column(name = "instance_id", nullable = false)
    var instanceId: UUID,
    @Column(name = "action_id")
    var actionId: UUID? = null,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "description")
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_role", nullable = false, length = 32)
    var ownerRole: OnboardingOwnerRole = OnboardingOwnerRole.NEW_HIRE,
    @Column(name = "assignee_employee_id")
    var assigneeEmployeeId: UUID? = null,
    @Column(name = "due_date", nullable = false)
    var dueDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: OnboardingTaskStatus = OnboardingTaskStatus.PENDING,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(name = "notes")
    var notes: String? = null,
    @Column(name = "attachment_url", length = 512)
    var attachmentUrl: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "exit_type")
class ExitTypeEntity(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "voluntary", nullable = false)
    var voluntary: Boolean = true,
    @Column(name = "notice_days", nullable = false)
    var noticeDays: Int = 30,
    @Column(name = "requires_interview", nullable = false)
    var requiresInterview: Boolean = true,
    @Column(name = "requires_clearance", nullable = false)
    var requiresClearance: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "exit_reason")
class ExitReasonEntity(
    @Column(name = "exit_type_id", nullable = false)
    var exitTypeId: UUID,
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "category", nullable = false, length = 64)
    var category: String = "CAREER",
) : TenantScopedEntity()

@Entity
@Table(name = "exit_notice")
class ExitNoticeEntity(
    @Column(name = "notice_number", nullable = false, length = 64)
    var noticeNumber: String,
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "exit_type_id", nullable = false)
    var exitTypeId: UUID,
    @Column(name = "exit_reason_id")
    var exitReasonId: UUID? = null,
    @Column(name = "notice_date", nullable = false)
    var noticeDate: LocalDate,
    @Column(name = "requested_last_working_date", nullable = false)
    var requestedLastWorkingDate: LocalDate,
    @Column(name = "approved_last_working_date")
    var approvedLastWorkingDate: LocalDate? = null,
    @Column(name = "remarks")
    var remarks: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ExitNoticeStatus = ExitNoticeStatus.SUBMITTED,
    @Column(name = "reversal_reason")
    var reversalReason: String? = null,
    @Column(name = "approved_by")
    var approvedBy: UUID? = null,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "exit_interview")
class ExitInterviewEntity(
    @Column(name = "exit_notice_id", nullable = false)
    var exitNoticeId: UUID,
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "interviewer_employee_id")
    var interviewerEmployeeId: UUID? = null,
    @Column(name = "conducted_at", nullable = false)
    var conductedAt: Instant = Instant.now(),
    @Column(name = "overall_experience_rating", nullable = false)
    var overallExperienceRating: Int = 3,
    @Column(name = "management_rating", nullable = false)
    var managementRating: Int = 3,
    @Column(name = "culture_rating", nullable = false)
    var cultureRating: Int = 3,
    @Column(name = "reason_details")
    var reasonDetails: String? = null,
    @Column(name = "suggestions")
    var suggestions: String? = null,
    @Column(name = "would_recommend", nullable = false)
    var wouldRecommend: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "clearance_item")
class ClearanceItemEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false, length = 32)
    var department: ClearanceDepartment,
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
    @Column(name = "default_assignee_role", nullable = false, length = 32)
    var defaultAssigneeRole: String = "DEPARTMENT_LEAD",
) : TenantScopedEntity()

@Entity
@Table(name = "clearance_task")
class ClearanceTaskEntity(
    @Column(name = "exit_notice_id", nullable = false)
    var exitNoticeId: UUID,
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "item_id")
    var itemId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false, length = 32)
    var department: ClearanceDepartment,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "assignee_employee_id")
    var assigneeEmployeeId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ClearanceTaskStatus = ClearanceTaskStatus.PENDING,
    @Column(name = "cleared_at")
    var clearedAt: Instant? = null,
    @Column(name = "remarks")
    var remarks: String? = null,
    @Column(name = "recoverable_amount", precision = 12, scale = 2, nullable = false)
    var recoverableAmount: BigDecimal = BigDecimal.ZERO,
) : TenantScopedEntity()
