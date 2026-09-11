package com.hr.loan.internal

import com.hr.loan.AmortizationCalculationResult
import com.hr.loan.AmortizationScheduleLine
import com.hr.loan.InterestMethod
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.pow

@Component
class LoanScheduleCalculator {

    /**
     * Computes the amortization repayment schedule and installment breakdown.
     */
    fun calculateSchedule(
        principal: BigDecimal,
        annualInterestRate: BigDecimal,
        tenureMonths: Int,
        method: InterestMethod,
        startDate: LocalDate = LocalDate.now(),
    ): AmortizationCalculationResult {
        require(principal > BigDecimal.ZERO) { "Principal amount must be positive: $principal" }
        require(tenureMonths > 0) { "Tenure months must be positive: $tenureMonths" }
        require(annualInterestRate >= BigDecimal.ZERO) { "Annual interest rate cannot be negative: $annualInterestRate" }

        return when (method) {
            InterestMethod.ZERO_INTEREST -> calculateZeroInterest(principal, tenureMonths, startDate)
            InterestMethod.FLAT_RATE -> calculateFlatRate(principal, annualInterestRate, tenureMonths, startDate)
            InterestMethod.REDUCING_BALANCE -> calculateReducingBalance(principal, annualInterestRate, tenureMonths, startDate)
        }
    }

    private fun calculateZeroInterest(
        principal: BigDecimal,
        tenureMonths: Int,
        startDate: LocalDate,
    ): AmortizationCalculationResult {
        val scaledPrincipal = principal.setScale(2, RoundingMode.HALF_UP)
        val baseInstallment = scaledPrincipal.divide(BigDecimal(tenureMonths), 2, RoundingMode.HALF_UP)

        var accumulatedPrincipal = BigDecimal.ZERO
        var remainingPrincipal = scaledPrincipal
        val lines = mutableListOf<AmortizationScheduleLine>()

        for (i in 1..tenureMonths) {
            val isLast = (i == tenureMonths)
            val instPrincipal = if (isLast) {
                scaledPrincipal.subtract(accumulatedPrincipal).max(BigDecimal.ZERO)
            } else {
                baseInstallment
            }

            accumulatedPrincipal = accumulatedPrincipal.add(instPrincipal)
            remainingPrincipal = remainingPrincipal.subtract(instPrincipal).max(BigDecimal.ZERO)

            lines.add(
                AmortizationScheduleLine(
                    installmentNumber = i,
                    dueDate = startDate.plusMonths(i.toLong()),
                    principalAmount = instPrincipal,
                    interestAmount = BigDecimal.ZERO.setScale(2),
                    totalInstallment = instPrincipal,
                    remainingPrincipal = remainingPrincipal,
                ),
            )
        }

        return AmortizationCalculationResult(
            monthlyInstallment = baseInstallment,
            totalInterest = BigDecimal.ZERO.setScale(2),
            totalRepayable = scaledPrincipal,
            schedule = lines,
        )
    }

