package com.hr.payroll.internal.statutory

import com.hr.payroll.EmployeePayrollProfile
import com.hr.payroll.StatutoryCalculationResult
import com.hr.payroll.StatutoryDeductionLine
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Statutory calculation engine for Sri Lanka (LK).
 *
 * Implements:
 * 1. Employees' Provident Fund (EPF):
 *    - Employee contribution: 8.00% of EPF-qualifying base earnings.
 *    - Employer contribution: 12.00% of EPF-qualifying base earnings.
 * 2. Employees' Trust Fund (ETF):
 *    - Employer contribution: 3.00% of ETF-qualifying base earnings.
 *    - Employee contribution: 0.00%.
 * 3. Advance Personal Income Tax (APIT):
 *    - Progressive monthly tax brackets with 100,000.00 LKR monthly tax-free threshold.
 *    - Progressive brackets: 6%, 12%, 18%, 24%, 30%, 36% on successive 41,667.00 LKR slabs.
 */
@Component
class SriLankaStatutoryCalculator : StatutoryCalculator {

    override val countryCode: String = "LK"

    private val epfEmployeeRate = BigDecimal("0.08")
    private val epfEmployerRate = BigDecimal("0.12")
    private val etfEmployerRate = BigDecimal("0.03")

    private val monthlyTaxFreeAllowance = BigDecimal("100000.00")
    private val bracketWidth = BigDecimal("41666.67")

    private val taxTiers = listOf(
        BigDecimal("0.06"),
        BigDecimal("0.12"),
        BigDecimal("0.18"),
        BigDecimal("0.24"),
        BigDecimal("0.30"),
    )
    private val topTierRate = BigDecimal("0.36")

    override fun calculate(
        employee: EmployeePayrollProfile,
        statutoryBaseEarnings: BigDecimal,
        grossTaxableEarnings: BigDecimal,
    ): StatutoryCalculationResult {
        // 1. EPF (Employee 8%, Employer 12%)
        val epfEmployee = statutoryBaseEarnings
            .multiply(epfEmployeeRate)
            .setScale(2, RoundingMode.HALF_UP)

        val epfEmployer = statutoryBaseEarnings
            .multiply(epfEmployerRate)
            .setScale(2, RoundingMode.HALF_UP)

        val epfLine = StatutoryDeductionLine(
            code = "EPF",
            name = "Employees' Provident Fund",
            employeeAmount = epfEmployee,
            employerAmount = epfEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings × [Employee=8% ($epfEmployee), Employer=12% ($epfEmployer)]",
        )

        // 2. ETF (Employer 3%)
        val etfEmployer = statutoryBaseEarnings
            .multiply(etfEmployerRate)
            .setScale(2, RoundingMode.HALF_UP)

        val etfLine = StatutoryDeductionLine(
            code = "ETF",
            name = "Employees' Trust Fund",
            employeeAmount = BigDecimal.ZERO,
            employerAmount = etfEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings × Employer=3% ($etfEmployer)",
        )

        // 3. APIT (Advance Personal Income Tax)
        val taxableIncome = grossTaxableEarnings.setScale(2, RoundingMode.HALF_UP)
        val (apitAmount, apitTrace) = calculateApit(taxableIncome)

        val totalEmployeeStatutory = epfEmployee
        val totalEmployerStatutory = epfEmployer.add(etfEmployer)

        return StatutoryCalculationResult(
            totalEmployeeStatutory = totalEmployeeStatutory,
            totalEmployerStatutory = totalEmployerStatutory,
            taxWithheld = apitAmount,
            taxableIncome = taxableIncome,
            lines = listOf(epfLine, etfLine),
            taxCalculationTrace = apitTrace,
        )
    }

    private fun calculateApit(taxableIncome: BigDecimal): Pair<BigDecimal, String> {
        if (taxableIncome <= monthlyTaxFreeAllowance) {
            return Pair(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                "Taxable Income $taxableIncome <= Monthly Relief $monthlyTaxFreeAllowance -> APIT = 0.00",
            )
        }

        var remaining = taxableIncome.subtract(monthlyTaxFreeAllowance)
        var totalTax = BigDecimal.ZERO
        val traceParts = mutableListOf("Relief $monthlyTaxFreeAllowance applied")

        for ((index, rate) in taxTiers.withIndex()) {
            if (remaining <= BigDecimal.ZERO) break
            val taxableInTier = remaining.min(bracketWidth)
            val taxForTier = taxableInTier.multiply(rate).setScale(2, RoundingMode.HALF_UP)
            totalTax = totalTax.add(taxForTier)
            traceParts.add("Tier ${index + 1} ($rate × $taxableInTier = $taxForTier)")
            remaining = remaining.subtract(taxableInTier)
        }

        // Top tier for excess above 308,333.35 taxable
        if (remaining > BigDecimal.ZERO) {
            val topTax = remaining.multiply(topTierRate).setScale(2, RoundingMode.HALF_UP)
            totalTax = totalTax.add(topTax)
            traceParts.add("Top Tier ($topTierRate × $remaining = $topTax)")
        }

        val finalTax = totalTax.setScale(2, RoundingMode.HALF_UP)
        return Pair(finalTax, traceParts.joinToString("; ") + " => Total APIT = $finalTax")
    }
}
