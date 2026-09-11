package com.hr.loan

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Interest calculation method supported by loan products.
 */
enum class InterestMethod {
    ZERO_INTEREST,
    REDUCING_BALANCE,
    FLAT_RATE,
}

/**
 * Lifecycle status of an employee loan application / active loan.
 */
enum class LoanStatus {
    SUBMITTED,
    APPROVED,
    ACTIVE,
    SETTLED,
    REJECTED,
    CANCELLED,
}

/**
 * Status of an individual repayment installment schedule item.
 */
enum class ScheduleStatus {
    PENDING,
    DEDUCTED,
    WAIVED,
}

// ---------------------------------------------------------------------------
// DTOs matching the OpenAPI spec contract
// ---------------------------------------------------------------------------

data class LoanTypeItemResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val interestMethod: InterestMethod,
    val annualInterestRate: BigDecimal,
    val minTenureMonths: Int,
    val maxTenureMonths: Int,
    val minPrincipal: BigDecimal,
    val maxPrincipal: BigDecimal,
    val salaryMultipleLimit: BigDecimal,
    val minServiceMonths: Int,
    val maxActiveLoans: Int,
    val description: String? = null,
)

data class LoanTypesResponse(
    val types: List<LoanTypeItemResponse>,
)

data class EmployeeLoanItemResponse(
    val id: UUID,
    val loanCode: String,
    val loanTypeId: UUID,
    val loanTypeCode: String,
    val loanTypeName: String,
    val principalAmount: BigDecimal,
    val interestMethod: InterestMethod,
    val annualInterestRate: BigDecimal,
    val tenureMonths: Int,
    val monthlyInstallment: BigDecimal,
    val totalInterest: BigDecimal,
    val totalRepayable: BigDecimal,
    val totalRepaid: BigDecimal,
    val remainingBalance: BigDecimal,
    val reason: String,
    val status: LoanStatus,
    val disbursedDate: LocalDate? = null,
    val settledDate: LocalDate? = null,
)

data class EmployeeLoansResponse(
    val totalOutstandingBalance: BigDecimal,
    val activeLoansCount: Int,
    val loans: List<EmployeeLoanItemResponse>,
    val nextRepaymentDate: LocalDate? = null,
    val nextRepaymentAmount: BigDecimal? = null,
)

data class LoanRepaymentScheduleItemResponse(
    val installmentNumber: Int,
    val dueDate: LocalDate,
    val principalAmount: BigDecimal,
    val interestAmount: BigDecimal,
    val totalInstallment: BigDecimal,
    val status: ScheduleStatus,
    val deductedDate: LocalDate? = null,
)

data class EmployeeLoanDetailResponse(
    val loan: EmployeeLoanItemResponse,
    val schedule: List<LoanRepaymentScheduleItemResponse>,
)

data class LoanEligibilityRequest(
    val loanTypeId: UUID,
    val principalAmount: BigDecimal,
    val tenureMonths: Int,
)

data class LoanEligibilityResponse(
    val eligible: Boolean,
    val maxAllowedPrincipal: BigDecimal,
    val projectedMonthlyInstallment: BigDecimal,
    val projectedTotalInterest: BigDecimal,
    val projectedTotalRepayable: BigDecimal,
    val reasons: List<String>,
)

data class LoanApplicationRequest(
    val loanTypeId: UUID,
    val principalAmount: BigDecimal,
    val tenureMonths: Int,
    val reason: String,
)

data class LoanSettlementRequest(
    val settlementNotes: String? = null,
)

data class LoanSettlementResponse(
    val loanId: UUID,
    val settledDate: LocalDate,
    val settlementAmountPaid: BigDecimal,
    val remainingBalance: BigDecimal,
    val status: String,
)

// ---------------------------------------------------------------------------
// Schedule calculation engine models
// ---------------------------------------------------------------------------

data class AmortizationScheduleLine(
    val installmentNumber: Int,
    val dueDate: LocalDate,
    val principalAmount: BigDecimal,
    val interestAmount: BigDecimal,
    val totalInstallment: BigDecimal,
    val remainingPrincipal: BigDecimal,
)

data class AmortizationCalculationResult(
    val monthlyInstallment: BigDecimal,
    val totalInterest: BigDecimal,
    val totalRepayable: BigDecimal,
    val schedule: List<AmortizationScheduleLine>,
)