    private fun calculateFlatRate(
        principal: BigDecimal,
        annualInterestRate: BigDecimal,
        tenureMonths: Int,
        startDate: LocalDate,
    ): AmortizationCalculationResult {
        val scaledPrincipal = principal.setScale(2, RoundingMode.HALF_UP)

        // totalInterest = principal * (annualInterestRate / 100) * (tenureMonths / 12)
        val tenureYears = BigDecimal(tenureMonths).divide(BigDecimal("12"), 8, RoundingMode.HALF_UP)
        val rateFraction = annualInterestRate.divide(BigDecimal("100"), 8, RoundingMode.HALF_UP)
        val totalInterest = scaledPrincipal.multiply(rateFraction).multiply(tenureYears).setScale(2, RoundingMode.HALF_UP)
        val totalRepayable = scaledPrincipal.add(totalInterest)

        val basePrincipal = scaledPrincipal.divide(BigDecimal(tenureMonths), 2, RoundingMode.HALF_UP)
        val baseInterest = totalInterest.divide(BigDecimal(tenureMonths), 2, RoundingMode.HALF_UP)
        val baseMonthlyInstallment = totalRepayable.divide(BigDecimal(tenureMonths), 2, RoundingMode.HALF_UP)

        var accumulatedPrincipal = BigDecimal.ZERO
        var accumulatedInterest = BigDecimal.ZERO
        var remainingPrincipal = scaledPrincipal
        val lines = mutableListOf<AmortizationScheduleLine>()

        for (i in 1..tenureMonths) {
            val isLast = (i == tenureMonths)
            val instPrincipal = if (isLast) {
                scaledPrincipal.subtract(accumulatedPrincipal).max(BigDecimal.ZERO)
            } else {
                basePrincipal
            }
            val instInterest = if (isLast) {
                totalInterest.subtract(accumulatedInterest).max(BigDecimal.ZERO)
            } else {
                baseInterest
            }
            val instTotal = instPrincipal.add(instInterest)

            accumulatedPrincipal = accumulatedPrincipal.add(instPrincipal)
            accumulatedInterest = accumulatedInterest.add(instInterest)
            remainingPrincipal = remainingPrincipal.subtract(instPrincipal).max(BigDecimal.ZERO)

            lines.add(
                AmortizationScheduleLine(
                    installmentNumber = i,
                    dueDate = startDate.plusMonths(i.toLong()),
                    principalAmount = instPrincipal,
                    interestAmount = instInterest,
                    totalInstallment = instTotal,
                    remainingPrincipal = remainingPrincipal,
                ),
            )
        }

        return AmortizationCalculationResult(
            monthlyInstallment = baseMonthlyInstallment,
            totalInterest = totalInterest,
            totalRepayable = totalRepayable,
            schedule = lines,
        )
    }

    private fun calculateReducingBalance(
        principal: BigDecimal,
        annualInterestRate: BigDecimal,
        tenureMonths: Int,
        startDate: LocalDate,
    ): AmortizationCalculationResult {
        val scaledPrincipal = principal.setScale(2, RoundingMode.HALF_UP)
        if (annualInterestRate.compareTo(BigDecimal.ZERO) == 0) {
            return calculateZeroInterest(scaledPrincipal, tenureMonths, startDate)
        }

        // Monthly interest rate r = (annualInterestRate / 100) / 12
        val rDbl = annualInterestRate.toDouble() / 1200.0
        val factor = (1.0 + rDbl).pow(tenureMonths.toDouble())
        val emiDbl = principal.toDouble() * (rDbl * factor) / (factor - 1.0)
        val targetEmi = BigDecimal.valueOf(emiDbl).setScale(2, RoundingMode.HALF_UP)

        val monthlyRate = annualInterestRate.divide(BigDecimal("1200"), 10, RoundingMode.HALF_UP)

        var currBalance = scaledPrincipal
        var totalInterestAccum = BigDecimal.ZERO
        var totalRepayableAccum = BigDecimal.ZERO
        val lines = mutableListOf<AmortizationScheduleLine>()

        for (i in 1..tenureMonths) {
            val isLast = (i == tenureMonths)

            val instInterest = currBalance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP)

            val instPrincipal = if (isLast) {
                currBalance
            } else {
                val p = targetEmi.subtract(instInterest)
                if (p > currBalance) currBalance else p.max(BigDecimal.ZERO)
            }

            val instTotal = instPrincipal.add(instInterest)
            currBalance = currBalance.subtract(instPrincipal).max(BigDecimal.ZERO)
            totalInterestAccum = totalInterestAccum.add(instInterest)
            totalRepayableAccum = totalRepayableAccum.add(instTotal)

            lines.add(
                AmortizationScheduleLine(
                    installmentNumber = i,
                    dueDate = startDate.plusMonths(i.toLong()),
                    principalAmount = instPrincipal,
                    interestAmount = instInterest,
                    totalInstallment = instTotal,
                    remainingPrincipal = currBalance,
                ),
            )
        }

        return AmortizationCalculationResult(
            monthlyInstallment = targetEmi,
            totalInterest = totalInterestAccum,
            totalRepayable = totalRepayableAccum,
            schedule = lines,
        )
    }
}
