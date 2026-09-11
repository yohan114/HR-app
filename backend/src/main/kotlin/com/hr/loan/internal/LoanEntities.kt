package com.hr.loan.internal

import com.hr.loan.InterestMethod
import com.hr.loan.LoanStatus
import com.hr.loan.ScheduleStatus
import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "loan_type")
class LoanType(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false, length = 32)
    var interestMethod: InterestMethod = InterestMethod.ZERO_INTEREST,
    @Column(name = "annual_interest_rate", nullable = false, precision = 5, scale = 2)
    var annualInterestRate: BigDecimal = BigDecimal("0.00"),
    @Column(name = "min_tenure_months", nullable = false)
    var minTenureMonths: Int = 1,
    @Column(name = "max_tenure_months", nullable = false)
    var maxTenureMonths: Int = 12,
    @Column(name = "min_principal", nullable = false, precision = 12, scale = 2)
    var minPrincipal: BigDecimal = BigDecimal("1000.00"),
    @Column(name = "max_principal", nullable = false, precision = 12, scale = 2)
    var maxPrincipal: BigDecimal = BigDecimal("500000.00"),
    @Column(name = "salary_multiple_limit", nullable = false, precision = 4, scale = 2)
    var salaryMultipleLimit: BigDecimal = BigDecimal("3.00"),
    @Column(name = "min_service_months", nullable = false)
    var minServiceMonths: Int = 6,
    @Column(name = "max_active_loans_per_employee", nullable = false)
    var maxActiveLoansPerEmployee: Int = 1,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "employee_loan")
class EmployeeLoan(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "loan_type_id", nullable = false)
    var loanTypeId: UUID,
    @Column(name = "loan_code", nullable = false, length = 32)
    var loanCode: String,
    @Column(name = "principal_amount", nullable = false, precision = 12, scale = 2)
    var principalAmount: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false, length = 32)
    var interestMethod: InterestMethod,
    @Column(name = "annual_interest_rate", nullable = false, precision = 5, scale = 2)
    var annualInterestRate: BigDecimal = BigDecimal("0.00"),
    @Column(name = "tenure_months", nullable = false)
    var tenureMonths: Int,
    @Column(name = "monthly_installment", nullable = false, precision = 12, scale = 2)
    var monthlyInstallment: BigDecimal,
    @Column(name = "total_interest", nullable = false, precision = 12, scale = 2)
    var totalInterest: BigDecimal = BigDecimal("0.00"),
    @Column(name = "total_repayable", nullable = false, precision = 12, scale = 2)
    var totalRepayable: BigDecimal,
    @Column(name = "total_repaid", nullable = false, precision = 12, scale = 2)
    var totalRepaid: BigDecimal = BigDecimal("0.00"),
    @Column(name = "remaining_balance", nullable = false, precision = 12, scale = 2)
    var remainingBalance: BigDecimal,
    @Column(name = "reason", nullable = false)
    var reason: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: LoanStatus = LoanStatus.SUBMITTED,
    @Column(name = "disbursed_date")
    var disbursedDate: LocalDate? = null,
    @Column(name = "settled_date")
    var settledDate: LocalDate? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "loan_repayment_schedule")
class LoanRepaymentSchedule(
    @Column(name = "loan_id", nullable = false)
    var loanId: UUID,
    @Column(name = "installment_number", nullable = false)
    var installmentNumber: Int,
    @Column(name = "due_date", nullable = false)
    var dueDate: LocalDate,
    @Column(name = "principal_amount", nullable = false, precision = 12, scale = 2)
    var principalAmount: BigDecimal,
    @Column(name = "interest_amount", nullable = false, precision = 12, scale = 2)
    var interestAmount: BigDecimal = BigDecimal("0.00"),
    @Column(name = "total_installment", nullable = false, precision = 12, scale = 2)
    var totalInstallment: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ScheduleStatus = ScheduleStatus.PENDING,
    @Column(name = "deducted_date")
    var deductedDate: LocalDate? = null,
    @Column(name = "payroll_run_id")
    var payrollRunId: UUID? = null,
) : TenantScopedEntity()
