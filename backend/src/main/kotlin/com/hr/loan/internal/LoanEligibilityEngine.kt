package com.hr.loan.internal

import com.hr.loan.LoanEligibilityResponse
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class LoanEligibilityEngine(
    private val calculator: LoanScheduleCalculator,
) {

    /**
     * Evaluates whether an employee is eligible for a specified loan product and amount.
     */
    fun evaluate(
        joinDate: LocalDate,
        loanType: LoanType,
        requestedPrincipal: BigDecimal,
        tenureMonths: Int,
        basicSalary: BigDecimal,
        activeLoansCount: Int,
        currentDate: LocalDate = LocalDate.now(),
    ): LoanEligibilityResponse {
        val reasons = mutableListOf<String>()

        // 1. Service tenure check
        val serviceMonths = ChronoUnit.MONTHS.between(joinDate, currentDate).toInt()
        if (serviceMonths < loanType.minServiceMonths) {
            reasons.add("Requires at least ${loanType.minServiceMonths} months of service (current tenure: $serviceMonths months)")
        }

        // 2. Active loan count limit
        if (activeLoansCount >= loanType.maxActiveLoansPerEmployee) {
            reasons.add("Employee already has $activeLoansCount active loan(s); maximum allowed is ${loanType.maxActiveLoansPerEmployee}")
        }

        // 3. Principal amount bounds
        if (requestedPrincipal < loanType.minPrincipal || requestedPrincipal > loanType.maxPrincipal) {
            reasons.add("Requested principal ($requestedPrincipal) must be between ${loanType.minPrincipal} and ${loanType.maxPrincipal}")
        }

        // 4. Tenure bounds
        if (tenureMonths < loanType.minTenureMonths || tenureMonths > loanType.maxTenureMonths) {
            reasons.add("Requested tenure ($tenureMonths months) must be between ${loanType.minTenureMonths} and ${loanType.maxTenureMonths} months")
        }

        // 5. Salary multiple limit
        val maxBySalary = basicSalary.multiply(loanType.salaryMultipleLimit).setScale(2, RoundingMode.HALF_UP)
        if (requestedPrincipal > maxBySalary) {
            reasons.add("Requested amount ($requestedPrincipal) exceeds ${loanType.salaryMultipleLimit}x basic salary maximum of $maxBySalary")
        }

        // Maximum allowed principal is capped by both product maximum and salary multiple
        val maxAllowedPrincipal = if (maxBySalary < loanType.maxPrincipal) {
            maxBySalary.max(loanType.minPrincipal)
        } else {
            loanType.maxPrincipal
        }

        // Calculate projected schedule and EMI
        val calculation = runCatching {
            calculator.calculateSchedule(
                principal = requestedPrincipal,
                annualInterestRate = loanType.annualInterestRate,
                tenureMonths = tenureMonths,
                method = loanType.interestMethod,
                startDate = currentDate,
            )
        }.getOrNull()

        val projectedMonthlyInstallment = calculation?.monthlyInstallment ?: BigDecimal.ZERO.setScale(2)
        val projectedTotalInterest = calculation?.totalInterest ?: BigDecimal.ZERO.setScale(2)
        val projectedTotalRepayable = calculation?.totalRepayable ?: requestedPrincipal.setScale(2)

        // 6. Debt-to-income (DTI) installment threshold (max 50% of basic salary)
        val maxAllowedInstallment = basicSalary.multiply(BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP)
        if (projectedMonthlyInstallment > maxAllowedInstallment) {
            reasons.add("Projected installment ($projectedMonthlyInstallment) exceeds 50% monthly basic salary limit ($maxAllowedInstallment)")
        }

        val eligible = reasons.isEmpty()

        return LoanEligibilityResponse(
            eligible = eligible,
            maxAllowedPrincipal = maxAllowedPrincipal,
            projectedMonthlyInstallment = projectedMonthlyInstallment,
            projectedTotalInterest = projectedTotalInterest,
            projectedTotalRepayable = projectedTotalRepayable,
            reasons = reasons,
        )
    }
}
