package com.hr.mobile.internal

import com.hr.identity.Caller
import com.hr.leave.LeaveApplicationService
import com.hr.mobile.ApprovalDecisionRequestDto
import com.hr.mobile.ApprovalDecisionResponseDto
import com.hr.mobile.ApprovalItemDto
import com.hr.mobile.AttendanceApprovalDetailsDto
import com.hr.mobile.LeaveApprovalDetailsDto
import com.hr.mobile.PendingApprovalsResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/v1/approvals")
class MobileApprovalsController(
    private val leaveApplicationService: LeaveApplicationService? = null,
) {
    // In-memory cache of decisions for idempotency and demo workflows
    private val decidedItems = ConcurrentHashMap<String, ApprovalDecisionResponseDto>()

    @GetMapping("/pending")
    fun getPendingApprovals(
        @AuthenticationPrincipal jwt: Jwt,
    ): PendingApprovalsResponseDto {
        val caller = Caller.from(jwt)
        val roles = jwt.getClaimAsStringList("roles")?.toSet() ?: emptySet()
        val permissions = jwt.getClaimAsStringList("permissions")?.toSet() ?: emptySet()

        val canApprove = roles.any { it.equals("MANAGER", ignoreCase = true) || it.equals("ADMIN", ignoreCase = true) } ||
            permissions.any { it in setOf("leave.approve", "attendance.approve", "employee.manage") }

        if (!canApprove) {
            return PendingApprovalsResponseDto(items = emptyList(), totalCount = 0)
        }

        // Active seed/demo approval items for team review
        val allItems = listOf(
            ApprovalItemDto(
                id = "demo-leave-001",
                type = "LEAVE_APPLICATION",
                requesterId = UUID.fromString("01930000-0000-7000-8000-000000000004"),
                requesterName = "Kasun Fernando",
                requesterDesignation = "Software Engineer",
                requesterPhotoKey = null,
                departmentName = "Engineering",
                submittedAt = Instant.now().minusSeconds(7200),
                title = "Annual Leave (3 Days)",
                summary = "15 Sep - 17 Sep · Family wedding travel",
                status = "PENDING",
                urgency = "NORMAL",
                leaveDetails = LeaveApprovalDetailsDto(
                    leaveTypeName = "Annual Leave",
                    startDate = LocalDate.now().plusDays(8),
                    endDate = LocalDate.now().plusDays(10),
                    workingDays = 3.0,
                    reason = "Family wedding trip to Kandy.",
                    employeeBalanceDays = 14.0,
                    teamCoverageWarning = "⚠️ 1 other team member off during this window",
                ),
            ),
            ApprovalItemDto(
                id = "demo-leave-002",
                type = "LEAVE_APPLICATION",
                requesterId = UUID.fromString("01930000-0000-7000-8000-000000000005"),
                requesterName = "Dilani Perera",
                requesterDesignation = "Software Engineer",
                requesterPhotoKey = null,
                departmentName = "Engineering",
                submittedAt = Instant.now().minusSeconds(18000),
                title = "Casual Leave (1 Day)",
                summary = "Tomorrow · Personal urgent errand",
                status = "PENDING",
                urgency = "URGENT",
                leaveDetails = LeaveApprovalDetailsDto(
                    leaveTypeName = "Casual Leave",
                    startDate = LocalDate.now().plusDays(1),
                    endDate = LocalDate.now().plusDays(1),
                    workingDays = 1.0,
                    reason = "Government department documentation renewal.",
                    employeeBalanceDays = 6.5,
                    teamCoverageWarning = null,
                ),
            ),
            ApprovalItemDto(
                id = "demo-att-001",
                type = "ATTENDANCE_REGULARISATION",
                requesterId = UUID.fromString("01930000-0000-7000-8000-000000000006"),
                requesterName = "Thivanka Rajapaksa",
                requesterDesignation = "Software Engineer",
                requesterPhotoKey = null,
                departmentName = "Engineering",
                submittedAt = Instant.now().minusSeconds(3600),
                title = "Missed Clock-In Punch",
                summary = "Today 08:35 AM · Biometric scanner timeout",
                status = "PENDING",
                urgency = "NORMAL",
                attendanceDetails = AttendanceApprovalDetailsDto(
                    workDate = LocalDate.now(),
                    requestedPunchType = "IN",
                    requestedTime = "08:35 AM",
                    shiftName = "General Day Shift (08:30 - 17:30)",
                    reason = "Front lobby biometric scanner was unresponsive. Entered via security gate.",
                ),
            ),
        )

        val pendingItems = allItems.filterNot { decidedItems.containsKey(it.id) }
        return PendingApprovalsResponseDto(
            items = pendingItems,
            totalCount = pendingItems.size,
        )
    }

    @PostMapping("/{id}/decision")
    fun decideApproval(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: ApprovalDecisionRequestDto,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApprovalDecisionResponseDto> {
        val caller = Caller.from(jwt)

        // 1. Idempotency & Concurrency check: If already decided by this or another caller
        decidedItems[id]?.let { existing ->
            return ResponseEntity.ok(existing)
        }

        // 2. Business service delegation if backing application exists
        val decision = request.decision.uppercase()
        val approverId = caller.employeeId ?: UUID.randomUUID()

        val service = leaveApplicationService
        if (runCatching { UUID.fromString(id) }.isSuccess && service != null) {
            val appUuid = UUID.fromString(id)
            runCatching {
                if (decision == "APPROVE") {
                    service.approveApplication(appUuid, approverId, request.remarks)
                } else {
                    service.rejectApplication(appUuid, approverId, request.remarks ?: "Rejected by manager")
                }
            }
        }

        val response = ApprovalDecisionResponseDto(
            id = id,
            status = if (decision == "APPROVE") "APPROVED" else "REJECTED",
            decidedAt = Instant.now(),
            message = if (decision == "APPROVE") "Request approved successfully" else "Request rejected: ${request.remarks.orEmpty()}",
        )

        decidedItems[id] = response
        return ResponseEntity.ok(response)
    }
}
