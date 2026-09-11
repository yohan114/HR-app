package com.hr.payroll

import com.hr.payroll.internal.statutory.PhilippinesStatutoryCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Philippines Statutory Calculator (SSS / PhilHealth / Pag-IBIG / TRAIN Tax)")
class PhilippinesStatutoryTest {

    private val calculator = PhilippinesStatutoryCalculator()

    private val sampleEmployee = EmployeePayrollProfile(
        id = UUID.randomUUID(),
        employeeCode = "PH001",
        displayName = "Maria Santos",
        countryCode = "PH",
        currency = "PHP",
        socialSecurityNumber = "04-1234567-8",
        joinDate = LocalDate.of(2022, 6, 15),
    )

    @Test
    fun `calculates SSS capped at MSC ceiling, PhilHealth 5 percent split, Pag-IBIG 200 PHP cap`() {
        // Base salary = 40,000 PHP (exceeds SSS MSC ceiling of 30,000; within PhilHealth 10k-100k bracket)
        val baseEarnings = BigDecimal("40000.00")
        val grossTaxable = BigDecimal("40000.00")

        val result = calculator.calculate(sampleEmployee, baseEarnings, grossTaxable)

        // SSS: MSC clamped to 30,000 -> EE = 30,000 * 4.5% = 1,350.00; ER = 30,000 * 9.5% = 2,850.00
        val sssLine = result.lines.find { it.code == "SSS" }
        assertThat(sssLine).isNotNull
        assertThat(sssLine!!.employeeAmount).isEqualByComparingTo("1350.00")
        assertThat(sssLine.employerAmount).isEqualByComparingTo("2850.00")

        // PhilHealth: 40,000 * 2.5% = 1,000.00 EE; 1,000.00 ER
        val phLine = result.lines.find { it.code == "PHILHEALTH" }
        assertThat(phLine).isNotNull
        assertThat(phLine!!.employeeAmount).isEqualByComparingTo("1000.00")
        assertThat(phLine.employerAmount).isEqualByComparingTo("1000.00")

        // Pag-IBIG: 40,000 * 2% = 800.00 -> Capped at 200.00 PHP EE; 200.00 PHP ER
        val pagIbigLine = result.lines.find { it.code == "PAGIBIG" }
        assertThat(pagIbigLine).isNotNull
        assertThat(pagIbigLine!!.employeeAmount).isEqualByComparingTo("200.00")
        assertThat(pagIbigLine.employerAmount).isEqualByComparingTo("200.00")

        // Total Employee Statutory = 1,350 + 1,000 + 200 = 2,550.00 PHP
        assertThat(result.totalEmployeeStatutory).isEqualByComparingTo("2550.00")

        // Taxable income = 40,000 - 2,550 = 37,450.00 PHP
        assertThat(result.taxableIncome).isEqualByComparingTo("37450.00")

        // TRAIN Tax on 37,450.00 PHP (falls into bracket 33,332.01 - 66,666.00):
        // Base tax: 1,874.85 + 20% on excess over 33,332.00 (4,118.00 * 0.20 = 823.60)
        // Total Tax = 1,874.85 + 823.60 = 2,698.45 PHP
        assertThat(result.taxWithheld).isEqualByComparingTo("2698.45")
    }

    @Test
    fun `respects PhilHealth minimum floor and exempts low income from TRAIN tax`() {
        // Base salary = 3,000 PHP (below SSS floor 4,000; below PhilHealth floor 10,000; below TRAIN tax exemption 20,833)
        val baseEarnings = BigDecimal("3000.00")
        val grossTaxable = BigDecimal("3000.00")

        val result = calculator.calculate(sampleEmployee, baseEarnings, grossTaxable)

        // SSS: MSC floor 4,000 -> EE = 4,000 * 4.5% = 180.00 PHP
        val sssLine = result.lines.find { it.code == "SSS" }
        assertThat(sssLine!!.employeeAmount).isEqualByComparingTo("180.00")

        // PhilHealth: Floor 10,000 -> EE = 10,000 * 2.5% = 250.00 PHP
        val phLine = result.lines.find { it.code == "PHILHEALTH" }
        assertThat(phLine!!.employeeAmount).isEqualByComparingTo("250.00")

        // TRAIN Tax = 0.00 (taxable income < 20,833)
        assertThat(result.taxWithheld).isEqualByComparingTo("0.00")
    }
}
