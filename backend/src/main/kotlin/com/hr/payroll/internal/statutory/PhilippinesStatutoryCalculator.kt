package com.hr.payroll.internal.statutory

import com.hr.payroll.EmployeePayrollProfile
import com.hr.payroll.StatutoryCalculationResult
import com.hr.payroll.StatutoryDeductionLine
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Statutory calculation engine for the Philippines (PH).
 *
 * Implements:
 * 1. Social Security System (SSS):
 *    - Monthly Salary Credit (MSC) clamped between 4,000.00 and 30,000.00 PHP.
 *    - Employee contribution: 4.50% of MSC.
 *    - Employer contribution: 9.50% of MSC.
 * 2. Philippine Health Insurance Corporation (PhilHealth):
 *    - Total rate: 5.00% split equally (2.50% Employee, 2.50% Employer).
 *    - Minimum salary floor: 10,000.00 PHP (min 250.00 PHP each).
 *    - Maximum salary ceiling: 100,000.00 PHP (max 2,500.00 PHP each).
 * 3. Home Development Mutual Fund (Pag-IBIG / HDMF):
 *    - Employee contribution: 2.00% of basic pay, capped at 200.00 PHP.
 *    - Employer contribution: 2.00% match, capped at 200.00 PHP.
 * 4. Tax Reform for Acceleration and Inclusion (TRAIN) Withholding Tax:
 *    - Mandatory statutory contributions are pre-tax deductions.
 *    - BIR progressive monthly income tax schedule.
 */
@Component
class PhilippinesStatutoryCalculator : StatutoryCalculator {

    override val countryCode: String = "PH"

    private val sssFloorMsc = BigDecimal("4000.00")
    private val sssCeilingMsc = BigDecimal("30000.00")
    private val sssEmployeeRate = BigDecimal("0.045")
    private val sssEmployerRate = BigDecimal("0.095")

    private val philHealthFloor = BigDecimal("10000.00")
    private val philHealthCeiling = BigDecimal("100000.00")
    private val philHealthSplitRate = BigDecimal("0.025")

    private val pagIbigRate = BigDecimal("0.02")
    private val pagIbigCap = BigDecimal("200.00")

    override fun calculate(
        employee: EmployeePayrollProfile,
        statutoryBaseEarnings: BigDecimal,
        grossTaxableEarnings: BigDecimal,
    ): StatutoryCalculationResult {
        // 1. SSS
        val msc = statutoryBaseEarnings.max(sssFloorMsc).min(sssCeilingMsc)
        val sssEmployee = msc.multiply(sssEmployeeRate).setScale(2, RoundingMode.HALF_UP)
        val sssEmployer = msc.multiply(sssEmployerRate).setScale(2, RoundingMode.HALF_UP)
        val sssLine = StatutoryDeductionLine(
            code = "SSS",
            name = "Social Security System",
            employeeAmount = sssEmployee,
            employerAmount = sssEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings -> MSC=$msc × [EE=4.5% ($sssEmployee), ER=9.5% ($sssEmployer)]",
        )

        // 2. PhilHealth (2.5% EE, 2.5% ER, clamped 10k-100k)
        val phBase = statutoryBaseEarnings.max(philHealthFloor).min(philHealthCeiling)
        val phEmployee = phBase.multiply(philHealthSplitRate).setScale(2, RoundingMode.HALF_UP)
        val phEmployer = phBase.multiply(philHealthSplitRate).setScale(2, RoundingMode.HALF_UP)
        val philHealthLine = StatutoryDeductionLine(
            code = "PHILHEALTH",
            name = "Philippine Health Insurance",
            employeeAmount = phEmployee,
            employerAmount = phEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings -> Clamped=$phBase × 2.5% = $phEmployee each",
        )

        // 3. Pag-IBIG (2% capped at 200 PHP)
        val pagIbigRaw = statutoryBaseEarnings.multiply(pagIbigRate).setScale(2, RoundingMode.HALF_UP)
        val pagIbigEmployee = pagIbigRaw.min(pagIbigCap)
        val pagIbigEmployer = pagIbigRaw.min(pagIbigCap)
        val pagIbigLine = StatutoryDeductionLine(
            code = "PAGIBIG",
            name = "Home Development Mutual Fund",
            employeeAmount = pagIbigEmployee,
            employerAmount = pagIbigEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings × 2% = $pagIbigRaw (capped at $pagIbigCap)",
        )

        val totalEmployeeStatutory = sssEmployee.add(phEmployee).add(pagIbigEmployee)
        val totalEmployerStatutory = sssEmployer.add(phEmployer).add(pagIbigEmployer)

        // 4. TRAIN Tax Withholding: Taxable Income = Gross Taxable - Employee Mandatory Contributions
        val taxableIncome = grossTaxableEarnings
            .subtract(totalEmployeeStatutory)
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP)

        val (trainTax, trainTrace) = calculateTrainTax(taxableIncome)

        return StatutoryCalculationResult(
            totalEmployeeStatutory = totalEmployeeStatutory,
            totalEmployerStatutory = totalEmployerStatutory,
            taxWithheld = trainTax,
            taxableIncome = taxableIncome,
            lines = listOf(sssLine, philHealthLine, pagIbigLine),
            taxCalculationTrace = trainTrace,
        )
    }

    private fun calculateTrainTax(taxableIncome: BigDecimal): Pair<BigDecimal, String> {
        val b1 = BigDecimal("20833.00")
        val b2 = BigDecimal("33332.00")
        val b3 = BigDecimal("66666.00")
        val b4 = BigDecimal("166666.00")
        val b5 = BigDecimal("666666.00")

        return when {
            taxableIncome <= b1 -> {
                Pair(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), "Income $taxableIncome <= $b1: 0% Tax")
            }
            taxableIncome <= b2 -> {
                val excess = taxableIncome.subtract(b1)
                val tax = excess.multiply(BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP)
                Pair(tax, "15% of excess over $b1 ($excess) = $tax")
            }
            taxableIncome <= b3 -> {
                val baseTax = BigDecimal("1874.85")
                val excess = taxableIncome.subtract(b2)
                val tax = baseTax.add(excess.multiply(BigDecimal("0.20"))).setScale(2, RoundingMode.HALF_UP)
                Pair(tax, "Base $baseTax + 20% of excess over $b2 ($excess) = $tax")
            }
            taxableIncome <= b4 -> {
                val baseTax = BigDecimal("8541.65")
                val excess = taxableIncome.subtract(b3)
                val tax = baseTax.add(excess.multiply(BigDecimal("0.25"))).setScale(2, RoundingMode.HALF_UP)
                Pair(tax, "Base $baseTax + 25% of excess over $b3 ($excess) = $tax")
            }
            taxableIncome <= b5 -> {
                val baseTax = BigDecimal("33541.65")
                val excess = taxableIncome.subtract(b4)
                val tax = baseTax.add(excess.multiply(BigDecimal("0.30"))).setScale(2, RoundingMode.HALF_UP)
                Pair(tax, "Base $baseTax + 30% of excess over $b4 ($excess) = $tax")
            }
            else -> {
                val baseTax = BigDecimal("183541.65")
                val excess = taxableIncome.subtract(b5)
                val tax = baseTax.add(excess.multiply(BigDecimal("0.35"))).setScale(2, RoundingMode.HALF_UP)
                Pair(tax, "Base $baseTax + 35% of excess over $b5 ($excess) = $tax")
            }
        }
    }
}
