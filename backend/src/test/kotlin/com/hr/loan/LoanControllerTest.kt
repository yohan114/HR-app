package com.hr.loan

import com.hr.loan.internal.LoanController
import com.hr.loan.internal.LoanService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Loan Controller Unit Tests")
class LoanControllerTest {

    private val loanService = mockk<LoanService>(relaxed = true)
    private val controller = LoanController(loanService)

    @Test
    fun `getLoanTypes returns list from service`() {
        val sampleType = LoanTypeItemResponse(
            id = UUID.randomUUID(),
            code = "FESTIVAL_ADVANCE",
            name = "Festival Advance",
            interestMethod = InterestMethod.ZERO_INTEREST,
            annualInterestRate = BigDecimal.ZERO,
            minTenureMonths = 1,
            maxTenureMonths = 10,
            minPrincipal = BigDecimal("5000.00"),
            maxPrincipal = BigDecimal("50000.00"),
            salaryMultipleLimit = BigDecimal("1.00"),
            minServiceMonths = 3,
            maxActiveLoans = 1,
            description = "Festival advance",
        )

        every { loanService.getLoanTypes() } returns LoanTypesResponse(listOf(sampleType))

        val response = controller.getLoanTypes()
        assertThat(response.types).hasSize(1)
        assertThat(response.types.first().code).isEqualTo("FESTIVAL_ADVANCE")
    }

    @Test
    fun `getMyLoans returns loans for employee`() {
        val sampleLoan = EmployeeLoanItemResponse(
            id = UUID.randomUUID(),
            loanCode = "LN-2026-001",
            loanTypeId = UUID.randomUUID(),
            loanTypeCode = "FESTIVAL_ADVANCE",
            loanTypeName = "Festival Advance",
            principalAmount = BigDecimal("30000.00"),
            interestMethod = InterestMethod.ZERO_INTEREST,
            annualInterestRate = BigDecimal.ZERO,
            tenureMonths = 6,
            monthlyInstallment = BigDecimal("5000.00"),
            totalInterest = BigDecimal.ZERO,
            totalRepayable = BigDecimal("30000.00"),
            totalRepaid = BigDecimal("10000.00"),
            remainingBalance = BigDecimal("20000.00"),
            reason = "Festival shopping",
            status = LoanStatus.ACTIVE,
            disbursedDate = LocalDate.now().minusMonths(2),
        )

        every { loanService.getMyLoans(any()) } returns EmployeeLoansResponse(
            totalOutstandingBalance = BigDecimal("20000.00"),
            activeLoansCount = 1,
            loans = listOf(sampleLoan),
        )

        val response = controller.getMyLoans(null)
        assertThat(response.loans).hasSize(1)
        assertThat(response.loans.first().loanCode).isEqualTo("LN-2026-001")
        assertThat(response.totalOutstandingBalance).isEqualByComparingTo(BigDecimal("20000.00"))
    }

    @Test
    fun `requestLoanSettlement settles active loan`() {
        val loanId = UUID.randomUUID()
        val settlementResponse = LoanSettlementResponse(
            loanId = loanId,
            settledDate = LocalDate.now(),
            settlementAmountPaid = BigDecimal("20000.00"),
            remainingBalance = BigDecimal.ZERO,
            status = "SETTLED",
        )

        every { loanService.settleLoan(any(), loanId, any()) } returns settlementResponse

        val result = controller.requestLoanSettlement(loanId, null, null, null)
        assertThat(result.loanId).isEqualTo(loanId)
        assertThat(result.settlementAmountPaid).isEqualByComparingTo(BigDecimal("20000.00"))
        assertThat(result.status).isEqualTo("SETTLED")
    }
}
