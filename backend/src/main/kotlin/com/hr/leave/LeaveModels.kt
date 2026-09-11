package com.hr.leave

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Tenure tier bracket for service-based entitlement calculation.
 */
data class ServiceSlab(
    val minYears: Int = 0,
    val maxYears: Int = 999,
    val entitlementDays: BigDecimal = BigDecimal.ZERO,
)

/**
 * Fast aggregate balance projection per employee and leave type.
 */
data class LeaveBalanceDto(
    val employeeId: UUID,
    val leaveYearId: UUID,
    val leaveTypeId: UUID,
    val leaveTypeCode: String,
    val leaveTypeName: String,
    val openingBalance: BigDecimal,
    val accrued: BigDecimal,
    val taken: BigDecimal,
    val adjusted: BigDecimal,
    val carriedForward: BigDecimal,
    val encashed: BigDecimal,
    val expired: BigDecimal,
    val balance: BigDecimal,
    val lastAccruedAt: Instant?,
)

/**
 * Itemized statement entry from the immutable leave ledger.
 */
data class LedgerEntryDto(
    val id: UUID,
    val employeeId: UUID,
    val leaveYearId: UUID,
    val leaveTypeId: UUID,
    val entryType: LedgerEntryType,
    val days: BigDecimal,
    val referenceType: String,
    val referenceId: String,
    val effectiveDate: LocalDate,
    val balanceAfter: BigDecimal,
    val remarks: String?,
    val createdAt: Instant,
)

/**
 * Result of an accrual calculation for a specific employee and leave type.
 */
data class AccrualResult(
    val employeeId: UUID,
    val leaveTypeId: UUID,
    val daysAccrued: BigDecimal,
    val previousBalance: BigDecimal,
    val newBalance: BigDecimal,
    val skipped: Boolean = false,
    val reason: String? = null,
)

/**
 * Result of executing a year-end carry-forward rollover.
 */
data class YearRolloverResult(
    val employeeId: UUID,
    val leaveTypeId: UUID,
    val closingBalancePreviousYear: BigDecimal,
    val carriedForward: BigDecimal,
    val expired: BigDecimal,
)

/**
 * Payload for requesting absence/leave.
 */
data class LeaveApplicationRequest(
    val employeeId: UUID,
    val leaveTypeId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dayPortion: DayPortion = DayPortion.FULL_DAY,
    val reason: String? = null,
)

/**
 * Itemized schedule day for a leave application.
 */
data class LeaveApplicationDayDto(
    val id: UUID,
    val date: LocalDate,
    val dayPortion: DayPortion,
    val isWorkingDay: Boolean,
    val hours: BigDecimal,
)

/**
 * Detailed leave application representation.
 */
data class LeaveApplicationDto(
    val id: UUID,
    val employeeId: UUID,
    val leaveYearId: UUID,
    val leaveTypeId: UUID,
    val leaveTypeCode: String,
    val leaveTypeName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalDays: BigDecimal,
    val dayPortion: DayPortion,
    val status: ApplicationStatus,
    val reason: String?,
    val submittedAt: Instant,
    val actionedAt: Instant?,
    val actionedBy: UUID?,
    val actionReason: String?,
    val days: List<LeaveApplicationDayDto> = emptyList(),
)

/**
 * Outcome of evaluating leave eligibility rules before submission.
 */
data class LeaveEligibilityResult(
    val eligible: Boolean,
    val workingDaysRequested: BigDecimal,
    val currentBalance: BigDecimal,
    val pendingDays: BigDecimal,
    val availableBalance: BigDecimal,
    val reasons: List<String> = emptyList(),
)

/**
 * Public holiday calendar entry.
 */
data class PublicHolidayDto(
    val id: UUID,
    val date: LocalDate,
    val name: String,
    val countryCode: String,
    val isHalfDay: Boolean,
)

