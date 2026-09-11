package com.hr.app.ui.payroll

import com.hr.app.data.payroll.PayslipRepository
import com.hr.client.model.PayslipComparisonResponse
import com.hr.client.model.PayslipDetailResponse
import com.hr.client.model.PayslipLineItem
import com.hr.client.model.PayslipSummaryItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PayslipViewModelTest {

    private val repository = mockk<PayslipRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val sampleId1 = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val sampleId2 = UUID.fromString("00000000-0000-0000-0000-000000000202")

    private val samplePayslip1 = PayslipSummaryItem(
        id = sampleId1,
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

    private val samplePayslip2 = PayslipSummaryItem(
        id = sampleId2,
        payPeriodId = UUID.randomUUID(),
        periodCode = "2026-M02",
        periodName = "February 2026",
        startDate = LocalDate.of(2026, 2, 1),
        endDate = LocalDate.of(2026, 2, 28),
        paymentDate = LocalDate.of(2026, 2, 25),
        currency = "LKR",
        basicSalary = BigDecimal("150000.00"),
        grossPay = BigDecimal("150000.00"),
        totalDeductions = BigDecimal("15500.00"),
        netPay = BigDecimal("134500.00"),
        paymentStatus = PayslipSummaryItem.PaymentStatus.PAID,
    )

    private val sampleDetail = PayslipDetailResponse(
        id = sampleId1,
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
        earnings = emptyList(),
        deductions = emptyList(),
        taxes = emptyList(),
        employerContributions = emptyList(),
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
        lines = emptyList(),
    )

    private val payslipsFlow = MutableStateFlow(listOf(samplePayslip1, samplePayslip2))
    private val activePayslipFlow = MutableStateFlow<PayslipDetailResponse?>(sampleDetail)
    private val comparisonFlow = MutableStateFlow<PayslipComparisonResponse?>(sampleComparison)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.payslips } returns payslipsFlow
        coEvery { repository.activePayslip } returns activePayslipFlow
        coEvery { repository.comparison } returns comparisonFlow
        coEvery { repository.refreshPayslips() } returns Result.success(listOf(samplePayslip1, samplePayslip2))
        coEvery { repository.loadPayslipDetail(any()) } returns Result.success(sampleDetail)
        coEvery { repository.loadPayslipComparison(any()) } returns Result.success(sampleComparison)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads payslips and selects first period`() = runTest(testDispatcher) {
        val viewModel = PayslipViewModel(repository)
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.payslips.size)
        assertEquals(sampleId1, state.selectedPayslipId)
        assertNotNull(state.selectedPayslipDetail)
        assertEquals("Kasun Mendis", state.selectedPayslipDetail?.employeeName)
        assertNotNull(state.comparison)
    }

    @Test
    fun `selectPayslip updates selected period and reloads detail`() = runTest(testDispatcher) {
        val viewModel = PayslipViewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.selectPayslip(sampleId2)
        testScheduler.advanceUntilIdle()

        assertEquals(sampleId2, viewModel.state.value.selectedPayslipId)
    }

    @Test
    fun `openExplainer and closeExplainer toggles explainer state`() = runTest(testDispatcher) {
        val viewModel = PayslipViewModel(repository)
        testScheduler.advanceUntilIdle()

        val line = PayslipLineItem(
            id = "line-ot",
            category = PayslipLineItem.Category.EARNING,
            itemCode = "OT_NORMAL",
            itemName = "Normal Overtime (1.5x)",
            amount = BigDecimal("5113.64"),
            rate = BigDecimal("1.5"),
            hours = BigDecimal("4.0"),
            isStatutory = false,
            calculationTrace = "4.0 hrs × 852.27 × 1.50x rate",
        )

        viewModel.openExplainer(line)
        assertTrue(viewModel.state.value.isExplainerOpen)
        assertEquals("OT_NORMAL", viewModel.state.value.selectedExplainerLine?.itemCode)

        viewModel.closeExplainer()
        assertFalse(viewModel.state.value.isExplainerOpen)
        assertNull(viewModel.state.value.selectedExplainerLine)
    }

    @Test
    fun `openComparison and closeComparison toggles comparison dialog`() = runTest(testDispatcher) {
        val viewModel = PayslipViewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.openComparison()
        assertTrue(viewModel.state.value.isComparisonOpen)

        viewModel.closeComparison()
        assertFalse(viewModel.state.value.isComparisonOpen)
    }

    @Test
    fun `toggleMask toggles privacy concealing state`() = runTest(testDispatcher) {
        val viewModel = PayslipViewModel(repository)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isMasked)
        viewModel.toggleMask()
        assertTrue(viewModel.state.value.isMasked)
        viewModel.toggleMask()
        assertFalse(viewModel.state.value.isMasked)
    }
}
