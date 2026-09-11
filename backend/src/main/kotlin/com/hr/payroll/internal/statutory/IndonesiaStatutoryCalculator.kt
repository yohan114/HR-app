package com.hr.payroll.internal.statutory

import com.hr.payroll.EmployeePayrollProfile
import com.hr.payroll.StatutoryCalculationResult
import com.hr.payroll.StatutoryDeductionLine
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Statutory calculation engine for Indonesia (ID).
 *
 * Implements:
 * 1. BPJS Ketenagakerjaan (Employment Social Security):
 *    - Jaminan Hari Tua (JHT / Old Age): Employee 2.0%, Employer 3.7% (total 5.7%).
 *    - Jaminan Kecelakaan Kerja (JKK / Work Accident): Employer 0.54% (Standard Group II).
 *    - Jaminan Kematian (JKM / Life): Employer 0.30%.
 *    - Jaminan Pensiun (JP / Pension): Employee 1.0%, Employer 2.0% (capped at 10,042,300 IDR/mo).
 * 2. BPJS Kesehatan (Healthcare):
 *    - Employee 1.0%, Employer 4.0% (total 5.0%, capped at 12,000,000 IDR/mo).
 * 3. PPh 21 (Personal Income Tax):
 *    - Pre-tax deductions: Employee mandatory contributions (JHT + JP + BPJS Kes).
 *    - Biaya Jabatan (occupational deduction): 5% of gross, capped at 500,000 IDR/mo.
 *    - PTKP (Non-taxable income baseline for TK/0): 54,000,000 IDR/year (4,500,000 IDR/mo).
 *    - Annualized progressive brackets (UU HPP): 5%, 15%, 25%, 30%, 35%.
 */
@Component
class IndonesiaStatutoryCalculator : StatutoryCalculator {

    override val countryCode: String = "ID"

    // BPJS Ketenagakerjaan rates
    private val jhtEmployeeRate = BigDecimal("0.02")
    private val jhtEmployerRate = BigDecimal("0.037")
    private val jkkEmployerRate = BigDecimal("0.0054")
    private val jkmEmployerRate = BigDecimal("0.003")

    private val jpEmployeeRate = BigDecimal("0.01")
    private val jpEmployerRate = BigDecimal("0.02")
    private val jpCeiling = BigDecimal("10042300.00")

    // BPJS Kesehatan rates
    private val bpjsKesEmployeeRate = BigDecimal("0.01")
    private val bpjsKesEmployerRate = BigDecimal("0.04")
    private val bpjsKesCeiling = BigDecimal("12000000.00")

    // PPh 21 relief & brackets
    private val biayaJabatanRate = BigDecimal("0.05")
    private val biayaJabatanMonthlyCap = BigDecimal("500000.00")
    private val monthlyPtkp = BigDecimal("4500000.00") // TK/0 single baseline

    private val annualBracket1 = BigDecimal("60000000.00")
    private val annualBracket2 = BigDecimal("250000000.00")
    private val annualBracket3 = BigDecimal("500000000.00")
    private val annualBracket4 = BigDecimal("5000000000.00")

    override fun calculate(
        employee: EmployeePayrollProfile,
        statutoryBaseEarnings: BigDecimal,
        grossTaxableEarnings: BigDecimal,
    ): StatutoryCalculationResult {
        // 1. JHT (Old Age)
        val jhtEmployee = statutoryBaseEarnings.multiply(jhtEmployeeRate).setScale(2, RoundingMode.HALF_UP)
        val jhtEmployer = statutoryBaseEarnings.multiply(jhtEmployerRate).setScale(2, RoundingMode.HALF_UP)
        val jhtLine = StatutoryDeductionLine(
            code = "BPJS_JHT",
            name = "BPJS Ketenagakerjaan - Jaminan Hari Tua",
            employeeAmount = jhtEmployee,
            employerAmount = jhtEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings × [EE=2.0% ($jhtEmployee), ER=3.7% ($jhtEmployer)]",
        )

        // 2. JKK (Work Accident - Employer only)
        val jkkEmployer = statutoryBaseEarnings.multiply(jkkEmployerRate).setScale(2, RoundingMode.HALF_UP)
        val jkkLine = StatutoryDeductionLine(
            code = "BPJS_JKK",
            name = "BPJS Ketenagakerjaan - Jaminan Kecelakaan Kerja",
            employeeAmount = BigDecimal.ZERO,
            employerAmount = jkkEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings × ER=0.54% ($jkkEmployer)",
        )

        // 3. JKM (Death/Life - Employer only)
        val jkmEmployer = statutoryBaseEarnings.multiply(jkmEmployerRate).setScale(2, RoundingMode.HALF_UP)
        val jkmLine = StatutoryDeductionLine(
            code = "BPJS_JKM",
            name = "BPJS Ketenagakerjaan - Jaminan Kematian",
            employeeAmount = BigDecimal.ZERO,
            employerAmount = jkmEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings × ER=0.30% ($jkmEmployer)",
        )

        // 4. JP (Pension - capped at jpCeiling)
        val jpBase = statutoryBaseEarnings.min(jpCeiling)
        val jpEmployee = jpBase.multiply(jpEmployeeRate).setScale(2, RoundingMode.HALF_UP)
        val jpEmployer = jpBase.multiply(jpEmployerRate).setScale(2, RoundingMode.HALF_UP)
        val jpLine = StatutoryDeductionLine(
            code = "BPJS_JP",
            name = "BPJS Ketenagakerjaan - Jaminan Pensiun",
            employeeAmount = jpEmployee,
            employerAmount = jpEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings -> Capped=$jpBase × [EE=1.0% ($jpEmployee), ER=2.0% ($jpEmployer)]",
        )

        // 5. BPJS Kesehatan (Healthcare - capped at bpjsKesCeiling)
        val kesBase = statutoryBaseEarnings.min(bpjsKesCeiling)
        val kesEmployee = kesBase.multiply(bpjsKesEmployeeRate).setScale(2, RoundingMode.HALF_UP)
        val kesEmployer = kesBase.multiply(bpjsKesEmployerRate).setScale(2, RoundingMode.HALF_UP)
        val kesLine = StatutoryDeductionLine(
            code = "BPJS_KESEHATAN",
            name = "BPJS Kesehatan",
            employeeAmount = kesEmployee,
            employerAmount = kesEmployer,
            calculationTrace = "Base=$statutoryBaseEarnings -> Capped=$kesBase × [EE=1.0% ($kesEmployee), ER=4.0% ($kesEmployer)]",
        )

        val totalEmployeeStatutory = jhtEmployee.add(jpEmployee).add(kesEmployee)
        val totalEmployerStatutory = jhtEmployer.add(jkkEmployer).add(jkmEmployer).add(jpEmployer).add(kesEmployer)

        // 6. PPh 21 calculation
        val (pph21Tax, pphTrace, monthlyTaxable) = calculatePph21(
            gross = grossTaxableEarnings,
            employeeStatutory = totalEmployeeStatutory,
        )

        return StatutoryCalculationResult(
            totalEmployeeStatutory = totalEmployeeStatutory,
            totalEmployerStatutory = totalEmployerStatutory,
            taxWithheld = pph21Tax,
            taxableIncome = monthlyTaxable,
            lines = listOf(jhtLine, jkkLine, jkmLine, jpLine, kesLine),
            taxCalculationTrace = pphTrace,
        )
    }

