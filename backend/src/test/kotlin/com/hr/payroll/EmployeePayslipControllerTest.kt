package com.hr.payroll

import com.hr.payroll.internal.EmployeePayslipController
import com.hr.payroll.LineCategory
import com.hr.payroll.internal.PayPeriod
import com.hr.payroll.internal.PayPeriodRepository
import com.hr.payroll.internal.PayrollResult
import com.hr.payroll.internal.PayrollResultLine
import com.hr.payroll.internal.PayrollResultLineRepository
import com.hr.payroll.internal.PayrollResultRepository
import com.hr.payroll.internal.PayrollRun
import com.hr.payroll.internal.PayrollRunRepository
import com.hr.shared.api.NotFoundException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jwt.Jwt
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Employee Payslip Controller REST Endpoints Unit Tests")
class EmployeePayslipControllerTest {

    private val payrollResultRepository = mockk<PayrollResultRepository>(relaxed = true)
    private val payrollResultLineRepository = mockk<PayrollResultLineRepository>(relaxed = true)
    private val payrollRunRepository = mockk<PayrollRunRepository>(relaxed = true)
    private val payPeriodRepository = mockk<PayPeriodRepository>(relaxed = true)

    private val controller = EmployeePayslipController(
        payrollResultRepository = payrollResultRepository,
        payrollResultLineRepository = payrollResultLineRepository,
        payrollRunRepository = payrollRunRepository,
        payPeriodRepository = payPeriodRepository,
    )

    private val employeeId = UUID.randomUUID()
    private val tenantId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    private val runId = UUID.randomUUID()
    private val periodId = UUID.randomUUID()

    private val samplePayPeriod = PayPeriod(
        payGroupId = UUID.randomUUID(),
        code = "2026-M03",
        startDate = LocalDate.of(2026, 3, 1),
        endDate = LocalDate.of(2026, 3, 31),
        paymentDate = LocalDate.of(2026, 3, 25),
        status = PayPeriodStatus.CLOSED,
    )

    private val sampleRun = PayrollRun(
        payGroupId = samplePayPeriod.payGroupId,
        payPeriodId = periodId,
        runNumber = 1,
        status = PayrollRunStatus.COMMITTED,
        totalGross = BigDecimal("155113.64"),
        totalStatutoryEmployee = BigDecimal("12409.09"),
        totalStatutoryEmployer = BigDecimal("23267.05"),
        totalTax = BigDecimal("4113.64"),
        totalNet = BigDecimal("137738.64"),
        totalEmployees = 1,
    )

    private val sampleResult = PayrollResult(
        payrollRunId = runId,
        employeeId = employeeId,
        employeeCode = "LK010",
        employeeName = "Kasun Mendis",
        currency = "LKR",
        basicSalary = BigDecimal("143181.82"),
        grossPay = BigDecimal("155113.64"),
        totalStatutoryEmployee = BigDecimal("12409.09"),
        totalStatutoryEmployer = BigDecimal("23267.05"),
        taxWithheld = BigDecimal("4113.64"),
        totalVoluntaryDeductions = BigDecimal("852.27"),
        netPay = BigDecimal("137738.64"),
        paymentStatus = "PAID",
    )

