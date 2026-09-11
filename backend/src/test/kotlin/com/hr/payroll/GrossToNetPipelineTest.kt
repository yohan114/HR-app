package com.hr.payroll

import com.hr.payroll.internal.DefaultGrossToNetCalculationService
import com.hr.payroll.internal.statutory.PhilippinesStatutoryCalculator
import com.hr.payroll.internal.statutory.SriLankaStatutoryCalculator
import com.hr.payroll.internal.statutory.StatutoryCalculatorRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Gross-To-Net Calculation Pipeline")
class GrossToNetPipelineTest {

    private lateinit var pipeline: DefaultGrossToNetCalculationService

    private val employeeId = UUID.randomUUID()
    private val periodId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val registry = StatutoryCalculatorRegistry(
            listOf(
                SriLankaStatutoryCalculator(),
                PhilippinesStatutoryCalculator(),
            )
        )
        pipeline = DefaultGrossToNetCalculationService(registry)
    }

    @Test
    fun `calculates complete gross-to-net with basic, allowances, statutory and voluntary deductions`() {
        val employee = EmployeePayrollProfile(
            id = employeeId,
            employeeCode = "LK002",
            displayName = "Nimal Silva",
            countryCode = "LK",
            currency = "LKR",
            joinDate = LocalDate.of(2023, 5, 1),
        )

        val input = GrossToNetInput(
            employee = employee,
            payPeriodId = periodId,
            periodStartDate = LocalDate.of(2026, 3, 1),
            periodEndDate = LocalDate.of(2026, 3, 31),
            basicSalary = BigDecimal("200000.00"),
            allowances = listOf(
                AllowanceInput("HOUSING", "Housing Allowance", BigDecimal("30000.00"), isTaxable = true, isStatutoryBase = true),
                AllowanceInput("TRANSPORT", "Transport Reimbursement", BigDecimal("10000.00"), isTaxable = false, isStatutoryBase = false),
            ),
            deductions = listOf(
                DeductionInput("STAFF_LOAN", "Company Vehicle Loan", BigDecimal("15000.00")),
            ),
            workingDaysInMonth = BigDecimal("22.00"),
            unpaidLeaveDays = BigDecimal.ZERO,
        )

        val result = pipeline.calculateEmployee(input)

        // Gross Pay = Basic (200,000) + Housing (30,000) + Transport (10,000) = 240,000.00
        assertThat(result.grossPay).isEqualByComparingTo("240000.00")

        // Statutory Base = Basic (200,000) + Housing (30,000) = 230,000.00
        // EPF Employee = 230,000 * 8% = 18,400.00
        // EPF Employer = 230,000 * 12% = 27,600.00
        // ETF Employer = 230,000 * 3% = 6,900.00
        assertThat(result.totalStatutoryEmployee).isEqualByComparingTo("18400.00")
        assertThat(result.totalStatutoryEmployer).isEqualByComparingTo("34500.00")

        // Gross Taxable = Basic (200,000) + Housing (30,000) = 230,000.00 (Transport is non-taxable)
        // APIT on 230,000.00 LKR:
        // - 100,000 tax-free -> Remaining 130,000
        // - Tier 1: 41,666.67 * 6% = 2,500.00 -> Remaining 88,333.33
        // - Tier 2: 41,666.67 * 12% = 5,000.00 -> Remaining 46,666.66
        // - Tier 3: 41,666.67 * 18% = 7,500.00 -> Remaining 4,999.99
        // - Tier 4: 4,999.99 * 24% = 1,200.00
        // Total APIT = 2,500 + 5,000 + 7,500 + 1,200 = 16,200.00 LKR
        assertThat(result.taxWithheld).isEqualByComparingTo("16200.00")

        // Voluntary deductions = 15,000.00
        assertThat(result.totalVoluntaryDeductions).isEqualByComparingTo("15000.00")

        // Net Pay = Gross (240,000) - EPF (18,400) - APIT (16,200) - Loan (15,000) = 190,400.00 LKR
        assertThat(result.netPay).isEqualByComparingTo("190400.00")

        // Employer Total Cost = Gross (240,000) + EPF/ETF Employer (34,500) = 274,500.00 LKR
        assertThat(result.employerTotalCost).isEqualByComparingTo("274500.00")

        // Every line item has a descriptive calculation trace
        assertThat(result.lines).isNotEmpty
        val basicLine = result.lines.find { it.itemCode == "BASIC" }
        assertThat(basicLine?.calculationTrace).isNotBlank()
    }

    @Test
    fun `deducts loss of pay for unpaid absence days proportionally`() {
        val employee = EmployeePayrollProfile(
            id = employeeId,
            employeeCode = "LK003",
            displayName = "Sunil Shantha",
            countryCode = "LK",
            currency = "LKR",
            joinDate = LocalDate.of(2024, 1, 1),
        )

        // Basic 110,000 LKR, 22 working days, 2 unpaid leave days
        // LOP deduction = 110,000 * 2 / 22 = 10,000.00 LKR
        // Effective basic = 100,000.00 LKR
        val input = GrossToNetInput(
            employee = employee,
            payPeriodId = periodId,
            periodStartDate = LocalDate.of(2026, 3, 1),
            periodEndDate = LocalDate.of(2026, 3, 31),
            basicSalary = BigDecimal("110000.00"),
            workingDaysInMonth = BigDecimal("22.00"),
            unpaidLeaveDays = BigDecimal("2.00"),
        )

        val result = pipeline.calculateEmployee(input)

        assertThat(result.lossOfPayDeduction).isEqualByComparingTo("10000.00")
        assertThat(result.grossPay).isEqualByComparingTo("100000.00")

        val basicLine = result.lines.find { it.itemCode == "BASIC" }
        assertThat(basicLine?.amount).isEqualByComparingTo("100000.00")
        assertThat(basicLine?.calculationTrace).contains("LOP")
    }

    @Test
    fun `prevents negative net pay and throws IllegalStateException`() {
        val employee = EmployeePayrollProfile(
            id = employeeId,
            employeeCode = "LK004",
            displayName = "Over-deducted Employee",
            countryCode = "LK",
            currency = "LKR",
            joinDate = LocalDate.of(2024, 1, 1),
        )

        // Basic 50,000 LKR, voluntary deduction 60,000 LKR
        val input = GrossToNetInput(
            employee = employee,
            payPeriodId = periodId,
            periodStartDate = LocalDate.of(2026, 3, 1),
            periodEndDate = LocalDate.of(2026, 3, 31),
            basicSalary = BigDecimal("50000.00"),
            deductions = listOf(
                DeductionInput("EXCESS_LOAN", "Garnishment", BigDecimal("60000.00")),
            ),
        )

        assertThatThrownBy { pipeline.calculateEmployee(input) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Negative net pay")
    }
}
