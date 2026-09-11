package com.hr.benefit.internal

import com.hr.benefit.BenefitClaimStatus
import com.hr.benefit.BenefitKind
import com.hr.benefit.CoverageTier
import com.hr.benefit.DependentRelationship
import com.hr.benefit.EnrollmentStatus
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
@Table(name = "benefit_category")
class BenefitCategory(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_kind", nullable = false, length = 32)
    var benefitKind: BenefitKind = BenefitKind.NON_CASH,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "benefit_policy")
class BenefitPolicy(
    @Column(name = "category_id", nullable = false)
    var categoryId: UUID,
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_tier", nullable = false, length = 32)
    var coverageTier: CoverageTier = CoverageTier.INDIVIDUAL,
    @Column(name = "annual_limit", nullable = false, precision = 12, scale = 2)
    var annualLimit: BigDecimal,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "LKR",
    @Column(name = "co_pay_percentage", nullable = false, precision = 5, scale = 2)
    var coPayPercentage: BigDecimal = BigDecimal.ZERO,
    @Column(name = "deductible_amount", nullable = false, precision = 12, scale = 2)
    var deductibleAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "min_service_months", nullable = false)
    var minServiceMonths: Int = 0,
    @Column(name = "eligible_grades", nullable = false, length = 128)
    var eligibleGrades: String = "ALL",
    @Column(name = "requires_receipt", nullable = false)
    var requiresReceipt: Boolean = true,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "employee_benefit_enrollment")
class EmployeeBenefitEnrollment(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "policy_id", nullable = false)
    var policyId: UUID,
    @Column(name = "policy_number", nullable = false, length = 64)
    var policyNumber: String,
    @Column(name = "enrollment_year", nullable = false)
    var enrollmentYear: Int,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
    @Column(name = "annual_entitlement", nullable = false, precision = 12, scale = 2)
    var annualEntitlement: BigDecimal,
    @Column(name = "used_amount", nullable = false, precision = 12, scale = 2)
    var usedAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "pending_amount", nullable = false, precision = 12, scale = 2)
    var pendingAmount: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: EnrollmentStatus = EnrollmentStatus.ACTIVE,
) : TenantScopedEntity()

@Entity
@Table(name = "benefit_dependent")
class BenefitDependent(
    @Column(name = "enrollment_id", nullable = false)
    var enrollmentId: UUID,
    @Column(name = "full_name", nullable = false, length = 128)
    var fullName: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 32)
    var relationship: DependentRelationship,
    @Column(name = "date_of_birth", nullable = false)
    var dateOfBirth: LocalDate,
    @Column(name = "national_id_or_passport", length = 64)
    var nationalIdOrPassport: String? = null,
    @Column(name = "is_covered", nullable = false)
    var isCovered: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "benefit_claim")
class BenefitClaim(
    @Column(name = "enrollment_id", nullable = false)
    var enrollmentId: UUID,
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "claim_number", nullable = false, length = 32)
    var claimNumber: String,
    @Column(name = "claim_date", nullable = false)
    var claimDate: LocalDate,
    @Column(name = "dependent_id")
    var dependentId: UUID? = null,
    @Column(name = "service_provider", nullable = false, length = 128)
    var serviceProvider: String,
    @Column(name = "diagnosis_or_reason", nullable = false, length = 255)
    var diagnosisOrReason: String,
    @Column(name = "invoice_number", length = 64)
    var invoiceNumber: String? = null,
    @Column(name = "claimed_amount", nullable = false, precision = 12, scale = 2)
    var claimedAmount: BigDecimal,
    @Column(name = "approved_amount", precision = 12, scale = 2)
    var approvedAmount: BigDecimal? = null,
    @Column(name = "co_pay_amount", precision = 12, scale = 2)
    var coPayAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "payable_amount", precision = 12, scale = 2)
    var payableAmount: BigDecimal? = null,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "LKR",
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: BenefitClaimStatus = BenefitClaimStatus.SUBMITTED,
    @Column(name = "receipt_url")
    var receiptUrl: String? = null,
    @Column(name = "receipt_key", length = 255)
    var receiptKey: String? = null,
    @Column(name = "rejection_reason")
    var rejectionReason: String? = null,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
    @Column(name = "approved_by")
    var approvedBy: UUID? = null,
    @Column(name = "paid_at")
    var paidAt: Instant? = null,
    @Column(name = "remarks")
    var remarks: String? = null,
) : TenantScopedEntity()
