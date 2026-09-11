package com.hr.app.ui.loan

import com.hr.app.data.loan.LoanRepository
import com.hr.client.model.EmployeeLoanDetailResponse
import com.hr.client.model.EmployeeLoanItem
import com.hr.client.model.LoanEligibilityResponse
import com.hr.client.model.LoanRepaymentScheduleItem
import com.hr.client.model.LoanSettlementResponse
import com.hr.client.model.LoanTypeItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class LoanViewModelTest {

    private val repository = mockk<LoanRepository>(relaxed = true)

    private val loanTypesFlow = MutableStateFlow<List<LoanTypeItem>>(emptyList())
    private val loansFlow = MutableStateFlow<List<EmployeeLoanItem>>(emptyList())
    private val selectedLoanDetailFlow = MutableStateFlow<EmployeeLoanDetailResponse?>(null)
    private val eligibilityResultFlow = MutableStateFlow<LoanEligibilityResponse?>(null)
    private val pendingOutboxCountFlow = MutableStateFlow(0)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LoanViewModel

    private val sampleType = LoanTypeItem(
        id = UUID.randomUUID(),
        code = "FESTIVAL_ADVANCE",
        name = "Festival Advance",
        description = "Festival loan",
        interestMethod = LoanTypeItem.InterestMethod.ZERO_INTEREST,
        annualInterestRate = BigDecimal.ZERO,
        minTenureMonths = 1,
        maxTenureMonths = 10,
        minPrincipal = BigDecimal("5000.00"),
        maxPrincipal = BigDecimal("50000.00"),
        salaryMultipleLimit = BigDecimal("1.00"),
        minServiceMonths = 3,
        maxActiveLoans = 1,
    )

    private val sampleLoan = EmployeeLoanItem(
        id = UUID.randomUUID(),
        loanCode = "LN-2026-0042",
        loanTypeId = sampleType.id,
        loanTypeCode = sampleType.code,
        loanTypeName = sampleType.name,
        principalAmount = BigDecimal("30000.00"),
        interestMethod = EmployeeLoanItem.InterestMethod.ZERO_INTEREST,
        annualInterestRate = BigDecimal.ZERO,
        tenureMonths = 6,
        monthlyInstallment = BigDecimal("5000.00"),
        totalInterest = BigDecimal.ZERO,
        totalRepayable = BigDecimal("30000.00"),
        totalRepaid = BigDecimal("10000.00"),
        remainingBalance = BigDecimal("20000.00"),
        reason = "Festival advance",
        status = EmployeeLoanItem.Status.ACTIVE,
        disbursedDate = LocalDate.now().minusMonths(2),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { repository.loanTypes } returns loanTypesFlow
        every { repository.loans } returns loansFlow
        every { repository.selectedLoanDetail } returns selectedLoanDetailFlow
        every { repository.eligibilityResult } returns eligibilityResultFlow
        every { repository.pendingOutboxCount } returns pendingOutboxCountFlow

        coEvery { repository.refreshLoanTypes() } returns Result.success(listOf(sampleType))
        coEvery { repository.refreshLoans() } returns Result.success(listOf(sampleLoan))

        loanTypesFlow.value = listOf(sampleType)
        loansFlow.value = listOf(sampleLoan)

        viewModel = LoanViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads types, loans, and defaults`() = runTest {
        advanceUntilIdle()
        val state = viewModel.state.value

        assertEquals(1, state.loanTypes.size)
        assertEquals(1, state.loans.size)
        assertEquals(sampleType.id, state.applyLoanTypeId)
        assertEquals(LoanTab.MY_LOANS, state.selectedTab)
    }

    @Test
    fun `tab switching updates selectedTab and triggers eligibility on apply tab`() = runTest {
        viewModel.setTab(LoanTab.APPLY_LOAN)
        advanceUntilIdle()

        assertEquals(LoanTab.APPLY_LOAN, viewModel.state.value.selectedTab)
        coVerify { repository.checkEligibility(sampleType.id, any(), any()) }
    }

    @Test
    fun `openLoanSchedule opens modal and fetches detail`() = runTest {
        val detail = EmployeeLoanDetailResponse(loan = sampleLoan, schedule = emptyList())
        selectedLoanDetailFlow.value = detail

        viewModel.openLoanSchedule(sampleLoan)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isScheduleModalOpen)
        assertEquals(sampleLoan.id, viewModel.state.value.selectedLoan?.id)
        coVerify { repository.fetchLoanDetails(sampleLoan.id.toString()) }
    }

    @Test
    fun `submitLoanApplication validates and invokes repository`() = runTest {
        val detail = EmployeeLoanDetailResponse(loan = sampleLoan, schedule = emptyList())
        coEvery { repository.submitLoanApplication(any(), any(), any(), any()) } returns Result.success(detail)

        viewModel.setApplyPrincipal("30000")
        viewModel.setApplyTenureMonths(6)
        viewModel.setApplyReason("Medical aid")

        viewModel.submitLoanApplication()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSubmittingApplication)
        assertEquals(LoanTab.MY_LOANS, viewModel.state.value.selectedTab)
        assertNotNull(viewModel.state.value.actionMessage)
    }

    @Test
    fun `settlement dialog flow requests payoff and marks complete`() = runTest {
        val settleRes = LoanSettlementResponse(
            loanId = sampleLoan.id,
            settledDate = LocalDate.now(),
            settlementAmountPaid = BigDecimal("20000.00"),
            remainingBalance = BigDecimal.ZERO,
            status = "SETTLED",
        )
        coEvery { repository.requestSettlement(any(), any()) } returns Result.success(settleRes)

        viewModel.openSettlementDialog(sampleLoan)
        assertTrue(viewModel.state.value.isSettlementDialogOpen)
        assertEquals(sampleLoan.id, viewModel.state.value.loanToSettle?.id)

        viewModel.setSettlementNotes("Bonus deduction")
        viewModel.confirmSettlement()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSettlementDialogOpen)
        assertNull(viewModel.state.value.loanToSettle)
        assertEquals("Loan settled successfully! Outstanding principal paid: LKR 20000.00", viewModel.state.value.actionMessage)
    }
}
