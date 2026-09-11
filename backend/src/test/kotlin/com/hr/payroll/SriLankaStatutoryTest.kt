package com.hr.payroll

import com.hr.payroll.internal.statutory.SriLankaStatutoryCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Sri Lanka Statutory Calculator (EPF / ETF / APIT)")
class SriLankaStatutoryTest {

    private val calculator = SriLankaStatutoryCalculator()

    private val sampleEmployee = EmployeePayrollProfile(
        id = UUID.randomUUID(),
        employeeCode = "LK001",
        displayName = "Kasun Perera",
        countryCode = "LK",
        currency = "LKR",
        taxIdentificationNumber = "199012345678",
        joinDate = LocalDate.of(2023, 1, 1),
    )

    @Test
    fun `calculates EPF 8 percent employee and 12 percent employer, ETF 3 percent employer`() {
        val baseEarnings = BigDecimal("150000.00")
        val grossTaxable = BigDecimal("150000.00")

        val result = calculator.calculate(sampleEmployee, baseEarnings, grossTaxable)

        // EPF Employee = 150,000 * 8% = 12,000.00
        // EPF Employer = 150,000 * 12% = 18,000.00
        val epfLine = result.lines.find { it.code == "EPF" }
        assertThat(epfLine).isNotNull
        assertThat(epfLine!!.employeeAmount).isEqualByComparingTo("12000.00")
        assertThat(epfLine.employerAmount).isEqualByComparingTo("18000.00")

        // ETF Employer = 150,000 * 3% = 4,500.00
        val etfLine = result.lines.find { it.code == "ETF" }
        assertThat(etfLine).isNotNull
        assertThat(etfLine!!.employeeAmount).isEqualByComparingTo("0.00")
        assertThat(etfLine.employerAmount).isEqualByComparingTo("4500.00")

        assertThat(result.totalEmployeeStatutory).isEqualByComparingTo("12000.00")
        assertThat(result.totalEmployerStatutory).isEqualByComparingTo("22500.00")
    }

    @Test
    fun `exempts income at or below 100000 LKR threshold from APIT`() {
        val earnings = BigDecimal("100000.00")
        val result = calculator.calculate(sampleEmployee, earnings, earnings)

        assertThat(result.taxWithheld).isEqualByComparingTo("0.00")
        assertThat(result.taxCalculationTrace).contains("APIT = 0.00")
    }

    @Test
    fun `calculates APIT across multiple progressive tax tiers`() {
        // 180,000 LKR taxable income:
        // First 100,000 is tax-free -> Remaining: 80,000
        // Tier 1: 41,666.67 * 6% = 2,500.00 -> Remaining: 38,333.33
        // Tier 2: 38,333.33 * 12% = 4,600.00
        // Total APIT = 2,500.00 + 4,600.00 = 7,100.00 LKR
        val earnings = BigDecimal("180000.00")
        val result = calculator.calculate(sampleEmployee, earnings, earnings)

        assertThat(result.taxWithheld).isEqualByComparingTo("7100.00")
        assertThat(result.taxCalculationTrace).contains("Tier 1").contains("Tier 2")
    }
}
