package com.hr.leave

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Public service interface for the Absence & Leave Accrual Engine.
 *
 * Implements the 5 accrual methods, effective-dated formulas, mid-period join/exit
 * pro-rating, carry-forward rollover, and idempotent period execution.
 */
interface LeaveAccrualService {
    /**
     * Accrues annual upfront entitlement for an employee. If [prorateOnJoin] is enabled
     * and the employee joined after the leave year started, credits pro-rated days.
     */
    fun accrueAnnualUpfront(
        employeeId: UUID,
        leaveYearId: UUID,
        ruleId: UUID,
        asOfDate: LocalDate = LocalDate.now(),
    ): AccrualResult

    /**
     * Accrues monthly leave entitlement for an employee for [yearMonth].
     * Pro-rates appropriately if the employee joined or exited mid-month.
     */
    fun accrueMonthly(
        employeeId: UUID,
        leaveYearId: UUID,
        ruleId: UUID,
        yearMonth: YearMonth,
    ): AccrualResult

    /**
     * Evaluates employee service tenure against tenure slabs and credits the corresponding delta.
     */
    fun accrueServiceSlab(
        employeeId: UUID,
        leaveYearId: UUID,
        ruleId: UUID,
        asOfDate: LocalDate = LocalDate.now(),
    ): AccrualResult

    /**
     * Credits leave earned based on confirmed worked attendance days.
     */
    fun accruePerWorkedDays(
        employeeId: UUID,
        leaveYearId: UUID,
        ruleId: UUID,
        yearMonth: YearMonth,
        workedDays: Int,
    ): AccrualResult

    /**
     * Batch execution engine: sweeps across all active employees and active entitlement rules
     * in the given [tenantId] and [leaveYearId], executing accruals idempotently.
     */
    fun runAccrualBatch(
        tenantId: UUID,
        leaveYearId: UUID,
        asOfDate: LocalDate = LocalDate.now(),
    ): List<AccrualResult>

    /**
     * Executes year-end rollover: carries forward remaining balance up to rule's [carry_forward_max],
     * records expired balance, and writes opening balances for the next leave year.
     */
    fun rolloverYearEnd(
        tenantId: UUID,
        fromYearId: UUID,
        toYearId: UUID,
    ): List<YearRolloverResult>
}

/**
 * Public service interface for leave balances and ledger explainability.
 */
interface LeaveBalanceService {
    /**
     * Returns the aggregate balance snapshot for an employee, year, and leave type.
     */
    fun getBalance(
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
    ): LeaveBalanceDto?

    /**
     * Returns all active leave type balances for an employee in [leaveYearId].
     */
    fun getAllBalancesForEmployee(
        employeeId: UUID,
        leaveYearId: UUID,
    ): List<LeaveBalanceDto>

    /**
     * Retrieves the itemized ledger history (statement) for explainability.
     */
    fun getLedgerStatement(
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
    ): List<LedgerEntryDto>

    /**
     * Mathematically reconstructs the balance by summing all append-only ledger entries
     * up to [asOfDate]. The result MUST match [employee_leave_entitlement.balance].
     */
    fun reconstructBalanceFromLedger(
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
        asOfDate: LocalDate = LocalDate.now(),
    ): BigDecimal
}

/**
 * Service orchestrating employee leave requests, working-day calculations,
 * multi-level eligibility checks, approval debiting, and cancellation reversals.
 */
interface LeaveApplicationService {
    /**
     * Pre-validates eligibility for a requested leave span, returning failure reasons if invalid.
     */
    fun checkEligibility(request: LeaveApplicationRequest): LeaveEligibilityResult

    /**
     * Submits a leave application, expands working days, and persists in SUBMITTED state.
     */
    fun submitApplication(request: LeaveApplicationRequest): LeaveApplicationDto

    /**
     * Approves an application, changes state to APPROVED, debits the immutable leave ledger,
     * and updates the aggregate balance projection.
     */
    fun approveApplication(
        applicationId: UUID,
        approverId: UUID,
        remarks: String? = null,
    ): LeaveApplicationDto

    /**
     * Rejects an application, recording rejection reason.
     */
    fun rejectApplication(
        applicationId: UUID,
        approverId: UUID,
        reason: String,
    ): LeaveApplicationDto

    /**
     * Cancels an already approved application, reverses the debit in the leave ledger,
     * and restores the employee's available balance.
     */
    fun cancelApplication(
        applicationId: UUID,
        cancelledBy: UUID,
        reason: String,
    ): LeaveApplicationDto

    /**
     * Withdraws a pending SUBMITTED application.
     */
    fun withdrawApplication(
        applicationId: UUID,
        employeeId: UUID,
    ): LeaveApplicationDto

    /**
     * Retrieves application details by ID.
     */
    fun getApplication(applicationId: UUID): LeaveApplicationDto

    /**
     * Retrieves all applications submitted by an employee.
     */
    fun getEmployeeApplications(employeeId: UUID): List<LeaveApplicationDto>
}

