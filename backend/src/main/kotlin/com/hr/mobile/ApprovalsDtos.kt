package com.hr.mobile

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PendingApprovalsResponseDto(
    val items: List<ApprovalItemDto>,
    val totalCount: Int,
)

data class ApprovalItemDto(
    val id: String,
    val type: String, // LEAVE_APPLICATION, ATTENDANCE_REGULARISATION
    val requesterId: UUID,
    val requesterName: String,
    val requesterDesignation: String? = null,
    val requesterPhotoKey: String? = null,
    val departmentName: String? = null,
    val submittedAt: Instant,
    val title: String,
    val summary: String,
    val status: String,
    val urgency: String, // NORMAL, URGENT, OVERDUE
    val leaveDetails: LeaveApprovalDetailsDto? = null,
    val attendanceDetails: AttendanceApprovalDetailsDto? = null,
)

data class LeaveApprovalDetailsDto(
    val leaveTypeName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val workingDays: Double,
    val reason: String,
    val employeeBalanceDays: Double,
    val teamCoverageWarning: String? = null,
)

data class AttendanceApprovalDetailsDto(
    val workDate: LocalDate,
    val requestedPunchType: String,
    val requestedTime: String,
    val shiftName: String? = null,
    val reason: String,
)

data class ApprovalDecisionRequestDto(
    val decision: String, // APPROVE, REJECT
    val remarks: String? = null,
)

data class ApprovalDecisionResponseDto(
    val id: String,
    val status: String,
    val decidedAt: Instant,
    val message: String,
)
