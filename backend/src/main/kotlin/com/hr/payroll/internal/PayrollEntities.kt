package com.hr.payroll.internal

import com.hr.payroll.LineCategory
import com.hr.payroll.PayFrequency
import com.hr.payroll.PayPeriodStatus
import com.hr.payroll.PayrollRunStatus
import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "pay_group")
class PayGroup(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "country_code", nullable = false, length = 2)
    var countryCode: String,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "pay_frequency", nullable = false, length = 32)
    var payFrequency: PayFrequency = PayFrequency.MONTHLY,
    @Column(name = "standard_days_per_month", nullable = false, precision = 4, scale = 2)
    var standardDaysPerMonth: BigDecimal = BigDecimal("22.00"),
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "pay_period")
class PayPeriod(
    @Column(name = "pay_group_id", nullable = false)
    var payGroupId: UUID,
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
    @Column(name = "payment_date", nullable = false)
    var paymentDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: PayPeriodStatus = PayPeriodStatus.OPEN,
) : TenantScopedEntity()

@Entity
@Table(name = "payroll_run")
class PayrollRun(
    @Column(name = "pay_group_id", nullable = false)
    var payGroupId: UUID,
    @Column(name = "pay_period_id", nullable = false)
    var payPeriodId: UUID,
    @Column(name = "run_number", nullable = false)
    var runNumber: Int = 1,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: PayrollRunStatus = PayrollRunStatus.DRAFT,
    @Column(name = "total_gross", nullable = false, precision = 14, scale = 2)
    var totalGross: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_statutory_employee", nullable = false, precision = 14, scale = 2)
    var totalStatutoryEmployee: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_statutory_employer", nullable = false, precision = 14, scale = 2)
    var totalStatutoryEmployer: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_tax", nullable = false, precision = 14, scale = 2)
    var totalTax: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_net", nullable = false, precision = 14, scale = 2)
    var totalNet: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_employees", nullable = false)
    var totalEmployees: Int = 0,
    @Column(name = "calculated_at")
    var calculatedAt: Instant? = null,
    @Column(name = "calculated_by")
    var calculatedBy: UUID? = null,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
    @Column(name = "approved_by")
    var approvedBy: UUID? = null,
    @Column(name = "committed_at")
    var committedAt: Instant? = null,
    @Column(name = "committed_by")
    var committedBy: UUID? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "payroll_result")
class PayrollResult(
    @Column(name = "payroll_run_id", nullable = false)
    var payrollRunId: UUID,
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "employee_code", nullable = false, length = 32)
    var employeeCode: String,
    @Column(name = "employee_name", nullable = false, length = 128)
    var employeeName: String,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String,
    @Column(name = "basic_salary", nullable = false, precision = 12, scale = 2)
    var basicSalary: BigDecimal,
    @Column(name = "gross_pay", nullable = false, precision = 12, scale = 2)
    var grossPay: BigDecimal,
    @Column(name = "total_statutory_employee", nullable = false, precision = 12, scale = 2)
    var totalStatutoryEmployee: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_statutory_employer", nullable = false, precision = 12, scale = 2)
    var totalStatutoryEmployer: BigDecimal = BigDecimal.ZERO,
    @Column(name = "tax_withheld", nullable = false, precision = 12, scale = 2)
    var taxWithheld: BigDecimal = BigDecimal.ZERO,
    @Column(name = "total_voluntary_deductions", nullable = false, precision = 12, scale = 2)
    var totalVoluntaryDeductions: BigDecimal = BigDecimal.ZERO,
    @Column(name = "net_pay", nullable = false, precision = 12, scale = 2)
    var netPay: BigDecimal,
    @Column(name = "payment_status", nullable = false, length = 32)
    var paymentStatus: String = "PENDING",
) : TenantScopedEntity()

@Entity
@Table(name = "payroll_result_line")
class PayrollResultLine(
    @Column(name = "payroll_result_id", nullable = false)
    var payrollResultId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "line_category", nullable = false, length = 32)
    var lineCategory: LineCategory,
    @Column(name = "item_code", nullable = false, length = 32)
    var itemCode: String,
    @Column(name = "item_name", nullable = false, length = 128)
    var itemName: String,
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    var amount: BigDecimal,
    @Column(name = "is_statutory", nullable = false)
    var isStatutory: Boolean = false,
    @Column(name = "calculation_trace")
    var calculationTrace: String? = null,
) : TenantScopedEntity()
