package com.hr.payroll.internal

import com.hr.payroll.GrossToNetCalculationService
import com.hr.payroll.GrossToNetInput
import com.hr.payroll.GrossToNetResult
import com.hr.payroll.LineCategory
import com.hr.payroll.PayItemResultLine
import com.hr.payroll.internal.statutory.StatutoryCalculatorRegistry
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class DefaultGrossToNetCalculationService(
    private val calculatorRegistry: StatutoryCalculatorRegistry,
) : GrossToNetCalculationService {

    override fun calculateEmployee(input: GrossToNetInput): GrossToNetResult {
        require(input.basicSalary >= BigDecimal.ZERO) { "Basic salary cannot be negative: ${input.basicSalary}" }
        require(input.workingDaysInMonth > BigDecimal.ZERO) { "Working days in month must be positive: ${input.workingDaysInMonth}" }

        val lines = mutableListOf<PayItemResultLine>()

        // 1. Loss of Pay (LOP) Deduction
        val lopDeduction = if (input.unpaidLeaveDays > BigDecimal.ZERO) {
            input.basicSalary
                .multiply(input.unpaidLeaveDays)
                .divide(input.workingDaysInMonth, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        }

        val effectiveBasic = input.basicSalary.subtract(lopDeduction).max(BigDecimal.ZERO)

        lines.add(
            PayItemResultLine(
                category = LineCategory.EARNING,
                itemCode = "BASIC",
                itemName = "Basic Salary",
                amount = effectiveBasic,
                calculationTrace = if (lopDeduction > BigDecimal.ZERO) {
                    "Basic ${input.basicSalary} - LOP ($lopDeduction = ${input.basicSalary} × ${input.unpaidLeaveDays}/${input.workingDaysInMonth} days)"
                } else {
                    "Contractual Basic Salary"
                },
            )
        )

        // 2. Allowances
        var totalAllowances = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        var statutoryBaseAllowances = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        var taxableAllowances = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)

        for (allowance in input.allowances) {
            val amt = allowance.amount.setScale(2, RoundingMode.HALF_UP)
            totalAllowances = totalAllowances.add(amt)
            if (allowance.isStatutoryBase) {
                statutoryBaseAllowances = statutoryBaseAllowances.add(amt)
            }
            if (allowance.isTaxable) {
                taxableAllowances = taxableAllowances.add(amt)
            }

            lines.add(
                PayItemResultLine(
                    category = LineCategory.EARNING,
                    itemCode = allowance.code,
                    itemName = allowance.name,
                    amount = amt,
                    calculationTrace = "Allowance (Taxable=${allowance.isTaxable}, StatutoryBase=${allowance.isStatutoryBase})",
                )
            )
        }

        val grossPay = effectiveBasic.add(totalAllowances)
        val statutoryBase = effectiveBasic.add(statutoryBaseAllowances)
        val grossTaxable = effectiveBasic.add(taxableAllowances)

        // 3. Statutory Deductions & Employer Contributions
        val calculator = calculatorRegistry.getCalculator(input.employee.countryCode)
        val statutoryResult = calculator.calculate(input.employee, statutoryBase, grossTaxable)

        for (statLine in statutoryResult.lines) {
            if (statLine.employeeAmount > BigDecimal.ZERO) {
                lines.add(
                    PayItemResultLine(
                        category = LineCategory.STATUTORY_DEDUCTION,
                        itemCode = "${statLine.code}_EE",
                        itemName = "${statLine.name} (Employee)",
                        amount = statLine.employeeAmount,
                        isStatutory = true,
                        calculationTrace = statLine.calculationTrace,
                    )
                )
            }
            if (statLine.employerAmount > BigDecimal.ZERO) {
                lines.add(
                    PayItemResultLine(
                        category = LineCategory.EMPLOYER_CONTRIBUTION,
                        itemCode = "${statLine.code}_ER",
                        itemName = "${statLine.name} (Employer)",
                        amount = statLine.employerAmount,
                        isStatutory = true,
                        calculationTrace = statLine.calculationTrace,
                    )
                )
            }
        }

        // 4. Personal Income Tax Withheld
        if (statutoryResult.taxWithheld > BigDecimal.ZERO) {
            lines.add(
                PayItemResultLine(
                    category = LineCategory.TAX,
                    itemCode = "TAX_WITHHOLDING",
                    itemName = "Income Tax Withheld",
                    amount = statutoryResult.taxWithheld,
                    isStatutory = true,
                    calculationTrace = statutoryResult.taxCalculationTrace,
                )
            )
        }

        // 5. Voluntary Deductions
        var totalVoluntary = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        for (deduction in input.deductions) {
            val amt = deduction.amount.setScale(2, RoundingMode.HALF_UP)
            totalVoluntary = totalVoluntary.add(amt)

            lines.add(
                PayItemResultLine(
                    category = LineCategory.VOLUNTARY_DEDUCTION,
                    itemCode = deduction.code,
                    itemName = deduction.name,
                    amount = amt,
                    calculationTrace = "Voluntary Deduction (PreTax=${deduction.isPreTax})",
                )
            )
        }

        // 6. Net Pay Computation
        val totalDeductions = statutoryResult.totalEmployeeStatutory
            .add(statutoryResult.taxWithheld)
            .add(totalVoluntary)

        val netPay = grossPay.subtract(totalDeductions)
        check(netPay >= BigDecimal.ZERO) {
            "Negative net pay calculated ($netPay) for employee ${input.employee.employeeCode}: gross=$grossPay, deductions=$totalDeductions"
        }

        val employerTotalCost = grossPay.add(statutoryResult.totalEmployerStatutory)

        return GrossToNetResult(
            employeeId = input.employee.id,
            employeeCode = input.employee.employeeCode,
            employeeName = input.employee.displayName,
            currency = input.employee.currency,
            basicSalary = input.basicSalary,
            lossOfPayDeduction = lopDeduction,
            grossPay = grossPay,
            totalStatutoryEmployee = statutoryResult.totalEmployeeStatutory,
            totalStatutoryEmployer = statutoryResult.totalEmployerStatutory,
            taxableIncome = statutoryResult.taxableIncome,
            taxWithheld = statutoryResult.taxWithheld,
            totalVoluntaryDeductions = totalVoluntary,
            netPay = netPay,
            employerTotalCost = employerTotalCost,
            lines = lines,
        )
    }
}
