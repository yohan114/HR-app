package com.hr.payroll.internal.statutory

import com.hr.payroll.EmployeePayrollProfile
import com.hr.payroll.StatutoryCalculationResult
import com.hr.payroll.StatutoryDeductionLine
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Statutory calculation engine for the United Arab Emirates (AE).
 *
 * Implements:
 * 1. Personal Income Tax:
 *    - 0.00% across all income (UAE federal personal income tax exemption).
 * 2. End of Service Gratuity (EOSG) Statutory Accrual (UAE Labour Law Federal Decree-Law No. 33):
 *    - Accrual liability calculation for expatriates based on tenure from joinDate:
 *      - First 5 years of service: 21 days basic salary per year (approx 5.83% monthly provision).
 *      - Over 5 years of service: 30 days basic salary per year (approx 8.33% monthly provision).
 *    - Captured as an employer liability provision line.
 * 3. GPSSA (General Pension and Social Security Authority):
 *    - Applicable for UAE National employees (identified by Emirates ID format or social security number):
 *      - Employee contribution: 5.00% of contributory base salary.
 *      - Employer contribution: 12.50% of contributory base salary.
 */
@Component
class UaeStatutoryCalculator : StatutoryCalculator {

    override val countryCode: String = "AE"

    // EOSG monthly provision factors
    private val eosgFirst5YearsFactor = BigDecimal("21").divide(BigDecimal("360"), 6, RoundingMode.HALF_UP) // 21/30 / 12 = 0.058333
    private val eosgAfter5YearsFactor = BigDecimal("30").divide(BigDecimal("360"), 6, RoundingMode.HALF_UP) // 30/30 / 12 = 0.083333

    // GPSSA rates for UAE Nationals
    private val gpssaEmployeeRate = BigDecimal("0.05")
    private val gpssaEmployerRate = BigDecimal("0.125")

    override fun calculate(
        employee: EmployeePayrollProfile,
        statutoryBaseEarnings: BigDecimal,
        grossTaxableEarnings: BigDecimal,
    ): StatutoryCalculationResult {
        val lines = mutableListOf<StatutoryDeductionLine>()
        var totalEmployeeStatutory = BigDecimal.ZERO
        var totalEmployerStatutory = BigDecimal.ZERO

        // Check if employee is a UAE national enrolled in GPSSA
        val isUaeNational = isUaeNational(employee)

        if (isUaeNational) {
            val gpssaEmployee = statutoryBaseEarnings.multiply(gpssaEmployeeRate).setScale(2, RoundingMode.HALF_UP)
            val gpssaEmployer = statutoryBaseEarnings.multiply(gpssaEmployerRate).setScale(2, RoundingMode.HALF_UP)

            lines.add(
                StatutoryDeductionLine(
                    code = "GPSSA",
                    name = "General Pension & Social Security Authority",
                    employeeAmount = gpssaEmployee,
                    employerAmount = gpssaEmployer,
                    calculationTrace = "UAE National Contributory Base=$statutoryBaseEarnings × [EE=5.0% ($gpssaEmployee), ER=12.5% ($gpssaEmployer)]",
                )
            )
            totalEmployeeStatutory = totalEmployeeStatutory.add(gpssaEmployee)
            totalEmployerStatutory = totalEmployerStatutory.add(gpssaEmployer)
        } else {
            // Expatriates: Statutory End of Service Gratuity (EOSG) accrual
            val serviceYears = calculateServiceYears(employee.joinDate)
            val daysPerYear = if (serviceYears < 5) 21 else 30
            val eosgAccrual = statutoryBaseEarnings
                .multiply(BigDecimal(daysPerYear))
                .divide(BigDecimal("360"), 2, RoundingMode.HALF_UP)

            lines.add(
                StatutoryDeductionLine(
                    code = "UAE_EOSG_ACCRUAL",
                    name = "End of Service Gratuity Provision",
                    employeeAmount = BigDecimal.ZERO,
                    employerAmount = eosgAccrual,
                    calculationTrace = "Tenure=${serviceYears}y -> Accrual Rate: $daysPerYear days/yr × Base $statutoryBaseEarnings / 360 = $eosgAccrual",
                )
            )
            totalEmployerStatutory = totalEmployerStatutory.add(eosgAccrual)
        }

        return StatutoryCalculationResult(
            totalEmployeeStatutory = totalEmployeeStatutory,
            totalEmployerStatutory = totalEmployerStatutory,
            taxWithheld = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            taxableIncome = grossTaxableEarnings.setScale(2, RoundingMode.HALF_UP),
            lines = lines,
            taxCalculationTrace = "UAE Federal Income Tax: 0.00% exemption on employment income",
        )
    }

    private fun isUaeNational(employee: EmployeePayrollProfile): Boolean {
        val ssn = employee.socialSecurityNumber?.trim() ?: return false
        // UAE national identification standard: Emirates ID starts with 784 or explicit national tag
        return ssn.startsWith("784-") || ssn.contains("NATIONAL", ignoreCase = true)
    }

    private fun calculateServiceYears(joinDate: LocalDate?): Long {
        if (joinDate == null) return 0
        val now = LocalDate.now()
        val years = ChronoUnit.YEARS.between(joinDate, now)
        return if (years < 0) 0 else years
    }
}
