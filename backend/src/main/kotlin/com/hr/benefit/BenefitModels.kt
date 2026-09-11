package com.hr.benefit

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * High-level classification of a company benefit.
 */
enum class BenefitKind {
    CASH,
    NON_CASH,
    REIMBURSEMENT,
}

/**
 * Coverage breadth for insurance and health benefit policies.
 */
enum class CoverageTier {
    INDIVIDUAL,
    EMPLOYEE_AND_SPOUSE,
    FAMILY,
}

/**
 * Lifecycle status of an employee's annual policy enrollment.
 */
enum class EnrollmentStatus {
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    TERMINATED,
}

/**
 * Relationship of a covered family dependent.
 */
enum class DependentRelationship {
    SPOUSE,
    CHILD,
    PARENT,
}

/**
 * Workflow status of a medical or flexible benefit claim.
 */
enum class BenefitClaimStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    PAID,
    CANCELLED,
}

// ---------------------------------------------------------------------------
// DTOs matching the OpenAPI spec contract
// ---------------------------------------------------------------------------

data class BenefitCategoryItemResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val description: String? = null,
    val benefitKind: BenefitKind,
    val isActive: Boolean,
)

data class BenefitPolicyItemResponse(
    val id: UUID,
    val categoryId: UUID,
    val categoryCode: String,
    val categoryName: String,
    val code: String,
    val name: String,
    val description: String? = null,
    val coverageTier: CoverageTier,
    val annualLimit: BigDecimal,
    val currency: String = "LKR",
    val coPayPercentage: BigDecimal = BigDecimal.ZERO,
    val deductibleAmount: BigDecimal = BigDecimal.ZERO,
    val minServiceMonths: Int = 0,
    val eligibleGrades: String = "ALL",
    val requiresReceipt: Boolean = true,
    val isActive: Boolean = true,
    val isEligible: Boolean = true,
    val ineligibilityReason: String? = null,
)

data class BenefitCatalogueResponse(
    val categories: List<BenefitCategoryItemResponse>,
    val policies: List<BenefitPolicyItemResponse>,
)

data class BenefitDependentItemResponse(
    val id: UUID,
    val fullName: String,
    val relationship: DependentRelationship,
    val dateOfBirth: LocalDate,
    val nationalIdOrPassport: String? = null,
    val isCovered: Boolean = true,
)

data class EmployeeBenefitEnrollmentItemResponse(
    val id: UUID,
    val policyId: UUID,
    val policyCode: String,
    val policyName: String,
    val categoryName: String,
    val coverageTier: CoverageTier,
    val policyNumber: String,
    val enrollmentYear: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val annualEntitlement: BigDecimal,
    val usedAmount: BigDecimal,
    val pendingAmount: BigDecimal,
    val remainingBalance: BigDecimal,
    val currency: String = "LKR",
    val status: EnrollmentStatus,
    val dependents: List<BenefitDependentItemResponse> = emptyList(),
)

data class MyBenefitsResponse(
    val totalAnnualEntitlement: BigDecimal,
    val totalUsedAmount: BigDecimal,
    val totalPendingAmount: BigDecimal,
    val totalRemainingBalance: BigDecimal,
    val enrollments: List<EmployeeBenefitEnrollmentItemResponse>,
)

data class BenefitClaimItemResponse(
    val id: UUID,
    val enrollmentId: UUID,
    val policyName: String,
    val categoryName: String,
    val claimNumber: String,
    val claimDate: LocalDate,
    val dependentName: String? = null,
    val serviceProvider: String,
    val diagnosisOrReason: String,
    val invoiceNumber: String? = null,
    val claimedAmount: BigDecimal,
    val approvedAmount: BigDecimal? = null,
    val coPayAmount: BigDecimal = BigDecimal.ZERO,
    val payableAmount: BigDecimal? = null,
    val currency: String = "LKR",
    val status: BenefitClaimStatus,
    val receiptUrl: String? = null,
    val receiptKey: String? = null,
    val rejectionReason: String? = null,
    val remarks: String? = null,
    val createdAt: Instant,
)

data class BenefitClaimsResponse(
    val totalClaimedAmount: BigDecimal,
    val totalApprovedAmount: BigDecimal,
    val totalPaidAmount: BigDecimal,
    val pendingCount: Int,
    val claims: List<BenefitClaimItemResponse>,
)

data class BenefitClaimSubmitRequest(
    val enrollmentId: UUID,
    val claimDate: LocalDate,
    val dependentId: UUID? = null,
    val serviceProvider: String,
    val diagnosisOrReason: String,
    val invoiceNumber: String? = null,
    val claimedAmount: BigDecimal,
    val currency: String = "LKR",
    val receiptUrl: String? = null,
    val receiptKey: String? = null,
    val remarks: String? = null,
)

data class CancelBenefitClaimRequest(
    val cancellationReason: String? = null,
)
