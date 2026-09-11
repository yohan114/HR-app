package com.hr.app.data.benefit

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.BenefitsApi
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class BenefitRepositoryTest {

    private val benefitsApi = mockk<BenefitsApi>()
    private val outbox = mockk<Outbox>(relaxed = true)
    private val clock = Clock { 1772870000000L }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val pendingCountFlow = MutableStateFlow(0)
    private lateinit var repository: BenefitRepository

    private val sampleCategory = BenefitCategoryItem(
        id = UUID.randomUUID(),
        code = "HEALTH_INSURANCE",
        name = "Outpatient Medical Insurance",
        description = "OPD care",
        benefitKind = BenefitCategoryItem.BenefitKind.NON_CASH,
        isActive = true,
    )

    private val samplePolicy = BenefitPolicyItem(
        id = UUID.randomUUID(),
        categoryId = sampleCategory.id,
        categoryCode = sampleCategory.code,
        categoryName = sampleCategory.name,
        code = "POL_OPD_FAMILY",
        name = "Comprehensive Outpatient Medical",
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
    )

    private val sampleEnrollment = EmployeeBenefitEnrollmentItem(
        id = UUID.randomUUID(),
        policyId = samplePolicy.id,
        policyCode = samplePolicy.code,
        policyName = samplePolicy.name,
        categoryName = sampleCategory.name,
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
                id = UUID.randomUUID(),
                fullName = "Champa Perera",
                relationship = BenefitDependentItem.Relationship.SPOUSE,
                dateOfBirth = LocalDate.of(1992, 5, 14),
                nationalIdOrPassport = "199264501234",
                isCovered = true,
            ),
        ),
    )

    private val sampleClaim = BenefitClaimItem(
        id = UUID.randomUUID(),
        enrollmentId = sampleEnrollment.id,
        policyName = samplePolicy.name,
        categoryName = sampleCategory.name,
        claimNumber = "CLM-BEN-2026-001",
        claimDate = LocalDate.now(),
        serviceProvider = "Asiri Central Hospital",
        diagnosisOrReason = "Pediatric viral fever consultation",
        claimedAmount = BigDecimal("15000.00"),
        approvedAmount = BigDecimal("15000.00"),
        coPayAmount = BigDecimal("1500.00"),
        payableAmount = BigDecimal("13500.00"),
        currency = "LKR",
        status = BenefitClaimItem.Status.PAID,
        createdAt = OffsetDateTime.now(),
    )

    @Before
    fun setUp() {
        coEvery { outbox.pendingCount } returns pendingCountFlow
        repository = BenefitRepository(benefitsApi, outbox, json, clock)
    }

    @Test
    fun `refreshCatalogue updates stateFlow on success`() = runTest {
        coEvery { benefitsApi.getBenefitCatalogue() } returns Response.success(
            BenefitCatalogueResponse(listOf(sampleCategory), listOf(samplePolicy)),
        )

        val result = repository.refreshCatalogue()
        assertTrue(result.isSuccess)
        assertNotNull(repository.catalogue.value)
        assertEquals(1, repository.catalogue.value?.policies?.size)
        assertEquals("POL_OPD_FAMILY", repository.catalogue.value?.policies?.first()?.code)
    }

    @Test
    fun `refreshCatalogue falls back to offline default catalogue on network error`() = runTest {
        coEvery { benefitsApi.getBenefitCatalogue() } throws RuntimeException("Network unreachable")

        val result = repository.refreshCatalogue()
        assertTrue(result.isFailure)
        assertNotNull(repository.catalogue.value)
        assertTrue(repository.catalogue.value?.policies.orEmpty().isNotEmpty())
    }

    @Test
    fun `refreshMyBenefits updates stateFlow and metric totals on success`() = runTest {
        coEvery { benefitsApi.getMyBenefits() } returns Response.success(
            MyBenefitsResponse(
                totalAnnualEntitlement = BigDecimal("250000.00"),
                totalUsedAmount = BigDecimal("45000.00"),
                totalPendingAmount = BigDecimal("12500.00"),
                totalRemainingBalance = BigDecimal("192500.00"),
                enrollments = listOf(sampleEnrollment),
            ),
        )

        val result = repository.refreshMyBenefits()
        assertTrue(result.isSuccess)
        assertEquals(BigDecimal("250000.00"), repository.totalAnnualEntitlement.value)
        assertEquals(BigDecimal("45000.00"), repository.totalUsedAmount.value)
        assertEquals(BigDecimal("192500.00"), repository.totalRemainingBalance.value)
        assertEquals(1, repository.myBenefits.value?.enrollments?.size)
    }

    @Test
    fun `refreshClaims updates claims list and pending count`() = runTest {
        coEvery { benefitsApi.getMyBenefitClaims(null) } returns Response.success(
            BenefitClaimsResponse(
                totalClaimedAmount = BigDecimal("15000.00"),
                totalApprovedAmount = BigDecimal("15000.00"),
                totalPaidAmount = BigDecimal("13500.00"),
                pendingCount = 0,
                claims = listOf(sampleClaim),
            ),
        )

        val result = repository.refreshClaims(null)
        assertTrue(result.isSuccess)
        assertEquals(1, repository.claims.value.size)
        assertEquals("CLM-BEN-2026-001", repository.claims.value.first().claimNumber)
        assertEquals(0, repository.pendingClaimsCount.value)
    }

    @Test
    fun `submitClaim updates pending claims count and remaining balances`() = runTest {
        val newClaim = sampleClaim.copy(
            id = UUID.randomUUID(),
            claimNumber = "CLM-BEN-2026-002",
            status = BenefitClaimItem.Status.SUBMITTED,
        )

        coEvery {
            benefitsApi.submitBenefitClaim(
                idempotencyKey = any(),
                benefitClaimSubmitRequest = any(),
            )
        } returns Response.success(newClaim)

        val result = repository.submitClaim(
            enrollmentId = sampleEnrollment.id,
            claimDate = LocalDate.now(),
            serviceProvider = "Nawaloka Hospital",
            diagnosisOrReason = "Specialist consultation",
            claimedAmount = BigDecimal("10000.00"),
        )

        assertTrue(result.isSuccess)
        assertEquals(1, repository.pendingClaimsCount.value)
        assertEquals("CLM-BEN-2026-002", repository.claims.value.first().claimNumber)
    }

    @Test
    fun `cancelClaim updates status to CANCELLED and decrements pending count`() = runTest {
        val claimId = sampleClaim.id
        val submittedClaim = sampleClaim.copy(status = BenefitClaimItem.Status.SUBMITTED)
        val cancelledClaim = sampleClaim.copy(status = BenefitClaimItem.Status.CANCELLED)

        coEvery { benefitsApi.getMyBenefitClaims(null) } returns Response.success(
            BenefitClaimsResponse(
                totalClaimedAmount = BigDecimal("15000.00"),
                totalApprovedAmount = BigDecimal.ZERO,
                totalPaidAmount = BigDecimal.ZERO,
                pendingCount = 1,
                claims = listOf(submittedClaim),
            ),
        )
        repository.refreshClaims(null)
        assertEquals(1, repository.pendingClaimsCount.value)

        coEvery {
            benefitsApi.cancelBenefitClaim(
                id = claimId,
                idempotencyKey = any(),
                cancelBenefitClaimRequest = any(),
            )
        } returns Response.success(cancelledClaim)

        val result = repository.cancelClaim(claimId.toString(), "Entered wrong receipt")
        assertTrue(result.isSuccess)
        assertEquals(BenefitClaimItem.Status.CANCELLED, repository.claims.value.first().status)
        assertEquals(0, repository.pendingClaimsCount.value)
    }
}
