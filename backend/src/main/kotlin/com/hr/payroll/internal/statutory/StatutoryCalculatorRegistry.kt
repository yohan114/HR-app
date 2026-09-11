package com.hr.payroll.internal.statutory

import com.hr.payroll.EmployeePayrollProfile
import com.hr.payroll.StatutoryCalculationResult
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Locale

@Component
class StatutoryCalculatorRegistry(
    calculators: List<StatutoryCalculator>,
) {
    private val calculatorMap: Map<String, StatutoryCalculator> =
        calculators.associateBy { it.countryCode.uppercase(Locale.ROOT) }

    private val defaultCalculator = object : StatutoryCalculator {
        override val countryCode: String = "DEFAULT"
        override fun calculate(
            employee: EmployeePayrollProfile,
            statutoryBaseEarnings: BigDecimal,
            grossTaxableEarnings: BigDecimal,
        ): StatutoryCalculationResult {
            return StatutoryCalculationResult(
                totalEmployeeStatutory = BigDecimal.ZERO,
                totalEmployerStatutory = BigDecimal.ZERO,
                taxWithheld = BigDecimal.ZERO,
                taxableIncome = grossTaxableEarnings,
                lines = emptyList(),
                taxCalculationTrace = "No statutory calculator registered for country ${employee.countryCode}",
            )
        }
    }

    fun getCalculator(countryCode: String): StatutoryCalculator {
        return calculatorMap[countryCode.uppercase(Locale.ROOT)] ?: defaultCalculator
    }
}
