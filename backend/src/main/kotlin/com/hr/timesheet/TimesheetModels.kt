package com.hr.timesheet

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

enum class TimesheetStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED
}

enum class ReconciliationStatus {
    MATCHED,
    UNDER_LOGGED,
    OVER_LOGGED,
    NO_ATTENDANCE
}

data class TimesheetClientItem(
    val id: UUID,
    val clientCode: String,
    val clientName: String,
    val status: String,
    val billingCurrency: String? = "USD",
    val contactEmail: String? = null
)

data class TimesheetClientListResponse(
    val clients: List<TimesheetClientItem>
)

data class TimesheetProjectItem(
    val id: UUID,
    val clientId: UUID,
    val clientName: String?,
    val projectCode: String,
    val projectName: String,
    val description: String? = null,
    val billable: Boolean,
    val status: String,
    val budgetHours: Double? = null
)

data class TimesheetProjectListResponse(
    val projects: List<TimesheetProjectItem>
)

data class TimesheetProjectCreateRequest(
    val clientId: UUID,
    val projectCode: String,
    val projectName: String,
    val description: String? = null,
    val billable: Boolean = true,
    val budgetHours: Double? = null
)

data class TimesheetActivityItem(
    val id: UUID,
    val projectId: UUID,
    val activityCode: String,
    val activityName: String,
    val billable: Boolean,
    val status: String
)

data class TimesheetActivityListResponse(
    val activities: List<TimesheetActivityItem>
)

data class TimesheetListItem(
    val id: UUID,
    val employeeId: UUID,
    val employeeName: String,
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate,
    val status: String,
    val totalHours: Double,
    val billableHours: Double,
    val nonBillableHours: Double,
    val submittedAt: OffsetDateTime? = null,
    val approvedAt: OffsetDateTime? = null
)

data class TimesheetListResponse(
    val timesheets: List<TimesheetListItem>
)

data class TimesheetEntryItem(
    val id: UUID,
    val projectId: UUID,
    val projectCode: String,
    val projectName: String,
    val activityId: UUID,
    val activityCode: String,
    val activityName: String,
    val entryDate: LocalDate,
    val hours: Double,
    val billable: Boolean,
    val notes: String? = null
)

data class TimesheetDetailResponse(
    val id: UUID,
    val employeeId: UUID,
    val employeeName: String,
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate,
    val status: String,
    val totalHours: Double,
    val billableHours: Double,
    val nonBillableHours: Double,
    val submittedAt: OffsetDateTime? = null,
    val approvedAt: OffsetDateTime? = null,
    val approverId: UUID? = null,
    val approverName: String? = null,
    val rejectionReason: String? = null,
    val comments: String? = null,
    val entries: List<TimesheetEntryItem> = emptyList()
)

data class TimesheetEntryInput(
    val projectId: UUID,
    val activityId: UUID,
    val entryDate: LocalDate,
    val hours: Double,
    val billable: Boolean = true,
    val notes: String? = null
)

data class TimesheetSaveRequest(
    val id: UUID? = null,
    val employeeId: UUID? = null,
    val weekStartDate: LocalDate,
    val comments: String? = null,
    val entries: List<TimesheetEntryInput>
)

data class TimesheetApproveRequest(
    val comments: String? = null
)

data class TimesheetRejectRequest(
    val reason: String
)

data class DailyReconciliationItem(
    val date: LocalDate,
    val loggedHours: Double,
    val attendanceHours: Double,
    val varianceHours: Double,
    val status: String
)

data class TimesheetReconciliationResponse(
    val employeeId: UUID,
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate,
    val totalLoggedHours: Double,
    val totalAttendanceHours: Double,
    val totalVarianceHours: Double,
    val dailyBreakdown: List<DailyReconciliationItem>
)

data class TimesheetCopyPreviousRequest(
    val employeeId: UUID? = null,
    val targetWeekStartDate: LocalDate
)
