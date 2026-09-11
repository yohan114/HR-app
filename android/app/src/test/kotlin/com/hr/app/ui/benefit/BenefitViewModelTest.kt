package com.hr.app.ui.benefit

import com.hr.app.data.benefit.BenefitRepository
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BenefitViewModelTest {

    private val repository = mockk<BenefitRepository>(relaxed = true)

    private val catalogueFlow = MutableStateFlow<BenefitCatalogueResponse?>(null)
    private val myBenefitsFlow = MutableStateFlow<MyBenefitsResponse?>(null)
    private val claimsFlow = MutableStateFlow<List<BenefitClaimItem>>(emptyList())
    private val totalAnnualEntitlementFlow = MutableStateFlow(BigDecimal.ZERO)
    private val totalUsedAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val totalPendingAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val totalRemainingBalanceFlow = MutableStateFlow(BigDecimal.ZERO)
    private val pendingClaimsCountFlow = MutableStateFlow(0)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: BenefitViewModel

    private val sampleCategory = BenefitCategoryItem(
        id = UUID.randomUUID(),
        code = "HEALTH_INSURANCE",
        name = "Outpatient Medical Insurance",
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
        dependents = emptyList(),
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
        Dispatchers.setMain(testDispatcher)

        every { repository.catalogue } returns catalogueFlow
        every { repository.myBenefits } returns myBenefitsFlow
        every { repository.claims } returns claimsFlow
        every { repository.totalAnnualEntitlement } returns totalAnnualEntitlementFlow
        every { repository.totalUsedAmount } returns totalUsedAmountFlow
        every { repository.totalPendingAmount } returns totalPendingAmountFlow
        every { repository.totalRemainingBalance } returns totalRemainingBalanceFlow
        every { repository.pendingClaimsCount } returns pendingClaimsCountFlow

        coEvery { repository.refreshCatalogue() } returns Result.success(
            BenefitCatalogueResponse(listOf(sampleCategory), listOf(samplePolicy)),
        )
        coEvery { repository.refreshMyBenefits() } returns Result.success(
            MyBenefitsResponse(
                totalAnnualEntitlement = BigDecimal("250000.00"),
                totalUsedAmount = BigDecimal("45000.00"),
                totalPendingAmount = BigDecimal("12500.00"),
                totalRemainingBalance = BigDecimal("192500.00"),
                enrollments = listOf(sampleEnrollment),
            ),
        )
        coEvery { repository.refreshClaims(any()) } returns Result.success(listOf(sampleClaim))

        myBenefitsFlow.value = MyBenefitsResponse(
            totalAnnualEntitlement = BigDecimal("250000.00"),
            totalUsedAmount = BigDecimal("45000.00"),
            totalPendingAmount = BigDecimal("12500.00"),
            totalRemainingBalance = BigDecimal("192500.00"),
            enrollments = listOf(sampleEnrollment),
        )
        claimsFlow.value = listOf(sampleClaim)
        totalRemainingBalanceFlow.value = BigDecimal("192500.00")

        viewModel = BenefitViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization observes repository and populates state flows`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.myBenefits.value?.enrollments?.size)
        assertEquals(BigDecimal("192500.00"), viewModel.totalRemainingBalance.value)
        assertEquals(1, viewModel.claims.value.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `selectTab switches activeTab in uiState`() = runTest {
        viewModel.selectTab(BenefitTab.CLAIMS)
        assertEquals(BenefitTab.CLAIMS, viewModel.uiState.value.activeTab)

        viewModel.selectTab(BenefitTab.CATALOGUE)
        assertEquals(BenefitTab.CATALOGUE, viewModel.uiState.value.activeTab)
    }

    @Test
    fun `filterByStatus delegates to repository and updates statusFilter`() = runTest {
        viewModel.filterByStatus("APPROVED")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("APPROVED", viewModel.uiState.value.statusFilter)
        coVerify { repository.refreshClaims("APPROVED") }
    }

    @Test
    fun `openClaimDialog initializes form fields`() = runTest {
        viewModel.openClaimDialog(sampleEnrollment.id)

        val state = viewModel.uiState.value
        assertTrue(state.showClaimDialog)
        assertEquals(sampleEnrollment.id, state.selectedEnrollmentId)
        assertNull(state.selectedDependentId)
        assertEquals("", state.claimedAmount)
        assertEquals("", state.serviceProvider)
    }

    @Test
    fun `submitClaim succeeds with valid inputs and updates banner`() = runTest {
        viewModel.openClaimDialog(sampleEnrollment.id)
        viewModel.updateServiceProvider("Lanka Hospitals")
        viewModel.updateDiagnosisOrReason("Blood investigations panel")
        viewModel.updateInvoiceNumber("INV-2026-99")
        viewModel.updateClaimedAmount("12000.00")

        val submittedClaim = sampleClaim.copy(
            id = UUID.randomUUID(),
            claimNumber = "CLM-BEN-2026-002",
            status = BenefitClaimItem.Status.SUBMITTED,
        )

        coEvery {
            repository.submitClaim(
                enrollmentId = sampleEnrollment.id,
                claimDate = any(),
                dependentId = any(),
                serviceProvider = "Lanka Hospitals",
                diagnosisOrReason = "Blood investigations panel",
                invoiceNumber = "INV-2026-99",
                claimedAmount = BigDecimal("12000.00"),
                currency = any(),
                receiptUrl = any(),
                receiptKey = any(),
                remarks = any(),
            )
        } returns Result.success(submittedClaim)

        viewModel.submitClaim()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showClaimDialog)
        assertEquals(BenefitTab.CLAIMS, state.activeTab)
        assertNotNull(state.successBanner)
        assertTrue(state.successBanner!!.contains("CLM-BEN-2026-002"))
    }

    @Test
    fun `submitClaim sets errorBanner when required fields are blank`() = runTest {
        viewModel.openClaimDialog(sampleEnrollment.id)
        // Leave provider and diagnosis blank
        viewModel.submitClaim()

        val state = viewModel.uiState.value
        assertNotNull(state.errorBanner)
        assertTrue(state.errorBanner!!.contains("provider"))
    }

    @Test
    fun `cancelClaim delegates to repository and updates success banner`() = runTest {
        val claimId = sampleClaim.id.toString()
        val cancelledClaim = sampleClaim.copy(status = BenefitClaimItem.Status.CANCELLED)

        coEvery { repository.cancelClaim(claimId, any()) } returns Result.success(cancelledClaim)

        viewModel.cancelClaim(claimId, "Incorrect charge")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.successBanner)
        assertTrue(state.successBanner!!.contains("restored"))
        coVerify { repository.cancelClaim(claimId, "Incorrect charge") }
    }
}
