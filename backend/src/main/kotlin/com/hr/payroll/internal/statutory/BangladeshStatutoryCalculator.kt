package com.hr.payroll.internal.statutory

import com.hr.payroll.EmployeePayrollProfile
import com.hr.payroll.StatutoryCalculationResult
import com.hr.payroll.StatutoryDeductionLine
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Statutory calculation engine for Bangladesh (BD).
 *
 * Implements:
 * 1. Contributory Provident Fund (CPF):
 *    - Employee contribution: 10.00% of basic earnings.
 *    - Employer contribution: 10.00% matching contribution.
 * 2. National Board of Revenue (NBR) Personal Income Tax:
 *    - Annual tax-free threshold: 350,000 BDT (general individuals).
 *    - Progressive tax slabs:
 *      - First 350,000 BDT: 0.00%
 *      - Next 100,000 BDT: 5.00%
 *      - Next 300,000 BDT: 10.00%
 *      - Next 400,000 BDT: 15.00%
 *      - Next 500,000 BDT: 20.00%
 *      - Remainder above 1,650,000 BDT: 25.00%
 *    - Monthly withholding tax computed from annualized taxable income.
 */
@Component
class BangladeshStatutoryCalculator : StatutoryCalculator {

    override val countryCode: String = "BD"

    private val cpfRate = BigDecimal("0.10")

    private val annualTaxFreeThreshold = BigDecimal("350000.00")
    private val slab1Width = BigDecimal("100000.00") // 5%
    private val slab2Width = BigDecimal("300000.00") // 10%
    private val slab3Width = BigDecimal("400000.00") // 15%
    private val slab4Width = BigDecimal("500000.00") // 20%

    override fun calculate(
        employee: EmployeePayrollProfile,
        statutoryBaseEarnings: BigDecimal,
        grossTaxableEarnings: BigDecimal,
    ): StatutoryCalculationResult {
        // 1. Contributory Provident Fund (10% Employee, 10% Employer)
        val cpfEmployee = statutoryBaseEarnings.multiply(cpfRate).setScale(2, RoundingMode.HALF_UP)
        val cpfEmployer = statutoryBaseEarnings.multiply(cpfRate).setScale(2, RoundingMode.HALF_UP)
        val cpfLine = StatutoryDeductionLine(
            code = "BD_CPF",
            name = "Contributory Provident Fund",
            employeeAmount = cpfEmployee,
            employerAmount = cpfEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings × [Employee=10% ($cpfEmployee), Employer=10% ($cpfEmployer)]",
        )

        val totalEmployeeStatutory = cpfEmployee
        val totalEmployerStatutory = cpfEmployer

        // 2. Taxable income: Gross - Employee CPF
        val monthlyTaxable = grossTaxableEarnings.subtract(totalEmployeeStatutory).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        val (monthlyTax, taxTrace) = calculateNbrTax(monthlyTaxable)

        return StatutoryCalculationResult(
            totalEmployeeStatutory = totalEmployeeStatutory,
            totalEmployerStatutory = totalEmployerStatutory,
            taxWithheld = monthlyTax,
            taxableIncome = monthlyTaxable,
            lines = listOf(cpfLine),
            taxCalculationTrace = taxTrace,
        )
    }

    private fun calculateNbrTax(monthlyTaxable: BigDecimal): Pair<BigDecimal, String> {
        val annualTaxable = monthlyTaxable.multiply(BigDecimal("12")).setScale(2, RoundingMode.HALF_UP)

        if (annualTaxable <= annualTaxFreeThreshold) {
            return Pair(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                "Annual Taxable $annualTaxable <= Exemption Threshold $annualTaxFreeThreshold -> Tax = 0.00",
            )
        }

        var remaining = annualTaxable.subtract(annualTaxFreeThreshold)
        var annualTax = BigDecimal.ZERO
        val trace = mutableListOf("Annual Taxable: $annualTaxable (Exemption $annualTaxFreeThreshold applied)")

        // Slab 1: 100k @ 5%
        val s1 = remaining.min(slab1Width)
        if (s1 > BigDecimal.ZERO) {
            val tax1 = s1.multiply(BigDecimal("0.05"))
            annualTax = annualTax.add(tax1)
            trace.add("Slab 1: 5% × $s1 = $tax1")
            remaining = remaining.subtract(s1)
        }

        // Slab 2: 300k @ 10%
        if (remaining > BigDecimal.ZERO) {
            val s2 = remaining.min(slab2Width)
            val tax2 = s2.multiply(BigDecimal("0.10"))
            annualTax = annualTax.add(tax2)
            trace.add("Slab 2: 10% × $s2 = $tax2")
            remaining = remaining.subtract(s2)
        }

        // Slab 3: 400k @ 15%
        if (remaining > BigDecimal.ZERO) {
            val s3 = remaining.min(slab3Width)
            val tax3 = s3.multiply(BigDecimal("0.15"))
            annualTax = annualTax.add(tax3)
            trace.add("Slab 3: 15% × $s3 = $tax3")
            remaining = remaining.subtract(s3)
        }

        // Slab 4: 500k @ 20%
        if (remaining > BigDecimal.ZERO) {
            val s4 = remaining.min(slab4Width)
            val tax4 = s4.multiply(BigDecimal("0.20"))
            annualTax = annualTax.add(tax4)
            trace.add("Slab 4: 20% × $s4 = $tax4")
            remaining = remaining.subtract(s4)
        }

        // Slab 5: Remainder @ 25%
        if (remaining > BigDecimal.ZERO) {
            val tax5 = remaining.multiply(BigDecimal("0.25"))
            annualTax = annualTax.add(tax5)
            trace.add("Slab 5: 25% × $remaining = $tax5")
        }

        val monthlyTax = annualTax.divide(BigDecimal("12"), 2, RoundingMode.HALF_UP)
        return Pair(
            monthlyTax,
            trace.joinToString("; ") + " => Annual Tax = $annualTax => Monthly = $monthlyTax",
        )
    }
}
