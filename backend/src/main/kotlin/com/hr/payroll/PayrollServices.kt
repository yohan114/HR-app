package com.hr.payroll

import java.time.LocalDate

/**
 * Executes gross-to-net salary and deduction calculations for employees.
 */
interface GrossToNetCalculationService {
    /**
     * Computes gross pay, statutory employee/employer portions, tax withholding,
     * voluntary deductions, and final net pay with itemized calculation traces.
     */
    fun calculateEmployee(input: GrossToNetInput): GrossToNetResult
}

/**
 * Generates electronic direct deposit / bank advice files for salary disbursement.
 */
interface BankAdviceService {
    /**
     * Formats approved payroll results into bank transfer instructions with batch controls.
     */
    fun generateBankAdvice(
        companyName: String,
        companyAccount: String,
        paymentDate: LocalDate,
        currency: String,
        records: List<BankAdviceRecord>,
        format: BankFileFormat = BankFileFormat.CSV_STANDARD,
    ): BankAdviceFile
}
