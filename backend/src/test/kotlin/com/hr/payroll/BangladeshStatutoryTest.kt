package com.hr.payroll

import com.hr.payroll.internal.statutory.BangladeshStatutoryCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Bangladesh Statutory Calculator (Contributory Provident Fund / NBR Income Tax)")
class BangladeshStatutoryTest {

    private val calculator = BangladeshStatutoryCalculator()

    private val sampleEmployee = EmployeePayrollProfile(
        id = UUID.randomUUID(),
        employeeCode = "BD001",
        displayName = "Tanvir Ahmed",
        countryCode = "BD",
        currency = "BDT",
        socialSecurityNumber = "NID-99128312",
        joinDate = LocalDate.of(2022, 1, 1),
    )

    @Test
    fun `calculates 10 percent CPF and NBR progressive tax on 70000 BDT monthly salary`() {
        val baseSalary = BigDecimal("70000.00")
        val grossTaxable = BigDecimal("70000.00")

        val result = calculator.calculate(sampleEmployee, baseSalary, grossTaxable)

        // CPF: 70,000 * 10% = 7,000.00 BDT EE, 7,000.00 BDT ER
        val cpfLine = result.lines.find { it.code == "BD_CPF" }
        assertThat(cpfLine).isNotNull
        assertThat(cpfLine!!.employeeAmount).isEqualByComparingTo("7000.00")
        assertThat(cpfLine.employerAmount).isEqualByComparingTo("7000.00")

        assertThat(result.totalEmployeeStatutory).isEqualByComparingTo("7000.00")
        assertThat(result.totalEmployerStatutory).isEqualByComparingTo("7000.00")

        // Taxable monthly: 70,000 - 7,000 (CPF) = 63,000.00 BDT
        assertThat(result.taxableIncome).isEqualByComparingTo("63000.00")

        // Annualized Taxable = 63,000 * 12 = 756,000.00 BDT
        // First 350k exempt -> Remaining: 406,000.00 BDT
        // Next 100k @ 5% = 5,000.00 BDT -> Remaining: 306,000.00 BDT
        // Next 300k @ 10% = 30,000.00 BDT -> Remaining: 6,000.00 BDT
        // Next 6k @ 15% = 900.00 BDT
        // Total Annual Tax = 5,000 + 30,000 + 900 = 35,900.00 BDT
        // Monthly Tax = 35,900 / 12 = 2,991.67 BDT
        assertThat(result.taxWithheld).isEqualByComparingTo("2991.67")
    }

    @Test
    fun `exempts low income earners below 350000 BDT annual threshold`() {
        val baseSalary = BigDecimal("25000.00")
        val grossTaxable = BigDecimal("25000.00")

        val result = calculator.calculate(sampleEmployee, baseSalary, grossTaxable)

        // 25k monthly = 300k annual < 350k threshold
        assertThat(result.taxWithheld).isEqualByComparingTo("0.00")
    }
}
