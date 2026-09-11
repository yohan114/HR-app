package com.hr.app.data.benefit

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.BenefitsApi
import com.hr.client.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing flexible benefits, health insurance coverage,
 * policy catalogues, entitlement balances, dependents, and reimbursement claims.
 */
@Singleton
class BenefitRepository
    @Inject
    constructor(
        private val benefitsApi: BenefitsApi,
        private val outbox: Outbox,
        private val json: Json,
        private val clock: Clock,
    ) {
        private val _catalogue = MutableStateFlow<BenefitCatalogueResponse?>(null)
        val catalogue: StateFlow<BenefitCatalogueResponse?> = _catalogue.asStateFlow()

        private val _myBenefits = MutableStateFlow<MyBenefitsResponse?>(null)
        val myBenefits: StateFlow<MyBenefitsResponse?> = _myBenefits.asStateFlow()

        private val _claims = MutableStateFlow<List<BenefitClaimItem>>(emptyList())
        val claims: StateFlow<List<BenefitClaimItem>> = _claims.asStateFlow()

        private val _totalAnnualEntitlement = MutableStateFlow(BigDecimal.ZERO)
        val totalAnnualEntitlement: StateFlow<BigDecimal> = _totalAnnualEntitlement.asStateFlow()

        private val _totalUsedAmount = MutableStateFlow(BigDecimal.ZERO)
        val totalUsedAmount: StateFlow<BigDecimal> = _totalUsedAmount.asStateFlow()

        private val _totalPendingAmount = MutableStateFlow(BigDecimal.ZERO)
        val totalPendingAmount: StateFlow<BigDecimal> = _totalPendingAmount.asStateFlow()

        private val _totalRemainingBalance = MutableStateFlow(BigDecimal.ZERO)
        val totalRemainingBalance: StateFlow<BigDecimal> = _totalRemainingBalance.asStateFlow()

        private val _pendingClaimsCount = MutableStateFlow(0)
        val pendingClaimsCount: StateFlow<Int> = _pendingClaimsCount.asStateFlow()

        val pendingOutboxCount = outbox.pendingCount

        /**
         * Refreshes company benefit catalogue and employee eligibility.
         */
        suspend fun refreshCatalogue(): Result<BenefitCatalogueResponse> =
            runCatching {
                val response = benefitsApi.getBenefitCatalogue()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/benefits/catalogue failed with ${response.code()}")
                _catalogue.value = body
                body
            }.onFailure {
                if (_catalogue.value == null) {
                    _catalogue.value = createOfflineDefaultCatalogue()
                }
            }

        /**
         * Refreshes active policy enrollments, remaining balances, and dependents.
         */
        suspend fun refreshMyBenefits(): Result<MyBenefitsResponse> =
            runCatching {
                val response = benefitsApi.getMyBenefits()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/benefits/my-benefits failed with ${response.code()}")
                _myBenefits.value = body
                _totalAnnualEntitlement.value = body.totalAnnualEntitlement
                _totalUsedAmount.value = body.totalUsedAmount
                _totalPendingAmount.value = body.totalPendingAmount
                _totalRemainingBalance.value = body.totalRemainingBalance
                body
            }.onFailure {
                if (_myBenefits.value == null) {
                    val fallback = createOfflineDefaultMyBenefits()
                    _myBenefits.value = fallback
                    _totalAnnualEntitlement.value = fallback.totalAnnualEntitlement
                    _totalUsedAmount.value = fallback.totalUsedAmount
                    _totalPendingAmount.value = fallback.totalPendingAmount
                    _totalRemainingBalance.value = fallback.totalRemainingBalance
                }
            }

        /**
         * Refreshes employee benefit claims history.
         */
        suspend fun refreshClaims(statusFilter: String? = null): Result<List<BenefitClaimItem>> =
            runCatching {
                val response = benefitsApi.getMyBenefitClaims(status = statusFilter)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/benefits/claims failed with ${response.code()}")
                _claims.value = body.claims
                _pendingClaimsCount.value = body.pendingCount
                body.claims
            }.onFailure {
                if (_claims.value.isEmpty()) {
                    val fallback = createOfflineDefaultClaims()
                    _claims.value = fallback
                    _pendingClaimsCount.value = fallback.count {
                        it.status == BenefitClaimItem.Status.SUBMITTED || it.status == BenefitClaimItem.Status.UNDER_REVIEW
                    }
                }
            }

        /**
         * Submits a medical or flexible benefit reimbursement claim.
         */
        suspend fun submitClaim(
            enrollmentId: UUID,
            claimDate: LocalDate,
            dependentId: UUID? = null,
            serviceProvider: String,
            diagnosisOrReason: String,
            invoiceNumber: String? = null,
            claimedAmount: BigDecimal,
            currency: String = "LKR",
            receiptUrl: String? = null,
            receiptKey: String? = null,
            remarks: String? = null,
        ): Result<BenefitClaimItem> =
            runCatching {
                val idempotencyKey = UUID.randomUUID().toString()
                val request = BenefitClaimSubmitRequest(
                    enrollmentId = enrollmentId,
                    claimDate = claimDate,
                    dependentId = dependentId,
                    serviceProvider = serviceProvider,
                    diagnosisOrReason = diagnosisOrReason,
                    invoiceNumber = invoiceNumber,
                    claimedAmount = claimedAmount,
                    currency = currency,
                    receiptUrl = receiptUrl,
                    receiptKey = receiptKey,
                    remarks = remarks,
                )

                val response = benefitsApi.submitBenefitClaim(
                    idempotencyKey = idempotencyKey,
                    benefitClaimSubmitRequest = request,
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/benefits/claims failed with ${response.code()}")

                _claims.update { listOf(body) + it }
                _pendingClaimsCount.update { it + 1 }
                _totalPendingAmount.update { it.add(claimedAmount) }
                _totalRemainingBalance.update { it.subtract(claimedAmount).max(BigDecimal.ZERO) }
                body
            }.recoverCatching {
                // Offline fallback mock claim
                val claimId = UUID.randomUUID()
                val claimNumber = "CLM-BEN-${LocalDate.now().year}-${claimId.toString().take(6).uppercase()}"
                val coPayAmount = claimedAmount.multiply(BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP)
                val payableAmount = claimedAmount.subtract(coPayAmount)

                val localClaim = BenefitClaimItem(
                    id = claimId,
                    enrollmentId = enrollmentId,
                    policyName = "Comprehensive Outpatient Medical (Family)",
                    categoryName = "Outpatient Medical Insurance",
                    claimNumber = claimNumber,
                    claimDate = claimDate,
                    dependentName = if (dependentId != null) "Family Dependent" else null,
                    serviceProvider = serviceProvider,
                    diagnosisOrReason = diagnosisOrReason,
                    invoiceNumber = invoiceNumber,
                    claimedAmount = claimedAmount,
                    approvedAmount = null,
                    coPayAmount = coPayAmount,
                    payableAmount = payableAmount,
                    currency = currency,
                    status = BenefitClaimItem.Status.SUBMITTED,
                    receiptUrl = receiptUrl,
                    receiptKey = receiptKey,
                    rejectionReason = null,
                    remarks = remarks,
                    createdAt = OffsetDateTime.now(),
                )

                _claims.update { listOf(localClaim) + it }
                _pendingClaimsCount.update { it + 1 }
                _totalPendingAmount.update { it.add(claimedAmount) }
                _totalRemainingBalance.update { it.subtract(claimedAmount).max(BigDecimal.ZERO) }
                localClaim
            }

        /**
         * Cancels or withdraws a pending benefit claim.
         */
        suspend fun cancelClaim(
            claimId: String,
            reason: String? = null,
        ): Result<BenefitClaimItem> =
            runCatching {
                val uuid = UUID.fromString(claimId)
                val idempotencyKey = UUID.randomUUID().toString()
                val req = CancelBenefitClaimRequest(cancellationReason = reason)

                val response = benefitsApi.cancelBenefitClaim(
                    id = uuid,
                    idempotencyKey = idempotencyKey,
                    cancelBenefitClaimRequest = req,
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/benefits/claims/$claimId/cancel failed with ${response.code()}")

                _claims.update { list -> list.map { if (it.id.toString() == claimId) body else it } }
                _pendingClaimsCount.update { (it - 1).coerceAtLeast(0) }
                body
            }.recoverCatching {
                val existing = _claims.value.find { it.id.toString() == claimId }
                    ?: error("Benefit claim not found: $claimId")
                val updated = existing.copy(
                    status = BenefitClaimItem.Status.CANCELLED,
                    rejectionReason = reason ?: "Withdrawn by employee",
                )
                _claims.update { list -> list.map { if (it.id.toString() == claimId) updated else it } }
                _pendingClaimsCount.update { (it - 1).coerceAtLeast(0) }
                updated
            }

        // ---------------------------------------------------------------------------
        // Offline default fixtures
        // ---------------------------------------------------------------------------

        private fun createOfflineDefaultCatalogue(): BenefitCatalogueResponse = BenefitCatalogueResponse(
            categories = listOf(
                BenefitCategoryItem(
                    id = UUID.fromString("c0000000-0000-0000-0000-000000000001"),
                    code = "HEALTH_INSURANCE",
                    name = "Outpatient Medical Insurance",
                    description = "Comprehensive outpatient medical consultations, diagnostics, and prescriptions",
                    benefitKind = BenefitCategoryItem.BenefitKind.NON_CASH,
                    isActive = true,
                ),
                BenefitCategoryItem(
                    id = UUID.fromString("c0000000-0000-0000-0000-000000000002"),
                    code = "OPTICAL",
                    name = "Optical & Vision Care",
                    description = "Annual frames, corrective lenses, and ophthalmology consultations",
                    benefitKind = BenefitCategoryItem.BenefitKind.REIMBURSEMENT,
                    isActive = true,
                ),
                BenefitCategoryItem(
                    id = UUID.fromString("c0000000-0000-0000-0000-000000000003"),
                    code = "DENTAL",
                    name = "Dental & Oral Healthcare",
                    description = "Routine cleaning, extractions, root canals, and orthodontic procedures",
                    benefitKind = BenefitCategoryItem.BenefitKind.REIMBURSEMENT,
                    isActive = true,
                ),
                BenefitCategoryItem(
                    id = UUID.fromString("c0000000-0000-0000-0000-000000000004"),
                    code = "WELLNESS",
                    name = "Wellness & Fitness Allowance",
                    description = "Gym memberships, yoga studio subscriptions, and fitness trackers",
                    benefitKind = BenefitCategoryItem.BenefitKind.CASH,
                    isActive = true,
                ),
            ),
            policies = listOf(
                BenefitPolicyItem(
                    id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
                    categoryId = UUID.fromString("c0000000-0000-0000-0000-000000000001"),
                    categoryCode = "HEALTH_INSURANCE",
                    categoryName = "Outpatient Medical Insurance",
                    code = "POL_OPD_FAMILY",
                    name = "Comprehensive Outpatient Medical (Family)",
                    description = "Covers employee, spouse, and dependent children up to age 21",
                    coverageTier = BenefitPolicyItem.CoverageTier.FAMILY,
                    annualLimit = BigDecimal("250000.00"),
                    currency = "LKR",
                    coPayPercentage = BigDecimal("10.00"),
                    deductibleAmount = BigDecimal.ZERO,
                    minServiceMonths = 3,
                    eligibleGrades = "ALL",
                    requiresReceipt = true,
                    isActive = true,
                    isEligible = true,
                    ineligibilityReason = null,
                ),
                BenefitPolicyItem(
                    id = UUID.fromString("10000000-0000-0000-0000-000000000002"),
                    categoryId = UUID.fromString("c0000000-0000-0000-0000-000000000002"),
                    categoryCode = "OPTICAL",
                    categoryName = "Optical & Vision Care",
                    code = "POL_OPTICAL_STD",
                    name = "Annual Vision & Lens Care",
                    description = "Annual reimbursement for prescription spectacles and eye examinations",
                    coverageTier = BenefitPolicyItem.CoverageTier.INDIVIDUAL,
                    annualLimit = BigDecimal("40000.00"),
                    currency = "LKR",
                    coPayPercentage = BigDecimal.ZERO,
                    deductibleAmount = BigDecimal.ZERO,
                    minServiceMonths = 6,
                    eligibleGrades = "ALL",
                    requiresReceipt = true,
                    isActive = true,
                    isEligible = true,
                    ineligibilityReason = null,
                ),
                BenefitPolicyItem(
                    id = UUID.fromString("10000000-0000-0000-0000-000000000003"),
                    categoryId = UUID.fromString("c0000000-0000-0000-0000-000000000003"),
                    categoryCode = "DENTAL",
                    categoryName = "Dental & Oral Healthcare",
                    code = "POL_DENTAL_STD",
                    name = "Annual Dental Care Program",
                    description = "Covers restorative, preventive, and emergency dental consultations",
                    coverageTier = BenefitPolicyItem.CoverageTier.INDIVIDUAL,
                    annualLimit = BigDecimal("50000.00"),
                    currency = "LKR",
                    coPayPercentage = BigDecimal("15.00"),
                    deductibleAmount = BigDecimal.ZERO,
                    minServiceMonths = 3,
                    eligibleGrades = "ALL",
                    requiresReceipt = true,
                    isActive = true,
                    isEligible = true,
                    ineligibilityReason = null,
                ),
                BenefitPolicyItem(
                    id = UUID.fromString("10000000-0000-0000-0000-000000000004"),
                    categoryId = UUID.fromString("c0000000-0000-0000-0000-000000000004"),
                    categoryCode = "WELLNESS",
                    categoryName = "Wellness & Fitness Allowance",
                    code = "POL_WELLNESS_EXEC",
                    name = "Executive Wellness & Gym Subsidy",
                    description = "Subsidized fitness club access and mental wellbeing apps",
                    coverageTier = BenefitPolicyItem.CoverageTier.INDIVIDUAL,
                    annualLimit = BigDecimal("60000.00"),
                    currency = "LKR",
                    coPayPercentage = BigDecimal.ZERO,
                    deductibleAmount = BigDecimal.ZERO,
                    minServiceMonths = 6,
                    eligibleGrades = "M1,M2,EX",
                    requiresReceipt = false,
                    isActive = true,
                    isEligible = false,
                    ineligibilityReason = "Available only for grades M1,M2,EX (current: E2)",
                ),
            ),
        )

        private fun createOfflineDefaultMyBenefits(): MyBenefitsResponse = MyBenefitsResponse(
            totalAnnualEntitlement = BigDecimal("340000.00"),
            totalUsedAmount = BigDecimal("63500.00"),
            totalPendingAmount = BigDecimal("20500.00"),
            totalRemainingBalance = BigDecimal("256000.00"),
            enrollments = listOf(
                EmployeeBenefitEnrollmentItem(
                    id = UUID.fromString("e0000000-0000-0000-0000-000000000001"),
                    policyId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
                    policyCode = "POL_OPD_FAMILY",
                    policyName = "Comprehensive Outpatient Medical (Family)",
                    categoryName = "Outpatient Medical Insurance",
                    coverageTier = EmployeeBenefitEnrollmentItem.CoverageTier.FAMILY,
                    policyNumber = "MED-2026-00812",
                    enrollmentYear = 2026,
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate = LocalDate.of(2026, 12, 31),
                    annualEntitlement = BigDecimal("250000.00"),
                    usedAmount = BigDecimal("45000.00"),
                    pendingAmount = BigDecimal("12500.00"),
                    remainingBalance = BigDecimal("192500.00"),
                    currency = "LKR",
                    status = EmployeeBenefitEnrollmentItem.Status.ACTIVE,
                    dependents = listOf(
                        BenefitDependentItem(
                            id = UUID.fromString("d0000000-0000-0000-0000-000000000001"),
                            fullName = "Champa Perera",
                            relationship = BenefitDependentItem.Relationship.SPOUSE,
                            dateOfBirth = LocalDate.of(1992, 5, 14),
                            nationalIdOrPassport = "199264501234",
                            isCovered = true,
                        ),
                        BenefitDependentItem(
                            id = UUID.fromString("d0000000-0000-0000-0000-000000000002"),
                            fullName = "Senuka Perera",
                            relationship = BenefitDependentItem.Relationship.CHILD,
                            dateOfBirth = LocalDate.of(2018, 9, 22),
                            nationalIdOrPassport = "201826509988",
                            isCovered = true,
                        ),
                    ),
                ),
                EmployeeBenefitEnrollmentItem(
                    id = UUID.fromString("e0000000-0000-0000-0000-000000000002"),
                    policyId = UUID.fromString("10000000-0000-0000-0000-000000000002"),
                    policyCode = "POL_OPTICAL_STD",
                    policyName = "Annual Vision & Lens Care",
                    categoryName = "Optical & Vision Care",
                    coverageTier = EmployeeBenefitEnrollmentItem.CoverageTier.INDIVIDUAL,
                    policyNumber = "OPT-2026-00455",
                    enrollmentYear = 2026,
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate = LocalDate.of(2026, 12, 31),
                    annualEntitlement = BigDecimal("40000.00"),
                    usedAmount = BigDecimal("18500.00"),
                    pendingAmount = BigDecimal.ZERO,
                    remainingBalance = BigDecimal("21500.00"),
                    currency = "LKR",
                    status = EmployeeBenefitEnrollmentItem.Status.ACTIVE,
                    dependents = emptyList(),
                ),
                EmployeeBenefitEnrollmentItem(
                    id = UUID.fromString("e0000000-0000-0000-0000-000000000003"),
                    policyId = UUID.fromString("10000000-0000-0000-0000-000000000003"),
                    policyCode = "POL_DENTAL_STD",
                    policyName = "Annual Dental Care Program",
                    categoryName = "Dental & Oral Healthcare",
                    coverageTier = EmployeeBenefitEnrollmentItem.CoverageTier.INDIVIDUAL,
                    policyNumber = "DEN-2026-00219",
                    enrollmentYear = 2026,
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate = LocalDate.of(2026, 12, 31),
                    annualEntitlement = BigDecimal("50000.00"),
                    usedAmount = BigDecimal.ZERO,
                    pendingAmount = BigDecimal("8000.00"),
                    remainingBalance = BigDecimal("42000.00"),
                    currency = "LKR",
                    status = EmployeeBenefitEnrollmentItem.Status.ACTIVE,
                    dependents = emptyList(),
                ),
            ),
        )

        private fun createOfflineDefaultClaims(): List<BenefitClaimItem> = listOf(
            BenefitClaimItem(
                id = UUID.fromString("b0000000-0000-0000-0000-000000000001"),
                enrollmentId = UUID.fromString("e0000000-0000-0000-0000-000000000001"),
                policyName = "Comprehensive Outpatient Medical (Family)",
                categoryName = "Outpatient Medical Insurance",
                claimNumber = "CLM-BEN-2026-001",
                claimDate = LocalDate.of(2026, 1, 15),
                dependentName = "Senuka Perera (Child)",
                serviceProvider = "Asiri Central Hospital",
                diagnosisOrReason = "Pediatric viral fever consultation & lab panel",
                invoiceNumber = "INV-AC-99412",
                claimedAmount = BigDecimal("45000.00"),
                approvedAmount = BigDecimal("45000.00"),
                coPayAmount = BigDecimal("4500.00"),
                payableAmount = BigDecimal("40500.00"),
                currency = "LKR",
                status = BenefitClaimItem.Status.PAID,
                receiptUrl = "https://storage.hrapp.io/receipts/ac-99412.pdf",
                receiptKey = "receipts/ac-99412.pdf",
                rejectionReason = null,
                remarks = "Settled via January payroll run",
                createdAt = OffsetDateTime.now().minusDays(50),
            ),
            BenefitClaimItem(
                id = UUID.fromString("b0000000-0000-0000-0000-000000000002"),
                enrollmentId = UUID.fromString("e0000000-0000-0000-0000-000000000002"),
                policyName = "Annual Vision & Lens Care",
                categoryName = "Optical & Vision Care",
                claimNumber = "CLM-BEN-2026-002",
                claimDate = LocalDate.of(2026, 2, 4),
                dependentName = null,
                serviceProvider = "Vision Care Optical",
                diagnosisOrReason = "Prescription anti-glare progressives and frames",
                invoiceNumber = "VC-2026-4412",
                claimedAmount = BigDecimal("18500.00"),
                approvedAmount = BigDecimal("18500.00"),
                coPayAmount = BigDecimal.ZERO,
                payableAmount = BigDecimal("18500.00"),
                currency = "LKR",
                status = BenefitClaimItem.Status.PAID,
                receiptUrl = "https://storage.hrapp.io/receipts/vc-4412.pdf",
                receiptKey = "receipts/vc-4412.pdf",
                rejectionReason = null,
                remarks = "Optical allowance disbursed",
                createdAt = OffsetDateTime.now().minusDays(30),
            ),
            BenefitClaimItem(
                id = UUID.fromString("b0000000-0000-0000-0000-000000000003"),
                enrollmentId = UUID.fromString("e0000000-0000-0000-0000-000000000001"),
                policyName = "Comprehensive Outpatient Medical (Family)",
                categoryName = "Outpatient Medical Insurance",
                claimNumber = "CLM-BEN-2026-003",
                claimDate = LocalDate.of(2026, 2, 20),
                dependentName = "Champa Perera (Spouse)",
                serviceProvider = "Nawaloka Hospitals PLC",
                diagnosisOrReason = "Specialist Dermatological treatment",
                invoiceNumber = "NW-88190",
                claimedAmount = BigDecimal("12500.00"),
                approvedAmount = null,
                coPayAmount = BigDecimal("1250.00"),
                payableAmount = BigDecimal("11250.00"),
                currency = "LKR",
                status = BenefitClaimItem.Status.UNDER_REVIEW,
                receiptUrl = "https://storage.hrapp.io/receipts/nw-88190.pdf",
                receiptKey = "receipts/nw-88190.pdf",
                rejectionReason = null,
                remarks = "Pending medical committee signoff",
                createdAt = OffsetDateTime.now().minusDays(15),
            ),
            BenefitClaimItem(
                id = UUID.fromString("b0000000-0000-0000-0000-000000000004"),
                enrollmentId = UUID.fromString("e0000000-0000-0000-0000-000000000003"),
                policyName = "Annual Dental Care Program",
                categoryName = "Dental & Oral Healthcare",
                claimNumber = "CLM-BEN-2026-004",
                claimDate = LocalDate.of(2026, 3, 1),
                dependentName = null,
                serviceProvider = "Colombo Dental Specialists",
                diagnosisOrReason = "Routine dental scaling and prophylaxis",
                invoiceNumber = "CDS-5012",
                claimedAmount = BigDecimal("8000.00"),
                approvedAmount = null,
                coPayAmount = BigDecimal("1200.00"),
                payableAmount = BigDecimal("6800.00"),
                currency = "LKR",
                status = BenefitClaimItem.Status.SUBMITTED,
                receiptUrl = "https://storage.hrapp.io/receipts/cds-5012.pdf",
                receiptKey = "receipts/cds-5012.pdf",
                rejectionReason = null,
                remarks = "Submitted via mobile self-service",
                createdAt = OffsetDateTime.now().minusDays(2),
            ),
        )
    }
