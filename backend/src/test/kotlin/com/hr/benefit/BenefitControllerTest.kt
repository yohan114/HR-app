package com.hr.benefit

import com.hr.benefit.internal.BenefitController
import com.hr.benefit.internal.BenefitService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Benefit Controller Unit Tests")
class BenefitControllerTest {

    private val benefitService = mockk<BenefitService>(relaxed = true)
    private val controller = BenefitController(benefitService)

    @Test
    fun `getBenefitCatalogue delegates to service`() {
        val sampleCat = BenefitCategoryItemResponse(
            id = UUID.randomUUID(),
            code = "HEALTH_INSURANCE",
            name = "Outpatient Medical",
            benefitKind = BenefitKind.NON_CASH,
            isActive = true,
        )
        val samplePolicy = BenefitPolicyItemResponse(
            id = UUID.randomUUID(),
            categoryId = sampleCat.id,
            categoryCode = sampleCat.code,
            categoryName = sampleCat.name,
            code = "POL_OPD",
            name = "OPD Family",
            coverageTier = CoverageTier.FAMILY,
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
        )

        every { benefitService.getBenefitCatalogue(any()) } returns BenefitCatalogueResponse(
            categories = listOf(sampleCat),
            policies = listOf(samplePolicy),
        )

        val response = controller.getBenefitCatalogue(null)
        assertThat(response.categories).hasSize(1)
        assertThat(response.policies).hasSize(1)
        assertThat(response.policies[0].code).isEqualTo("POL_OPD")
        assertThat(response.policies[0].isEligible).isTrue()
    }

    @Test
    fun `getMyBenefits delegates to service`() {
        val enrollment = EmployeeBenefitEnrollmentItemResponse(
            id = UUID.randomUUID(),
            policyId = UUID.randomUUID(),
            policyCode = "POL_OPD",
            policyName = "OPD Family",
            categoryName = "Medical",
            coverageTier = CoverageTier.FAMILY,
            policyNumber = "MED-2026-11",
            enrollmentYear = 2026,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 12, 31),
            annualEntitlement = BigDecimal("250000.00"),
            usedAmount = BigDecimal("45000.00"),
            pendingAmount = BigDecimal("0.00"),
            remainingBalance = BigDecimal("205000.00"),
            currency = "LKR",
            status = EnrollmentStatus.ACTIVE,
            dependents = emptyList(),
        )

        every { benefitService.getMyBenefits(any()) } returns MyBenefitsResponse(
            totalAnnualEntitlement = BigDecimal("250000.00"),
            totalUsedAmount = BigDecimal("45000.00"),
            totalPendingAmount = BigDecimal.ZERO,
            totalRemainingBalance = BigDecimal("205000.00"),
            enrollments = listOf(enrollment),
        )

        val response = controller.getMyBenefits(null)
        assertThat(response.enrollments).hasSize(1)
        assertThat(response.totalRemainingBalance).isEqualByComparingTo(BigDecimal("205000.00"))
    }

    @Test
    fun `submitBenefitClaim delegates to service`() {
        val claimItem = BenefitClaimItemResponse(
            id = UUID.randomUUID(),
            enrollmentId = UUID.randomUUID(),
            policyName = "OPD Family",
            categoryName = "Medical",
            claimNumber = "CLM-BEN-2026-001",
            claimDate = LocalDate.now(),
            serviceProvider = "Asiri Central",
            diagnosisOrReason = "Viral fever",
            claimedAmount = BigDecimal("15000.00"),
            approvedAmount = null,
            coPayAmount = BigDecimal("1500.00"),
            payableAmount = BigDecimal("13500.00"),
            currency = "LKR",
            status = BenefitClaimStatus.SUBMITTED,
            createdAt = Instant.now(),
        )

        val request = BenefitClaimSubmitRequest(
            enrollmentId = claimItem.enrollmentId,
            claimDate = LocalDate.now(),
            serviceProvider = "Asiri Central",
            diagnosisOrReason = "Viral fever",
            claimedAmount = BigDecimal("15000.00"),
        )

        every { benefitService.submitBenefitClaim(any(), any()) } returns claimItem

        val response = controller.submitBenefitClaim(request, null, null)
        assertThat(response.claimNumber).isEqualTo("CLM-BEN-2026-001")
        assertThat(response.payableAmount).isEqualByComparingTo(BigDecimal("13500.00"))
        assertThat(response.status).isEqualTo(BenefitClaimStatus.SUBMITTED)
    }

    @Test
    fun `cancelBenefitClaim delegates to service`() {
        val claimId = UUID.randomUUID()
        val cancelledClaim = BenefitClaimItemResponse(
            id = claimId,
            enrollmentId = UUID.randomUUID(),
            policyName = "OPD Family",
            categoryName = "Medical",
            claimNumber = "CLM-BEN-2026-001",
            claimDate = LocalDate.now(),
            serviceProvider = "Asiri Central",
            diagnosisOrReason = "Viral fever",
            claimedAmount = BigDecimal("15000.00"),
            currency = "LKR",
            status = BenefitClaimStatus.CANCELLED,
            rejectionReason = "User withdrew request",
            createdAt = Instant.now(),
        )

        every { benefitService.cancelBenefitClaim(any(), claimId, any()) } returns cancelledClaim

        val response = controller.cancelBenefitClaim(
            id = claimId,
            idempotencyKey = null,
            request = CancelBenefitClaimRequest("User withdrew request"),
            jwt = null,
        )

        assertThat(response.status).isEqualTo(BenefitClaimStatus.CANCELLED)
        assertThat(response.rejectionReason).isEqualTo("User withdrew request")
    }
}
