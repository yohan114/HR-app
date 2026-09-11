package com.hr.payroll

import com.hr.payroll.internal.statutory.IndonesiaStatutoryCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Indonesia Statutory Calculator (BPJS Ketenagakerjaan / Kesehatan / PPh 21)")
class IndonesiaStatutoryTest {

    private val calculator = IndonesiaStatutoryCalculator()

    private val sampleEmployee = EmployeePayrollProfile(
        id = UUID.randomUUID(),
        employeeCode = "ID001",
        displayName = "Budi Pratama",
        countryCode = "ID",
        currency = "IDR",
        socialSecurityNumber = "0001234567890",
        joinDate = LocalDate.of(2023, 1, 10),
    )

    @Test
    fun `calculates standard BPJS contributions and PPh 21 tax for 15 million IDR salary`() {
        // Base and Gross = 15,000,000 IDR
        val baseEarnings = BigDecimal("15000000.00")
        val grossTaxable = BigDecimal("15000000.00")

        val result = calculator.calculate(sampleEmployee, baseEarnings, grossTaxable)

        // 1. JHT: EE = 15M * 2% = 300,000.00; ER = 15M * 3.7% = 555,000.00
        val jhtLine = result.lines.find { it.code == "BPJS_JHT" }
        assertThat(jhtLine).isNotNull
        assertThat(jhtLine!!.employeeAmount).isEqualByComparingTo("300000.00")
        assertThat(jhtLine.employerAmount).isEqualByComparingTo("555000.00")

        // 2. JKK: ER = 15M * 0.54% = 81,000.00
        val jkkLine = result.lines.find { it.code == "BPJS_JKK" }
        assertThat(jkkLine).isNotNull
        assertThat(jkkLine!!.employerAmount).isEqualByComparingTo("81000.00")

        // 3. JKM: ER = 15M * 0.30% = 45,000.00
        val jkmLine = result.lines.find { it.code == "BPJS_JKM" }
        assertThat(jkmLine).isNotNull
        assertThat(jkmLine!!.employerAmount).isEqualByComparingTo("45000.00")

        // 4. JP: Capped at 10,042,300 IDR -> EE = 10,042,300 * 1% = 100,423.00; ER = 200,846.00
        val jpLine = result.lines.find { it.code == "BPJS_JP" }
        assertThat(jpLine).isNotNull
        assertThat(jpLine!!.employeeAmount).isEqualByComparingTo("100423.00")
        assertThat(jpLine.employerAmount).isEqualByComparingTo("200846.00")

        // 5. BPJS Kesehatan: Capped at 12,000,000 IDR -> EE = 12M * 1% = 120,000.00; ER = 12M * 4% = 480,000.00
        val kesLine = result.lines.find { it.code == "BPJS_KESEHATAN" }
        assertThat(kesLine).isNotNull
        assertThat(kesLine!!.employeeAmount).isEqualByComparingTo("120000.00")
        assertThat(kesLine.employerAmount).isEqualByComparingTo("480000.00")

        // Total Employee Statutory = 300,000 + 100,423 + 120,000 = 520,423.00 IDR
        assertThat(result.totalEmployeeStatutory).isEqualByComparingTo("520423.00")

        // Tax calculation verification:
        // Biaya jabatan = 15M * 5% = 750,000 -> capped at 500,000.00 IDR
        // Monthly Net = 15,000,000 - 520,423 - 500,000 = 13,979,577.00 IDR
        // Monthly Taxable = 13,979,577 - 4,500,000 (PTKP) = 9,479,577.00 IDR
        assertThat(result.taxableIncome).isEqualByComparingTo("9479577.00")

        // Annualized Taxable = 9,479,577 * 12 = 113,754,924.00 IDR
        // Tier 1 (60M @ 5%) = 3,000,000.00 IDR
        // Tier 2 ((113,754,924 - 60,000,000) = 53,754,924 @ 15%) = 8,063,238.60 IDR
        // Annual Tax = 3,000,000 + 8,063,238.60 = 11,063,238.60 IDR
        // Monthly Tax = 11,063,238.60 / 12 = 921,936.55 IDR
        assertThat(result.taxWithheld).isEqualByComparingTo("921936.55")
    }

    @Test
    fun `exempts low income earners from PPh 21 when net income is below PTKP threshold`() {
        val baseEarnings = BigDecimal("4000000.00")
        val grossTaxable = BigDecimal("4000000.00")

        val result = calculator.calculate(sampleEmployee, baseEarnings, grossTaxable)

        assertThat(result.taxWithheld).isEqualByComparingTo("0.00")
        assertThat(result.taxableIncome).isEqualByComparingTo("0.00")
    }
}