    private fun calculatePph21(
        gross: BigDecimal,
        employeeStatutory: BigDecimal,
    ): Triple<BigDecimal, String, BigDecimal> {
        // Biaya Jabatan: 5% of gross, max 500,000 IDR
        val biayaJabatan = gross.multiply(biayaJabatanRate).min(biayaJabatanMonthlyCap).setScale(2, RoundingMode.HALF_UP)

        // Monthly Net = Gross - Employee Statutory - Biaya Jabatan
        val netIncomeMonthly = gross.subtract(employeeStatutory).subtract(biayaJabatan).max(BigDecimal.ZERO)

        // Net Taxable = Monthly Net - PTKP Monthly
        val monthlyTaxable = netIncomeMonthly.subtract(monthlyPtkp).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

        if (monthlyTaxable <= BigDecimal.ZERO) {
            return Triple(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                "Net Income $netIncomeMonthly <= PTKP Relief $monthlyPtkp -> PPh 21 = 0.00",
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            )
        }

        // Annualized Taxable Income
        val annualTaxable = monthlyTaxable.multiply(BigDecimal("12")).setScale(2, RoundingMode.HALF_UP)
        var remaining = annualTaxable
        var annualTax = BigDecimal.ZERO
        val trace = mutableListOf("Annual Taxable: $annualTaxable (Monthly Taxable: $monthlyTaxable)")

        // Tier 1: 0 - 60M @ 5%
        val t1 = remaining.min(annualBracket1)
        if (t1 > BigDecimal.ZERO) {
            val tax1 = t1.multiply(BigDecimal("0.05"))
            annualTax = annualTax.add(tax1)
            trace.add("Tier 1: 5% × $t1 = $tax1")
            remaining = remaining.subtract(t1)
        }

        // Tier 2: 60M - 250M (slab 190M) @ 15%
        val slab2Width = annualBracket2.subtract(annualBracket1)
        if (remaining > BigDecimal.ZERO) {
            val t2 = remaining.min(slab2Width)
            val tax2 = t2.multiply(BigDecimal("0.15"))
            annualTax = annualTax.add(tax2)
            trace.add("Tier 2: 15% × $t2 = $tax2")
            remaining = remaining.subtract(t2)
        }

        // Tier 3: 250M - 500M (slab 250M) @ 25%
        val slab3Width = annualBracket3.subtract(annualBracket2)
        if (remaining > BigDecimal.ZERO) {
            val t3 = remaining.min(slab3Width)
            val tax3 = t3.multiply(BigDecimal("0.25"))
            annualTax = annualTax.add(tax3)
            trace.add("Tier 3: 25% × $t3 = $tax3")
            remaining = remaining.subtract(t3)
        }

        // Tier 4: 500M - 5B (slab 4.5B) @ 30%
        val slab4Width = annualBracket4.subtract(annualBracket3)
        if (remaining > BigDecimal.ZERO) {
            val t4 = remaining.min(slab4Width)
            val tax4 = t4.multiply(BigDecimal("0.30"))
            annualTax = annualTax.add(tax4)
            trace.add("Tier 4: 30% × $t4 = $tax4")
            remaining = remaining.subtract(t4)
        }

        // Tier 5: > 5B @ 35%
        if (remaining > BigDecimal.ZERO) {
            val tax5 = remaining.multiply(BigDecimal("0.35"))
            annualTax = annualTax.add(tax5)
            trace.add("Tier 5: 35% × $remaining = $tax5")
        }

        val monthlyTax = annualTax.divide(BigDecimal("12"), 2, RoundingMode.HALF_UP)
        return Triple(
            monthlyTax,
            trace.joinToString("; ") + " => Annual PPh 21 = $annualTax => Monthly = $monthlyTax",
            monthlyTaxable,
        )
    }
}
