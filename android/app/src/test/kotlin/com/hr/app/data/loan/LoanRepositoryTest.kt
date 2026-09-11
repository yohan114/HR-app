package com.hr.app.data.loan

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.LoansApi
import com.hr.client.model.EmployeeLoanDetailResponse
import com.hr.client.model.EmployeeLoanItem
import com.hr.client.model.EmployeeLoansResponse
import com.hr.client.model.LoanEligibilityResponse
import com.hr.client.model.LoanRepaymentScheduleItem
import com.hr.client.model.LoanSettlementResponse
import com.hr.client.model.LoanTypeItem
import com.hr.client.model.LoanTypesResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class LoanRepositoryTest {

    private val loansApi = mockk<LoansApi>()
    private val outbox = mockk<Outbox>(relaxed = true)
    private val clock = Clock { 1772870000000L }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val pendingCountFlow = MutableStateFlow(0)
    private lateinit var repository: LoanRepository

    private val sampleType = LoanTypeItem(
        id = UUID.randomUUID(),
        code = "FESTIVAL_ADVANCE",
        name = "Festival Advance",
        description = "Cultural festival loan",
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
        coEvery { outbox.pendingCount } returns pendingCountFlow
        repository = LoanRepository(loansApi, outbox, json, clock)
    }

    @Test
    fun `refreshLoanTypes updates stateFlow on success`() = runTest {
        coEvery { loansApi.getLoanTypes() } returns Response.success(LoanTypesResponse(listOf(sampleType)))

        val result = repository.refreshLoanTypes()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.loanTypes.value.size)
        assertEquals("FESTIVAL_ADVANCE", repository.loanTypes.value.first().code)
    }

    @Test
    fun `refreshLoanTypes falls back to offline default types on network error`() = runTest {
        coEvery { loansApi.getLoanTypes() } throws RuntimeException("Network down")

        val result = repository.refreshLoanTypes()
        assertTrue(result.isFailure)
        assertTrue(repository.loanTypes.value.isNotEmpty())
        assertEquals("FESTIVAL_ADVANCE", repository.loanTypes.value.first().code)
    }

    @Test
    fun `refreshLoans updates stateFlow on success`() = runTest {
        coEvery { loansApi.getMyLoans() } returns Response.success(
            EmployeeLoansResponse(
                totalOutstandingBalance = BigDecimal("20000.00"),
                activeLoansCount = 1,
                loans = listOf(sampleLoan),
            )
        )

        val result = repository.refreshLoans()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.loans.value.size)
        assertEquals("LN-2026-0042", repository.loans.value.first().loanCode)
    }

    @Test
    fun `checkEligibility calculates and updates eligibilityResult`() = runTest {
        val eligibilityResponse = LoanEligibilityResponse(
            eligible = true,
            maxAllowedPrincipal = BigDecimal("100000.00"),
            projectedMonthlyInstallment = BigDecimal("5000.00"),
            projectedTotalInterest = BigDecimal.ZERO,
            projectedTotalRepayable = BigDecimal("30000.00"),
            reasons = emptyList(),
        )

        coEvery { loansApi.checkLoanEligibility(any()) } returns Response.success(eligibilityResponse)

        val result = repository.checkEligibility(sampleType.id, BigDecimal("30000.00"), 6)
        assertTrue(result.isSuccess)
        val stateResult = repository.eligibilityResult.value
        assertNotNull(stateResult)
        assertTrue(stateResult!!.eligible)
        assertEquals(BigDecimal("5000.00"), stateResult.projectedMonthlyInstallment)
    }

    @Test
    fun `submitLoanApplication updates loans list and selectedLoanDetail`() = runTest {
        coEvery { loansApi.submitLoanApplication(any(), any()) } returns Response.success(sampleLoan)

        val result = repository.submitLoanApplication(sampleType.id, BigDecimal("30000.00"), 6, "Festival advance")
        assertTrue(result.isSuccess)
        val detail = result.getOrNull()
        assertNotNull(detail)
        assertEquals(sampleLoan.id, detail!!.loan.id)
        assertEquals(6, detail.schedule.size)
        assertTrue(repository.loans.value.any { it.id == sampleLoan.id })
    }

    @Test
    fun `requestSettlement updates active loan to settled with zero remaining balance`() = runTest {
        coEvery { loansApi.getMyLoans() } returns Response.success(
            EmployeeLoansResponse(
                totalOutstandingBalance = BigDecimal("20000.00"),
                activeLoansCount = 1,
                loans = listOf(sampleLoan),
            )
        )
        repository.refreshLoans()

        val settlementResponse = LoanSettlementResponse(
            loanId = sampleLoan.id,
            settledDate = LocalDate.now(),
            settlementAmountPaid = BigDecimal("20000.00"),
            remainingBalance = BigDecimal.ZERO,
            status = "SETTLED",
        )

        coEvery { loansApi.requestLoanSettlement(sampleLoan.id.toString(), any(), any()) } returns Response.success(settlementResponse)

        val result = repository.requestSettlement(sampleLoan.id.toString(), "Settling early")
        assertTrue(result.isSuccess)

        val updatedLoan = repository.loans.value.find { it.id == sampleLoan.id }
        assertNotNull(updatedLoan)
        assertEquals(EmployeeLoanItem.Status.SETTLED, updatedLoan!!.status)
        assertEquals(BigDecimal.ZERO, updatedLoan.remainingBalance)
    }
}
