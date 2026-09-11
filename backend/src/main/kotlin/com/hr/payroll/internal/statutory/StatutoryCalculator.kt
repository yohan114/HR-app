package com.hr.payroll.internal.statutory

import com.hr.payroll.EmployeePayrollProfile
import com.hr.payroll.StatutoryCalculationResult
import java.math.BigDecimal

/**
 * Regional plug-in contract for calculating country-specific statutory deductions,
 * employer contributions, and tax withholding.
 */
interface StatutoryCalculator {
    /** Two-letter ISO country code (e.g. "LK", "PH"). */
    val countryCode: String

    /**
     * Executes statutory contributions and income tax calculation for an employee.
     *
     * @param employee The employee profile containing identification numbers.
     * @param statutoryBaseEarnings Earnings subject to social security / provident fund contributions.
     * @param grossTaxableEarnings Total gross income subject to personal income tax.
     */
    fun calculate(
        employee: EmployeePayrollProfile,
        statutoryBaseEarnings: BigDecimal,
        grossTaxableEarnings: BigDecimal,
    ): StatutoryCalculationResult
}
