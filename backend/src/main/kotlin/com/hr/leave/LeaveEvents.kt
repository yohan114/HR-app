package com.hr.leave

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class LeaveAppliedEvent(
    val tenantId: UUID,
    val applicationId: UUID,
    val employeeId: UUID,
    val leaveTypeName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalDays: BigDecimal,
)

data class LeaveApprovedEvent(
    val tenantId: UUID,
    val applicationId: UUID,
    val employeeId: UUID,
    val approverId: UUID,
    val leaveTypeName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalDays: BigDecimal,
    val remarks: String? = null,
)

data class LeaveRejectedEvent(
    val tenantId: UUID,
    val applicationId: UUID,
    val employeeId: UUID,
    val approverId: UUID,
    val leaveTypeName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalDays: BigDecimal,
    val reason: String,
)
