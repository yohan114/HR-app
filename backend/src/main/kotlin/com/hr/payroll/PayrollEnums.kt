package com.hr.payroll

/**
 * Pay schedule frequency for a pay group.
 */
enum class PayFrequency {
    MONTHLY,
    SEMI_MONTHLY,
    BI_WEEKLY,
    WEEKLY,
}

/**
 * Lifecycle state of an individual pay period.
 */
enum class PayPeriodStatus {
    OPEN,
    PROCESSING,
    APPROVED,
    CLOSED,
}

/**
 * Lifecycle state machine for a payroll run calculation batch.
 */
enum class PayrollRunStatus {
    DRAFT,
    CALCULATED,
    APPROVED,
    COMMITTED,
}

/**
 * Line item category on a payslip.
 */
enum class LineCategory {
    EARNING,
    STATUTORY_DEDUCTION,
    EMPLOYER_CONTRIBUTION,
    VOLUNTARY_DEDUCTION,
    TAX,
}

/**
 * Export format for electronic bank advice files.
 */
enum class BankFileFormat {
    CSV_STANDARD,
    ACH_NACHA,
    SLIPS_STANDARD,
}
