package com.hr.loan

import com.hr.loan.internal.LoanScheduleCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("Loan Schedule Calculator Unit Tests")
class LoanScheduleCalculatorTest {

    private val calculator = LoanScheduleCalculator()
    private val startDate = LocalDate.of(2026, 3, 1)

    @Test
    fun `calculates zero interest loan schedule with exact penny accuracy`() {
        val principal = BigDecimal("10000.00")
        val tenureMonths = 6

        val result = calculator.calculateSchedule(
            principal = principal,
            annualInterestRate = BigDecimal.ZERO,
            tenureMonths = tenureMonths,
            method = InterestMethod.ZERO_INTEREST,
            startDate = startDate,
        )

        assertThat(result.totalInterest).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.totalRepayable).isEqualByComparingTo(principal)
        assertThat(result.schedule).hasSize(tenureMonths)

        val principalSum = result.schedule.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.principalAmount) }
        assertThat(principalSum).isEqualByComparingTo(principal)

        val interestSum = result.schedule.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.interestAmount) }
        assertThat(interestSum).isEqualByComparingTo(BigDecimal.ZERO)

        // Installment sequence check
        assertThat(result.schedule.first().installmentNumber).isEqualTo(1)
        assertThat(result.schedule.first().dueDate).isEqualTo(startDate.plusMonths(1))
        assertThat(result.schedule.last().installmentNumber).isEqualTo(6)
        assertThat(result.schedule.last().remainingPrincipal).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `calculates flat rate interest loan schedule correctly`() {
        val principal = BigDecimal("50000.00")
        val rate = BigDecimal("6.00") // 6% annual
        val tenureMonths = 12 // 1 year

        // Total interest = 50000 * 0.06 * 1 = 3000.00
        val result = calculator.calculateSchedule(
            principal = principal,
            annualInterestRate = rate,
            tenureMonths = tenureMonths,
            method = InterestMethod.FLAT_RATE,
            startDate = startDate,
        )

        assertThat(result.totalInterest).isEqualByComparingTo(BigDecimal("3000.00"))
        assertThat(result.totalRepayable).isEqualByComparingTo(BigDecimal("53000.00"))
        assertThat(result.schedule).hasSize(tenureMonths)

        val principalSum = result.schedule.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.principalAmount) }
        assertThat(principalSum).isEqualByComparingTo(principal)

        val interestSum = result.schedule.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.interestAmount) }
        assertThat(interestSum).isEqualByComparingTo(BigDecimal("3000.00"))

        val totalInstallmentSum = result.schedule.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.totalInstallment) }
        assertThat(totalInstallmentSum).isEqualByComparingTo(BigDecimal("53000.00"))

        assertThat(result.schedule.last().remainingPrincipal).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `calculates reducing balance EMI amortization schedule correctly`() {
        val principal = BigDecimal("100000.00")
        val rate = BigDecimal("12.00") // 12% annual = 1% monthly
        val tenureMonths = 12

        val result = calculator.calculateSchedule(
            principal = principal,
            annualInterestRate = rate,
            tenureMonths = tenureMonths,
            method = InterestMethod.REDUCING_BALANCE,
            startDate = startDate,
        )

        assertThat(result.schedule).hasSize(tenureMonths)

        // For reducing balance, interest decreases while principal paid increases over time
        val firstMonth = result.schedule.first()
        val lastMonth = result.schedule.last()

        assertThat(firstMonth.interestAmount).isGreaterThan(lastMonth.interestAmount)
        assertThat(firstMonth.principalAmount).isLessThan(lastMonth.principalAmount)

        // Sum of all principal parts must equal initial principal
        val principalSum = result.schedule.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.principalAmount) }
        assertThat(principalSum).isEqualByComparingTo(principal)

        assertThat(lastMonth.remainingPrincipal).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `rejects invalid calculation inputs`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculateSchedule(
                principal = BigDecimal("-100.00"),
                annualInterestRate = BigDecimal("5.00"),
                tenureMonths = 12,
                method = InterestMethod.ZERO_INTEREST,
            )
        }

        assertThrows<IllegalArgumentException> {
            calculator.calculateSchedule(
                principal = BigDecimal("10000.00"),
                annualInterestRate = BigDecimal("5.00"),
                tenureMonths = 0,
                method = InterestMethod.ZERO_INTEREST,
            )
        }
    }
}
