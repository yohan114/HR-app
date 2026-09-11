package com.hr.payroll

import com.hr.payroll.internal.statutory.UaeStatutoryCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("UAE Statutory Calculator (0% Income Tax / EOSG Accrual / GPSSA)")
class UaeStatutoryTest {

    private val calculator = UaeStatutoryCalculator()

    @Test
    fun `calculates End of Service Gratuity provision and 0 percent tax for expatriate employee`() {
        val expatEmployee = EmployeePayrollProfile(
            id = UUID.randomUUID(),
            employeeCode = "AE001",
            displayName = "Alex Carter",
            countryCode = "AE",
            currency = "AED",
            socialSecurityNumber = "EXPAT-99120",
            joinDate = LocalDate.now().minusYears(3), // 3 years tenure (< 5 years)
        )

        val baseSalary = BigDecimal("24000.00")
        val grossTaxable = BigDecimal("30000.00") // 24k base + 6k allowance

        val result = calculator.calculate(expatEmployee, baseSalary, grossTaxable)

        // Expatriate employee: 0 employee statutory deductions
        assertThat(result.totalEmployeeStatutory).isEqualByComparingTo("0.00")

        // 0% Federal Income Tax
        assertThat(result.taxWithheld).isEqualByComparingTo("0.00")

        // EOSG Employer Accrual provision:
        // For < 5 years: 21 days / 360 * 24,000 = 0.058333 * 24,000 = 1,400.00 AED
        val eosgLine = result.lines.find { it.code == "UAE_EOSG_ACCRUAL" }
        assertThat(eosgLine).isNotNull
        assertThat(eosgLine!!.employeeAmount).isEqualByComparingTo("0.00")
        assertThat(eosgLine.employerAmount).isEqualByComparingTo("1400.00")
        assertThat(result.totalEmployerStatutory).isEqualByComparingTo("1400.00")
    }

    @Test
    fun `calculates GPSSA for UAE national employee`() {
        val nationalEmployee = EmployeePayrollProfile(
            id = UUID.randomUUID(),
            employeeCode = "AE002",
            displayName = "Fatima Al-Nuaimi",
            countryCode = "AE",
            currency = "AED",
            socialSecurityNumber = "784-1990-1234567-1", // Emirates ID starts with 784
            joinDate = LocalDate.now().minusYears(2),
        )

        val baseSalary = BigDecimal("30000.00")
        val grossTaxable = BigDecimal("35000.00")

        val result = calculator.calculate(nationalEmployee, baseSalary, grossTaxable)

        // GPSSA: Employee 5% (1,500 AED), Employer 12.5% (3,750 AED)
        val gpssaLine = result.lines.find { it.code == "GPSSA" }
        assertThat(gpssaLine).isNotNull
        assertThat(gpssaLine!!.employeeAmount).isEqualByComparingTo("1500.00")
        assertThat(gpssaLine.employerAmount).isEqualByComparingTo("3750.00")

        assertThat(result.totalEmployeeStatutory).isEqualByComparingTo("1500.00")
        assertThat(result.totalEmployerStatutory).isEqualByComparingTo("3750.00")
        assertThat(result.taxWithheld).isEqualByComparingTo("0.00")
    }
}