    private fun createJwt(): Jwt {
        return Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .claim("sub", userId.toString())
            .claim("tenant_id", tenantId.toString())
            .claim("employee_id", employeeId.toString())
            .claim("roles", listOf("EMPLOYEE"))
            .claim("permissions", listOf("payroll:view"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    @BeforeEach
    fun setUp() {
        every { payrollRunRepository.findById(runId) } returns Optional.of(sampleRun)
        every { payPeriodRepository.findById(periodId) } returns Optional.of(samplePayPeriod)
    }

    @Test
    fun `getMyPayslips returns employee's payslip history`() {
        val jwt = createJwt()
        every { payrollResultRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId) } returns listOf(sampleResult)

        val response = controller.getMyPayslips(jwt)

        assertThat(response.payslips).hasSize(1)
        val payslip = response.payslips.first()
        assertThat(payslip.periodCode).isEqualTo("2026-M03")
        assertThat(payslip.periodName).isEqualTo("March 2026")
        assertThat(payslip.currency).isEqualTo("LKR")
        assertThat(payslip.grossPay).isEqualTo(155113.64)
        assertThat(payslip.netPay).isEqualTo(137738.64)
        assertThat(payslip.paymentStatus).isEqualTo("PAID")
    }

    @Test
    fun `getPayslipDetails returns itemized lines with calculation traces`() {
        val jwt = createJwt()
        val payslipId = sampleResult.id

        every { payrollResultRepository.findByIdAndEmployeeId(payslipId, employeeId) } returns Optional.of(sampleResult)

        val lines = listOf(
            PayrollResultLine(
                payrollResultId = payslipId,
                lineCategory = LineCategory.EARNING,
                itemCode = "BASIC",
                itemName = "Basic Salary",
                amount = BigDecimal("143181.82"),
                isStatutory = false,
                calculationTrace = "150,000.00 - LOP (6818.18 = 150000.00 × 1.00/22.00 days)",
            ),
            PayrollResultLine(
                payrollResultId = payslipId,
                lineCategory = LineCategory.EARNING,
                itemCode = "OT_NORMAL",
                itemName = "Normal Overtime (1.5x)",
                amount = BigDecimal("5113.64"),
                isStatutory = false,
                calculationTrace = "4.0 hrs × 852.27 × 1.50x rate",
            ),
            PayrollResultLine(
                payrollResultId = payslipId,
                lineCategory = LineCategory.STATUTORY_DEDUCTION,
                itemCode = "EPF_EE",
                itemName = "EPF Employee (8%)",
                amount = BigDecimal("12409.09"),
                isStatutory = true,
                calculationTrace = "155,113.64 × 8% Employee EPF deduction",
            ),
            PayrollResultLine(
                payrollResultId = payslipId,
                lineCategory = LineCategory.TAX,
                itemCode = "TAX_WITHHOLDING",
                itemName = "APIT Withheld",
                amount = BigDecimal("4113.64"),
                isStatutory = true,
                calculationTrace = "Taxable 155,113.64 (Exempt 100k, 41,666.67 @ 6% = 2,500, 13,446.97 @ 12% = 1,613.64)",
            ),
            PayrollResultLine(
                payrollResultId = payslipId,
                lineCategory = LineCategory.VOLUNTARY_DEDUCTION,
                itemCode = "LATE_PENALTY",
                itemName = "Lateness Penalty",
                amount = BigDecimal("852.27"),
                isStatutory = false,
                calculationTrace = "1.0 hr × 852.27 deduction for late arrival",
            ),
            PayrollResultLine(
                payrollResultId = payslipId,
                lineCategory = LineCategory.EMPLOYER_CONTRIBUTION,
                itemCode = "EPF_ER",
                itemName = "EPF Employer (12%)",
                amount = BigDecimal("18613.64"),
                isStatutory = true,
                calculationTrace = "155,113.64 × 12% Employer EPF contribution",
            ),
        )

        every { payrollResultLineRepository.findAllByPayrollResultId(payslipId) } returns lines

        val detail = controller.getPayslipDetails(payslipId, jwt)

        assertThat(detail.id).isEqualTo(payslipId)
        assertThat(detail.employeeName).isEqualTo("Kasun Mendis")
        assertThat(detail.grossPay).isEqualTo(155113.64)
        assertThat(detail.netPay).isEqualTo(137738.64)
        assertThat(detail.bankName).isEqualTo("Commercial Bank of Ceylon")
        assertThat(detail.bankAccountNumberMasked).isEqualTo("••••5678")

        assertThat(detail.earnings).hasSize(2)
        val otLine = detail.earnings.find { it.itemCode == "OT_NORMAL" }
        assertThat(otLine).isNotNull
        assertThat(otLine!!.hours).isEqualTo(4.0)
        assertThat(otLine.rate).isEqualTo(1.5)
        assertThat(otLine.calculationTrace).contains("4.0 hrs × 852.27 × 1.50x rate")

        assertThat(detail.deductions).hasSize(2) // EPF_EE and LATE_PENALTY
        assertThat(detail.taxes).hasSize(1)
        assertThat(detail.taxes.first().calculationTrace).contains("Taxable 155,113.64")

        assertThat(detail.employerContributions).hasSize(1)
        assertThat(detail.employerContributions.first().rate).isEqualTo(0.12)
    }

    @Test
    fun `getPayslipComparison compares with immediate prior period`() {
        val jwt = createJwt()
        val currentId = sampleResult.id
        val priorRunId = UUID.randomUUID()
        val priorPeriodId = UUID.randomUUID()
        val priorPeriod = PayPeriod(
            payGroupId = samplePayPeriod.payGroupId,
            code = "2026-M02",
            startDate = LocalDate.of(2026, 2, 1),
            endDate = LocalDate.of(2026, 2, 28),
            paymentDate = LocalDate.of(2026, 2, 25),
            status = PayPeriodStatus.CLOSED,
        )
        val priorRun = PayrollRun(
            payGroupId = samplePayPeriod.payGroupId,
            payPeriodId = priorPeriodId,
            runNumber = 1,
            status = PayrollRunStatus.COMMITTED,
            totalGross = BigDecimal("150000.00"),
            totalStatutoryEmployee = BigDecimal("12000.00"),
            totalStatutoryEmployer = BigDecimal("22500.00"),
            totalTax = BigDecimal("3500.00"),
            totalNet = BigDecimal("134500.00"),
            totalEmployees = 1,
        )
        val priorResult = PayrollResult(
            payrollRunId = priorRunId,
            employeeId = employeeId,
            employeeCode = "LK010",
            employeeName = "Kasun Mendis",
            currency = "LKR",
            basicSalary = BigDecimal("150000.00"),
            grossPay = BigDecimal("150000.00"),
            totalStatutoryEmployee = BigDecimal("12000.00"),
            totalStatutoryEmployer = BigDecimal("22500.00"),
            taxWithheld = BigDecimal("3500.00"),
            totalVoluntaryDeductions = BigDecimal.ZERO,
            netPay = BigDecimal("134500.00"),
            paymentStatus = "PAID",
        )

        every { payrollRunRepository.findById(priorRunId) } returns Optional.of(priorRun)
        every { payPeriodRepository.findById(priorPeriodId) } returns Optional.of(priorPeriod)
        every { payrollResultRepository.findByIdAndEmployeeId(currentId, employeeId) } returns Optional.of(sampleResult)
        every { payrollResultRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId) } returns listOf(sampleResult, priorResult)

        val comp = controller.getPayslipComparison(currentId, jwt)

        assertThat(comp.currentPeriodCode).isEqualTo("2026-M03")
        assertThat(comp.priorPeriodCode).isEqualTo("2026-M02")
        assertThat(comp.currentGross).isEqualTo(155113.64)
        assertThat(comp.priorGross).isEqualTo(150000.00)
        assertThat(comp.grossVariance).isEqualTo(5113.64)
        assertThat(comp.grossVariancePercent).isEqualTo(3.41)
        assertThat(comp.currentNet).isEqualTo(137738.64)
        assertThat(comp.priorNet).isEqualTo(134500.00)
        assertThat(comp.netVariance).isEqualTo(3238.64)
    }

    @Test
    fun `getPayslipDetails throws NotFoundException when payslip does not exist`() {
        val jwt = createJwt()
        val unknownId = UUID.randomUUID()
        every { payrollResultRepository.findByIdAndEmployeeId(unknownId, employeeId) } returns Optional.empty()

        assertThrows<NotFoundException> {
            controller.getPayslipDetails(unknownId, jwt)
        }
    }
}
