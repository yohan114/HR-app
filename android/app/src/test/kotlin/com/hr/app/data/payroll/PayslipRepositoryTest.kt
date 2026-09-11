package com.hr.app.data.payroll

import com.hr.client.api.PayrollApi
import com.hr.client.model.PayslipComparisonLine
import com.hr.client.model.PayslipComparisonResponse
import com.hr.client.model.PayslipDetailResponse
import com.hr.client.model.PayslipLineItem
import com.hr.client.model.PayslipSummaryItem
import com.hr.client.model.PayslipsResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class PayslipRepositoryTest {

    private val payrollApi = mockk<PayrollApi>()
    private lateinit var repository: PayslipRepository

    private val sampleId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val sampleSummary = PayslipSummaryItem(
        id = sampleId,
        payPeriodId = UUID.randomUUID(),
        periodCode = "2026-M03",
        periodName = "March 2026",
        startDate = LocalDate.of(2026, 3, 1),
        endDate = LocalDate.of(2026, 3, 31),
        paymentDate = LocalDate.of(2026, 3, 25),
        currency = "LKR",
        basicSalary = BigDecimal("143181.82"),
        grossPay = BigDecimal("155113.64"),
        totalDeductions = BigDecimal("17375.00"),
        netPay = BigDecimal("137738.64"),
        paymentStatus = PayslipSummaryItem.PaymentStatus.PAID,
    )

    private val sampleDetail = PayslipDetailResponse(
        id = sampleId,
        employeeId = UUID.randomUUID(),
        employeeCode = "LK010",
        employeeName = "Kasun Mendis",
        department = "Engineering",
        designation = "Senior Systems Engineer",
        payPeriodCode = "2026-M03",
        payPeriodName = "March 2026",
        startDate = LocalDate.of(2026, 3, 1),
        endDate = LocalDate.of(2026, 3, 31),
        paymentDate = LocalDate.of(2026, 3, 25),
        currency = "LKR",
        bankName = "Commercial Bank",
        bankAccountNumberMasked = "••••5678",
        basicSalary = BigDecimal("143181.82"),
        grossPay = BigDecimal("155113.64"),
        totalStatutoryEmployee = BigDecimal("12409.09"),
        totalStatutoryEmployer = BigDecimal("23267.05"),
        taxWithheld = BigDecimal("4113.64"),
        totalVoluntaryDeductions = BigDecimal("852.27"),
        totalDeductions = BigDecimal("17375.00"),
        netPay = BigDecimal("137738.64"),
        paymentStatus = "PAID",
        earnings = listOf(
            PayslipLineItem(
                id = "line-1",
                category = PayslipLineItem.Category.EARNING,
                itemCode = "BASIC",
                itemName = "Basic Salary",
                amount = BigDecimal("143181.82"),
                rate = null,
                hours = BigDecimal("168.0"),
                isStatutory = false,
                calculationTrace = "150,000.00 - LOP (6,818.18)",
            )
        ),
        deductions = listOf(
            PayslipLineItem(
                id = "line-2",
                category = PayslipLineItem.Category.STATUTORY_DEDUCTION,
                itemCode = "EPF_EE",
                itemName = "EPF Employee (8%)",
                amount = BigDecimal("12409.09"),
                rate = BigDecimal("0.08"),
                hours = null,
                isStatutory = true,
                calculationTrace = "155,113.64 × 8.00%",
            )
        ),
        taxes = listOf(
            PayslipLineItem(
                id = "line-3",
                category = PayslipLineItem.Category.TAX,
                itemCode = "TAX_WITHHOLDING",
                itemName = "APIT Withheld",
                amount = BigDecimal("4113.64"),
                rate = null,
                hours = null,
                isStatutory = true,
                calculationTrace = "Taxable 155,113.64",
            )
        ),
        employerContributions = listOf(
            PayslipLineItem(
                id = "line-4",
                category = PayslipLineItem.Category.EMPLOYER_CONTRIBUTION,
                itemCode = "EPF_ER",
                itemName = "EPF Employer (12%)",
                amount = BigDecimal("18613.64"),
                rate = BigDecimal("0.12"),
                hours = null,
                isStatutory = true,
                calculationTrace = "155,113.64 × 12.00%",
            )
        ),
    )

    private val sampleComparison = PayslipComparisonResponse(
        currentPeriodCode = "2026-M03",
        priorPeriodCode = "2026-M02",
        currentGross = BigDecimal("155113.64"),
        priorGross = BigDecimal("150000.00"),
        grossVariance = BigDecimal("5113.64"),
        grossVariancePercent = BigDecimal("3.41"),
        currentNet = BigDecimal("137738.64"),
        priorNet = BigDecimal("134500.00"),
        netVariance = BigDecimal("3238.64"),
        netVariancePercent = BigDecimal("2.41"),
        lines = listOf(
            PayslipComparisonLine(
                itemCode = "BASIC",
                itemName = "Basic Salary",
                category = "EARNING",
                currentAmount = BigDecimal("143181.82"),
                priorAmount = BigDecimal("150000.00"),
                varianceAmount = BigDecimal("-6818.18"),
                variancePercent = BigDecimal("-4.55"),
            )
        ),
    )

    @Before
    fun setUp() {
        coEvery { payrollApi.getMyPayslips() } returns Response.success(
            PayslipsResponse(payslips = listOf(sampleSummary))
        )
        coEvery { payrollApi.getPayslipDetails(sampleId.toString()) } returns Response.success(sampleDetail)
        coEvery { payrollApi.getPayslipComparison(sampleId.toString()) } returns Response.success(sampleComparison)

        repository = PayslipRepository(payrollApi = payrollApi)
    }

    @Test
    fun `refreshPayslips populates payslips StateFlow from API`() = runTest {
        val result = repository.refreshPayslips()

        assertTrue(result.isSuccess)
        val list = repository.payslips.value
        assertEquals(1, list.size)
        assertEquals("2026-M03", list.first().periodCode)
        assertEquals(137738.64, list.first().netPay.toDouble(), 0.001)
    }

    @Test
    fun `loadPayslipDetail returns itemized breakdown and updates activePayslip`() = runTest {
        val result = repository.loadPayslipDetail(sampleId)

        assertTrue(result.isSuccess)
        val detail = repository.activePayslip.value
        assertNotNull(detail)
        assertEquals("Kasun Mendis", detail?.employeeName)
        assertEquals(1, detail?.earnings?.size)
        assertEquals(1, detail?.deductions?.size)
        assertEquals("155,113.64 × 8.00%", detail?.deductions?.first()?.calculationTrace)
    }

    @Test
    fun `loadPayslipComparison returns MoM differences and updates comparison`() = runTest {
        val result = repository.loadPayslipComparison(sampleId)

        assertTrue(result.isSuccess)
        val comp = repository.comparison.value
        assertNotNull(comp)
        assertEquals("2026-M03", comp?.currentPeriodCode)
        assertEquals("2026-M02", comp?.priorPeriodCode)
        assertEquals(5113.64, comp?.grossVariance?.toDouble() ?: 0.0, 0.001)
        assertEquals(3238.64, comp?.netVariance?.toDouble() ?: 0.0, 0.001)
    }

    @Test
    fun `offline fallback provides deterministic March and February 2026 data when API fails`() = runTest {
        coEvery { payrollApi.getMyPayslips() } throws RuntimeException("Network unreachable")

        val result = repository.refreshPayslips()
        assertTrue(result.isFailure)

        // Verifies offline fallback fixtures were loaded
        val fallbackList = repository.payslips.value
        assertEquals(3, fallbackList.size)
        assertEquals("March 2026", fallbackList[0].periodName)
        assertEquals("February 2026", fallbackList[1].periodName)
        assertEquals("January 2026", fallbackList[2].periodName)
    }
}
