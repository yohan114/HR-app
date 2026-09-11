package com.hr.benefit.internal

import com.hr.benefit.*
import com.hr.employee.EmployeeLookupService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.util.UUID

@Service
@Transactional
class BenefitService(
    private val categoryRepository: BenefitCategoryRepository,
    private val policyRepository: BenefitPolicyRepository,
    private val enrollmentRepository: EmployeeBenefitEnrollmentRepository,
    private val dependentRepository: BenefitDependentRepository,
    private val claimRepository: BenefitClaimRepository,
    private val employeeLookupService: EmployeeLookupService,
) {

    @Transactional(readOnly = true)
    fun getBenefitCatalogue(employeeId: UUID): BenefitCatalogueResponse {
        val categories = categoryRepository.findAllByIsActiveTrueOrderByCodeAsc()
        val policies = policyRepository.findAllByIsActiveTrueOrderByCodeAsc()
        val categoriesMap = categories.associateBy { it.id }

        val employee = employeeLookupService.findById(employeeId)
        val serviceMonths = if (employee != null) {
            Period.between(employee.joinDate, LocalDate.now()).toTotalMonths().toInt()
        } else {
            12 // Default to 12 months tenure if employee profile not seeded
        }

        val policyResponses = policies.map { policy ->
            val cat = categoriesMap[policy.categoryId]
            val (isEligible, reason) = evaluateEligibility(policy, serviceMonths, employee?.salaryGradeId)
            policy.toResponse(
                categoryCode = cat?.code ?: "UNKNOWN",
                categoryName = cat?.name ?: "Unknown Category",
                isEligible = isEligible,
                ineligibilityReason = reason,
            )
        }

        return BenefitCatalogueResponse(
            categories = categories.map { it.toResponse() },
            policies = policyResponses,
        )
    }

    @Transactional(readOnly = true)
    fun getMyBenefits(employeeId: UUID): MyBenefitsResponse {
        val enrollments = enrollmentRepository.findAllByEmployeeIdAndStatusOrderByStartDateDesc(
            employeeId,
            EnrollmentStatus.ACTIVE,
        )
        val policiesMap = policyRepository.findAll().associateBy { it.id }
        val categoriesMap = categoryRepository.findAll().associateBy { it.id }

        var totalAnnual = BigDecimal.ZERO
        var totalUsed = BigDecimal.ZERO
        var totalPending = BigDecimal.ZERO

        val enrollmentItems = enrollments.map { enrollment ->
            val policy = policiesMap[enrollment.policyId]
            val category = policy?.let { categoriesMap[it.categoryId] }
            val dependents = dependentRepository.findAllByEnrollmentIdAndIsCoveredTrue(enrollment.id)

            totalAnnual = totalAnnual.add(enrollment.annualEntitlement)
            totalUsed = totalUsed.add(enrollment.usedAmount)
            totalPending = totalPending.add(enrollment.pendingAmount)

            val remaining = enrollment.annualEntitlement
                .subtract(enrollment.usedAmount)
                .subtract(enrollment.pendingAmount)
                .max(BigDecimal.ZERO)

            enrollment.toResponse(
                policy = policy,
                categoryName = category?.name ?: "General Benefits",
                remainingBalance = remaining.setScale(2, RoundingMode.HALF_UP),
                dependents = dependents.map { it.toResponse() },
            )
        }

        val totalRemaining = totalAnnual.subtract(totalUsed).subtract(totalPending).max(BigDecimal.ZERO)

        return MyBenefitsResponse(
            totalAnnualEntitlement = totalAnnual.setScale(2, RoundingMode.HALF_UP),
            totalUsedAmount = totalUsed.setScale(2, RoundingMode.HALF_UP),
            totalPendingAmount = totalPending.setScale(2, RoundingMode.HALF_UP),
            totalRemainingBalance = totalRemaining.setScale(2, RoundingMode.HALF_UP),
            enrollments = enrollmentItems,
        )
    }

    @Transactional(readOnly = true)
    fun getMyBenefitClaims(employeeId: UUID, statusFilter: String?): BenefitClaimsResponse {
        val parsedStatus = statusFilter?.let {
            runCatching { BenefitClaimStatus.valueOf(it.uppercase()) }.getOrNull()
        }
        val allClaims = claimRepository.findAllByEmployeeIdOrderByClaimDateDesc(employeeId)
        val filtered = if (parsedStatus != null) {
            allClaims.filter { it.status == parsedStatus }
        } else {
            allClaims
        }

        val enrollmentsMap = enrollmentRepository.findAll().associateBy { it.id }
        val policiesMap = policyRepository.findAll().associateBy { it.id }
        val categoriesMap = categoryRepository.findAll().associateBy { it.id }
        val dependentsMap = dependentRepository.findAll().associateBy { it.id }

        var totalClaimed = BigDecimal.ZERO
        var totalApproved = BigDecimal.ZERO
        var totalPaid = BigDecimal.ZERO
        var pendingCount = 0

        for (claim in allClaims) {
            totalClaimed = totalClaimed.add(claim.claimedAmount)
            if (claim.status == BenefitClaimStatus.APPROVED || claim.status == BenefitClaimStatus.PAID) {
                totalApproved = totalApproved.add(claim.approvedAmount ?: claim.claimedAmount)
            }
            if (claim.status == BenefitClaimStatus.PAID) {
                totalPaid = totalPaid.add(claim.payableAmount ?: claim.claimedAmount)
            }
            if (claim.status == BenefitClaimStatus.SUBMITTED || claim.status == BenefitClaimStatus.UNDER_REVIEW) {
                pendingCount++
            }
        }

        val claimItems = filtered.map { claim ->
            val enrollment = enrollmentsMap[claim.enrollmentId]
            val policy = enrollment?.let { policiesMap[it.policyId] }
            val category = policy?.let { categoriesMap[it.categoryId] }
            val dependent = claim.dependentId?.let { dependentsMap[it] }

            claim.toResponse(
                policyName = policy?.name ?: "Standard Benefit",
                categoryName = category?.name ?: "Medical & Health",
                dependentName = dependent?.fullName,
            )
        }

        return BenefitClaimsResponse(
            totalClaimedAmount = totalClaimed.setScale(2, RoundingMode.HALF_UP),
            totalApprovedAmount = totalApproved.setScale(2, RoundingMode.HALF_UP),
            totalPaidAmount = totalPaid.setScale(2, RoundingMode.HALF_UP),
            pendingCount = pendingCount,
            claims = claimItems,
        )
    }

    fun submitBenefitClaim(
        employeeId: UUID,
        request: BenefitClaimSubmitRequest,
    ): BenefitClaimItemResponse {
        require(request.claimedAmount > BigDecimal.ZERO) { "Claimed amount must be greater than zero" }

        val enrollment = enrollmentRepository.findByIdAndEmployeeId(request.enrollmentId, employeeId)
            ?: error("Active enrollment not found for policy id: ${request.enrollmentId}")

        require(enrollment.status == EnrollmentStatus.ACTIVE) {
            "Cannot submit claim against non-active enrollment status: ${enrollment.status}"
        }

        val policy = policyRepository.findById(enrollment.policyId).orElse(null)
        val category = policy?.let { categoryRepository.findById(it.categoryId).orElse(null) }

        val remainingBalance = enrollment.annualEntitlement
            .subtract(enrollment.usedAmount)
            .subtract(enrollment.pendingAmount)

        require(request.claimedAmount <= remainingBalance) {
            "Claimed amount (${request.claimedAmount}) exceeds remaining entitlement balance ($remainingBalance)"
        }

        var dependentName: String? = null
        if (request.dependentId != null) {
            val dependent = dependentRepository.findByIdAndEnrollmentId(request.dependentId, enrollment.id)
                ?: error("Covered dependent not found for this enrollment: ${request.dependentId}")
            require(dependent.isCovered) { "Dependent ${dependent.fullName} is not currently marked as covered" }
            dependentName = dependent.fullName
        }

        val coPayRatio = (policy?.coPayPercentage ?: BigDecimal.ZERO).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
        val coPayAmount = request.claimedAmount.multiply(coPayRatio).setScale(2, RoundingMode.HALF_UP)
        val payableAmount = request.claimedAmount.subtract(coPayAmount).setScale(2, RoundingMode.HALF_UP)

        val claimNumber = "CLM-BEN-${LocalDate.now().year}-${UUID.randomUUID().toString().take(6).uppercase()}"

        val claim = BenefitClaim(
            enrollmentId = enrollment.id,
            employeeId = employeeId,
            claimNumber = claimNumber,
            claimDate = request.claimDate,
            dependentId = request.dependentId,
            serviceProvider = request.serviceProvider,
            diagnosisOrReason = request.diagnosisOrReason,
            invoiceNumber = request.invoiceNumber,
            claimedAmount = request.claimedAmount.setScale(2, RoundingMode.HALF_UP),
            approvedAmount = null,
            coPayAmount = coPayAmount,
            payableAmount = payableAmount,
            currency = request.currency,
            status = BenefitClaimStatus.SUBMITTED,
            receiptUrl = request.receiptUrl,
            receiptKey = request.receiptKey,
            rejectionReason = null,
            approvedAt = null,
            approvedBy = null,
            paidAt = null,
            remarks = request.remarks,
        )
        val savedClaim = claimRepository.save(claim)

        // Lock in pending amount on the enrollment
        enrollment.pendingAmount = enrollment.pendingAmount.add(request.claimedAmount).setScale(2, RoundingMode.HALF_UP)
        enrollmentRepository.save(enrollment)

        return savedClaim.toResponse(
            policyName = policy?.name ?: "Corporate Benefit",
            categoryName = category?.name ?: "Medical & Health",
            dependentName = dependentName,
        )
    }

    fun cancelBenefitClaim(
        employeeId: UUID,
        claimId: UUID,
        reason: String?,
    ): BenefitClaimItemResponse {
        val claim = claimRepository.findByIdAndEmployeeId(claimId, employeeId)
            ?: error("Benefit claim not found: $claimId")

        require(claim.status == BenefitClaimStatus.SUBMITTED || claim.status == BenefitClaimStatus.DRAFT) {
            "Cannot cancel claim in status ${claim.status}. Only SUBMITTED or DRAFT claims can be cancelled."
        }

        val enrollment = enrollmentRepository.findByIdAndEmployeeId(claim.enrollmentId, employeeId)
        if (enrollment != null && claim.status == BenefitClaimStatus.SUBMITTED) {
            enrollment.pendingAmount = enrollment.pendingAmount
                .subtract(claim.claimedAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
            enrollmentRepository.save(enrollment)
        }

        claim.status = BenefitClaimStatus.CANCELLED
        claim.rejectionReason = reason ?: "Cancelled by employee"
        val updatedClaim = claimRepository.save(claim)

        val policy = enrollment?.let { policyRepository.findById(it.policyId).orElse(null) }
        val category = policy?.let { categoryRepository.findById(it.categoryId).orElse(null) }
        val dependent = claim.dependentId?.let { dependentRepository.findById(it).orElse(null) }

        return updatedClaim.toResponse(
            policyName = policy?.name ?: "Corporate Benefit",
            categoryName = category?.name ?: "Medical & Health",
            dependentName = dependent?.fullName,
        )
    }

    private fun evaluateEligibility(
        policy: BenefitPolicy,
        serviceMonths: Int,
        employeeGradeId: UUID?,
    ): Pair<Boolean, String?> {
        if (policy.minServiceMonths > 0 && serviceMonths < policy.minServiceMonths) {
            return false to "Requires ${policy.minServiceMonths} months of service (current: $serviceMonths months)"
        }
        return true to null
    }

    // ---------------------------------------------------------------------------
    // Extension mappers
    // ---------------------------------------------------------------------------

    private fun BenefitCategory.toResponse() = BenefitCategoryItemResponse(
        id = id,
        code = code,
        name = name,
        description = description,
        benefitKind = benefitKind,
        isActive = isActive,
    )

    private fun BenefitPolicy.toResponse(
        categoryCode: String,
        categoryName: String,
        isEligible: Boolean,
        ineligibilityReason: String?,
    ) = BenefitPolicyItemResponse(
        id = id,
        categoryId = categoryId,
        categoryCode = categoryCode,
        categoryName = categoryName,
        code = code,
        name = name,
        description = description,
        coverageTier = coverageTier,
        annualLimit = annualLimit,
        currency = currency,
        coPayPercentage = coPayPercentage,
        deductibleAmount = deductibleAmount,
        minServiceMonths = minServiceMonths,
        eligibleGrades = eligibleGrades,
        requiresReceipt = requiresReceipt,
        isActive = isActive,
        isEligible = isEligible,
        ineligibilityReason = ineligibilityReason,
    )

    private fun EmployeeBenefitEnrollment.toResponse(
        policy: BenefitPolicy?,
        categoryName: String,
        remainingBalance: BigDecimal,
        dependents: List<BenefitDependentItemResponse>,
    ) = EmployeeBenefitEnrollmentItemResponse(
        id = id,
        policyId = policyId,
        policyCode = policy?.code ?: "BEN-STD",
        policyName = policy?.name ?: "Standard Policy",
        categoryName = categoryName,
        coverageTier = policy?.coverageTier ?: CoverageTier.INDIVIDUAL,
        policyNumber = policyNumber,
        enrollmentYear = enrollmentYear,
        startDate = startDate,
        endDate = endDate,
        annualEntitlement = annualEntitlement,
        usedAmount = usedAmount,
        pendingAmount = pendingAmount,
        remainingBalance = remainingBalance,
        currency = policy?.currency ?: "LKR",
        status = status,
        dependents = dependents,
    )

    private fun BenefitDependent.toResponse() = BenefitDependentItemResponse(
        id = id,
        fullName = fullName,
        relationship = relationship,
        dateOfBirth = dateOfBirth,
        nationalIdOrPassport = nationalIdOrPassport,
        isCovered = isCovered,
    )

    private fun BenefitClaim.toResponse(
        policyName: String,
        categoryName: String,
        dependentName: String?,
    ) = BenefitClaimItemResponse(
        id = id,
        enrollmentId = enrollmentId,
        policyName = policyName,
        categoryName = categoryName,
        claimNumber = claimNumber,
        claimDate = claimDate,
        dependentName = dependentName,
        serviceProvider = serviceProvider,
        diagnosisOrReason = diagnosisOrReason,
        invoiceNumber = invoiceNumber,
        claimedAmount = claimedAmount,
        approvedAmount = approvedAmount,
        coPayAmount = coPayAmount,
        payableAmount = payableAmount,
        currency = currency,
        status = status,
        receiptUrl = receiptUrl,
        receiptKey = receiptKey,
        rejectionReason = rejectionReason,
        remarks = remarks,
        createdAt = createdAt ?: java.time.Instant.now(),
    )
}
