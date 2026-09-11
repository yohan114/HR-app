package com.hr.loan

import com.hr.loan.internal.LoanEligibilityEngine
import com.hr.loan.internal.LoanScheduleCalculator
import com.hr.loan.internal.LoanType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("Loan Eligibility Engine Unit Tests")
class LoanEligibilityEngineTest {

    private val calculator = LoanScheduleCalculator()
    private val engine = LoanEligibilityEngine(calculator)

    private val sampleJoinDate = LocalDate.of(2024, 1, 1)

    private val standardLoanType = LoanType(
        code = "DISTRESS_LOAN",
        name = "Emergency Distress Loan",
        description = "Emergency loan",
        interestMethod = InterestMethod.REDUCING_BALANCE,
        annualInterestRate = BigDecimal("4.50"),
        minTenureMonths = 6,
        maxTenureMonths = 24,
        minPrincipal = BigDecimal("10000.00"),
        maxPrincipal = BigDecimal("200000.00"),
        salaryMultipleLimit = BigDecimal("3.00"),
        minServiceMonths = 6,
        maxActiveLoansPerEmployee = 1,
        isActive = true,
    )

    @Test
    fun `approves eligible employee with valid tenure, amount, and active loans`() {
        val currentDate = LocalDate.of(2026, 3, 1) // ~26 months tenure
        val basicSalary = BigDecimal("50000.00")
        val requestedPrincipal = BigDecimal("100000.00") // 2x salary <= 3x limit
        val tenureMonths = 12

        val response = engine.evaluate(
            joinDate = sampleJoinDate,
            loanType = standardLoanType,
            requestedPrincipal = requestedPrincipal,
            tenureMonths = tenureMonths,
            basicSalary = basicSalary,
            activeLoansCount = 0,
            currentDate = currentDate,
        )

        assertThat(response.eligible).isTrue()
        assertThat(response.reasons).isEmpty()
        assertThat(response.projectedMonthlyInstallment).isGreaterThan(BigDecimal.ZERO)
        assertThat(response.projectedTotalRepayable).isGreaterThan(requestedPrincipal)
        assertThat(response.maxAllowedPrincipal).isEqualByComparingTo(BigDecimal("150000.00")) // 3x 50k
    }

    @Test
    fun `rejects applicant with insufficient service tenure`() {
        val currentDate = LocalDate.of(2026, 3, 1)

        val response = engine.evaluate(
            joinDate = LocalDate.of(2026, 1, 1), // Only 2 months service
            loanType = standardLoanType,
            requestedPrincipal = BigDecimal("50000.00"),
            tenureMonths = 12,
            basicSalary = BigDecimal("50000.00"),
            activeLoansCount = 0,
            currentDate = currentDate,
        )

        assertThat(response.eligible).isFalse()
        assertThat(response.reasons).anyMatch { it.contains("Requires at least 6 months of service") }
    }

    @Test
    fun `rejects applicant who already reached maximum active loans count`() {
        val currentDate = LocalDate.of(2026, 3, 1)

        val response = engine.evaluate(
            joinDate = sampleJoinDate,
            loanType = standardLoanType,
            requestedPrincipal = BigDecimal("50000.00"),
            tenureMonths = 12,
            basicSalary = BigDecimal("50000.00"),
            activeLoansCount = 1, // Already has 1 active loan
            currentDate = currentDate,
        )

        assertThat(response.eligible).isFalse()
        assertThat(response.reasons).anyMatch { it.contains("maximum allowed is 1") }
    }

    @Test
    fun `rejects applicant exceeding salary multiple limit`() {
        val currentDate = LocalDate.of(2026, 3, 1)
        val basicSalary = BigDecimal("30000.00")
        val requestedPrincipal = BigDecimal("150000.00") // 5x salary > 3x limit

        val response = engine.evaluate(
            joinDate = sampleJoinDate,
            loanType = standardLoanType,
            requestedPrincipal = requestedPrincipal,
            tenureMonths = 12,
            basicSalary = basicSalary,
            activeLoansCount = 0,
            currentDate = currentDate,
        )

        assertThat(response.eligible).isFalse()
        assertThat(response.reasons).anyMatch { it.contains("exceeds 3.00x basic salary") }
    }
}
