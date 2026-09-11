package com.hr.onboarding

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

enum class OnboardingOwnerRole {
    NEW_HIRE,
    BUDDY,
    MANAGER,
    IT_OPS,
    HR_OPS
}

enum class OnboardingTaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    BLOCKED
}

enum class OnboardingInstanceStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

enum class ExitNoticeStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    WITHDRAWN,
    REVERSED
}

enum class ClearanceDepartment {
    IT_INFRASTRUCTURE,
    FINANCE_PAYROLL,
    HR_OPERATIONS,
    ADMIN_FACILITIES,
    LINE_MANAGER
}

enum class ClearanceTaskStatus {
    PENDING,
    IN_PROGRESS,
    CLEARED,
    WAIVED,
    REJECTED
}

// -----------------------------------------------------------------------------
// Onboarding DTOs
// -----------------------------------------------------------------------------

data class OnboardingProfileItem(
    val id: UUID,
    val code: String,
    val name: String,
    val departmentId: UUID? = null,
    val departmentName: String? = null,
    val description: String? = null,
    val active: Boolean = true,
)

data class OnboardingProfileListResponse(
    val items: List<OnboardingProfileItem>,
    val totalCount: Int,
)

data class OnboardingTaskItem(
    val id: UUID,
    val instanceId: UUID,
    val actionId: UUID? = null,
    val title: String,
    val description: String? = null,
    val ownerRole: OnboardingOwnerRole,
    val assigneeEmployeeId: UUID? = null,
    val assigneeName: String? = null,
    val dueDate: LocalDate,
    val status: OnboardingTaskStatus,
    val completedAt: OffsetDateTime? = null,
    val notes: String? = null,
    val attachmentUrl: String? = null,
)

data class OnboardingInstanceDetail(
    val id: UUID,
    val candidateId: UUID? = null,
    val employeeId: UUID,
    val employeeName: String,
    val employeeCode: String? = null,
    val profileId: UUID,
    val profileName: String,
    val joinDate: LocalDate,
    val status: OnboardingInstanceStatus,
    val progressPct: BigDecimal,
    val buddyEmployeeId: UUID? = null,
    val buddyName: String? = null,
    val completedAt: OffsetDateTime? = null,
    val tasks: List<OnboardingTaskItem> = emptyList(),
)

data class OnboardingInstanceSummary(
    val id: UUID,
    val employeeId: UUID,
    val employeeName: String,
    val employeeCode: String? = null,
    val profileId: UUID,
    val profileName: String,
    val joinDate: LocalDate,
    val status: OnboardingInstanceStatus,
    val progressPct: BigDecimal,
    val buddyEmployeeId: UUID? = null,
    val buddyName: String? = null,
    val totalTasks: Int,
    val completedTasks: Int,
)

data class OnboardingInstanceListResponse(
    val items: List<OnboardingInstanceSummary>,
    val totalCount: Int,
)

data class OnboardingInstanceCreateRequest(
    val employeeId: UUID,
    val profileId: UUID,
    val joinDate: LocalDate,
    val buddyEmployeeId: UUID? = null,
    val candidateId: UUID? = null,
)

data class OnboardingTaskCompleteRequest(
    val notes: String? = null,
    val attachmentUrl: String? = null,
)

// -----------------------------------------------------------------------------
// Offboarding DTOs
// -----------------------------------------------------------------------------

data class ExitReasonItem(
    val id: UUID,
    val code: String,
    val name: String,
    val category: String,
)

data class ExitTypeItem(
    val id: UUID,
    val code: String,
    val name: String,
    val voluntary: Boolean,
    val noticeDays: Int,
    val requiresInterview: Boolean,
    val requiresClearance: Boolean,
    val reasons: List<ExitReasonItem> = emptyList(),
)

data class ExitTypeListResponse(
    val items: List<ExitTypeItem>,
)

data class ExitNoticeItem(
    val id: UUID,
    val noticeNumber: String,
    val employeeId: UUID,
    val employeeName: String,
    val employeeCode: String? = null,
    val exitTypeId: UUID,
    val exitTypeCode: String,
    val exitTypeName: String,
    val exitReasonId: UUID? = null,
    val exitReasonName: String? = null,
    val noticeDate: LocalDate,
    val requestedLastWorkingDate: LocalDate,
    val approvedLastWorkingDate: LocalDate? = null,
    val remarks: String? = null,
    val status: ExitNoticeStatus,
    val approvedBy: UUID? = null,
    val approvedByName: String? = null,
    val approvedAt: OffsetDateTime? = null,
)

data class ExitNoticeListResponse(
    val items: List<ExitNoticeItem>,
    val totalCount: Int,
)

data class ExitNoticeCreateRequest(
    val exitTypeId: UUID,
    val exitReasonId: UUID? = null,
    val noticeDate: LocalDate? = null,
    val requestedLastWorkingDate: LocalDate,
    val remarks: String? = null,
)

data class ExitNoticeApproveRequest(
    val approvedLastWorkingDate: LocalDate,
    val remarks: String? = null,
)

data class ClearanceTaskItem(
    val id: UUID,
    val exitNoticeId: UUID,
    val employeeId: UUID,
    val department: ClearanceDepartment,
    val title: String,
    val assigneeEmployeeId: UUID? = null,
    val assigneeName: String? = null,
    val status: ClearanceTaskStatus,
    val clearedAt: OffsetDateTime? = null,
    val remarks: String? = null,
    val recoverableAmount: BigDecimal,
)

data class ClearanceDetailResponse(
    val exitNoticeId: UUID,
    val employeeId: UUID,
    val employeeName: String,
    val totalTasks: Int,
    val clearedTasks: Int,
    val pendingTasks: Int,
    val totalRecoverableAmount: BigDecimal,
    val tasks: List<ClearanceTaskItem> = emptyList(),
)

data class ClearanceTaskStatusUpdateRequest(
    val status: ClearanceTaskStatus,
    val remarks: String? = null,
    val recoverableAmount: BigDecimal? = null,
)

data class ExitInterviewItem(
    val id: UUID,
    val exitNoticeId: UUID,
    val employeeId: UUID,
    val employeeName: String,
    val interviewerEmployeeId: UUID? = null,
    val interviewerName: String? = null,
    val conductedAt: OffsetDateTime,
    val overallExperienceRating: Int,
    val managementRating: Int,
    val cultureRating: Int,
    val reasonDetails: String? = null,
    val suggestions: String? = null,
    val wouldRecommend: Boolean,
)

data class ExitInterviewSubmitRequest(
    val overallExperienceRating: Int,
    val managementRating: Int,
    val cultureRating: Int,
    val reasonDetails: String? = null,
    val suggestions: String? = null,
    val wouldRecommend: Boolean,
)
