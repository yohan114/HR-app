package com.hr.payroll

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Employee information required for payroll and statutory calculation.
 */
data class EmployeePayrollProfile(
    val id: UUID,
    val employeeCode: String,
    val displayName: String,
    val countryCode: String,
    val currency: String,
    val taxIdentificationNumber: String? = null,
    val socialSecurityNumber: String? = null,
    val bankName: String? = null,
    val bankCode: String? = null,
    val branchCode: String? = null,
    val bankAccountNumber: String? = null,
    val joinDate: LocalDate,
    val resignDate: LocalDate? = null,
)

/**
 * Additional fixed or variable earnings for an employee in a pay period.
 */
data class AllowanceInput(
    val code: String,
    val name: String,
    val amount: BigDecimal,
    val isTaxable: Boolean = true,
    val isStatutoryBase: Boolean = true,
)

/**
 * Non-statutory voluntary deductions (e.g. loans, club fees, health insurance top-up).
 */
data class DeductionInput(
    val code: String,
    val name: String,
    val amount: BigDecimal,
    val isPreTax: Boolean = false,
)

/**
 * Full input bundle for computing an individual employee's gross-to-net pay.
 */
data class GrossToNetInput(
    val employee: EmployeePayrollProfile,
    val payPeriodId: UUID,
    val periodStartDate: LocalDate,
    val periodEndDate: LocalDate,
    val basicSalary: BigDecimal,
    val allowances: List<AllowanceInput> = emptyList(),
    val deductions: List<DeductionInput> = emptyList(),
    val workingDaysInMonth: BigDecimal = BigDecimal("22.00"),
    val unpaidLeaveDays: BigDecimal = BigDecimal.ZERO,
)

/**
 * Line item breakdown for a single statutory scheme (EPF, ETF, SSS, PhilHealth, etc.).
 */
data class StatutoryDeductionLine(
    val code: String,
    val name: String,
    val employeeAmount: BigDecimal,
    val employerAmount: BigDecimal,
    val calculationTrace: String,
)

/**
 * Aggregated statutory result produced by a country-specific calculator.
 */
data class StatutoryCalculationResult(
    val totalEmployeeStatutory: BigDecimal,
    val totalEmployerStatutory: BigDecimal,
    val taxWithheld: BigDecimal,
    val taxableIncome: BigDecimal,
    val lines: List<StatutoryDeductionLine>,
    val taxCalculationTrace: String,
)

/**
 * Individual itemized line on the final payslip with audit calculation trace.
 */
data class PayItemResultLine(
    val category: LineCategory,
    val itemCode: String,
    val itemName: String,
    val amount: BigDecimal,
    val isStatutory: Boolean = false,
    val calculationTrace: String? = null,
)

/**
 * Completed gross-to-net pay calculation result.
 */
data class GrossToNetResult(
    val employeeId: UUID,
    val employeeCode: String,
    val employeeName: String,
    val currency: String,
    val basicSalary: BigDecimal,
    val lossOfPayDeduction: BigDecimal,
    val grossPay: BigDecimal,
    val totalStatutoryEmployee: BigDecimal,
    val totalStatutoryEmployer: BigDecimal,
    val taxableIncome: BigDecimal,
    val taxWithheld: BigDecimal,
    val totalVoluntaryDeductions: BigDecimal,
    val netPay: BigDecimal,
    val employerTotalCost: BigDecimal,
    val lines: List<PayItemResultLine>,
)

/**
 * Individual transaction instruction in a bank advice file.
 */
data class BankAdviceRecord(
    val employeeCode: String,
    val employeeName: String,
    val bankCode: String,
    val branchCode: String,
    val accountNumber: String,
    val amount: BigDecimal,
    val paymentReference: String,
)

/**
 * Generated electronic bank transfer payload with batch control verification.
 */
data class BankAdviceFile(
    val filename: String,
    val format: BankFileFormat,
    val content: ByteArray,
    val totalRecords: Int,
    val totalAmount: BigDecimal,
    val batchHash: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BankAdviceFile
        return filename == other.filename &&
            format == other.format &&
            content.contentEquals(other.content) &&
            totalRecords == other.totalRecords &&
            totalAmount == other.totalAmount &&
            batchHash == other.batchHash
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + totalRecords
        result = 31 * result + totalAmount.hashCode()
        result = 31 * result + batchHash.hashCode()
        return result
    }
}
