package com.hr.app.ui.expense

import com.hr.app.data.expense.ExpenseRepository
import com.hr.client.model.ExpenseCategoryItem
import com.hr.client.model.ExpenseClaimDetailResponse
import com.hr.client.model.ExpenseClaimItem
import com.hr.client.model.ExpenseClaimLineItem
import com.hr.client.model.ReceiptOcrResponse
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelTest {

    private val repository = mockk<ExpenseRepository>(relaxed = true)

    private val categoriesFlow = MutableStateFlow<List<ExpenseCategoryItem>>(emptyList())
    private val claimsFlow = MutableStateFlow<List<ExpenseClaimItem>>(emptyList())
    private val totalClaimedAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val totalApprovedAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val totalReimbursedAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val pendingCountFlow = MutableStateFlow(0)
    private val selectedClaimDetailFlow = MutableStateFlow<ExpenseClaimDetailResponse?>(null)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ExpenseViewModel

    private val sampleCategory = ExpenseCategoryItem(
        id = UUID.randomUUID(),
        code = "MEALS",
        name = "Meals & Subsistence",
        description = "Business meals",
        requiresReceipt = true,
        maxAmountPerClaim = BigDecimal("15000.00"),
        isActive = true,
    )

    private val sampleClaim = ExpenseClaimItem(
        id = UUID.randomUUID(),
        claimNumber = "EXP-2026-0042",
        title = "Client Tech Summit Galle",
        claimDate = LocalDate.now(),
        currency = "LKR",
        totalAmount = BigDecimal("17500.00"),
        status = ExpenseClaimItem.Status.SUBMITTED,
        lineCount = 2,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { repository.categories } returns categoriesFlow
        every { repository.claims } returns claimsFlow
        every { repository.totalClaimedAmount } returns totalClaimedAmountFlow
        every { repository.totalApprovedAmount } returns totalApprovedAmountFlow
        every { repository.totalReimbursedAmount } returns totalReimbursedAmountFlow
        every { repository.pendingCount } returns pendingCountFlow
        every { repository.selectedClaimDetail } returns selectedClaimDetailFlow

        coEvery { repository.refreshCategories() } returns Result.success(listOf(sampleCategory))
        coEvery { repository.refreshClaims(any()) } returns Result.success(listOf(sampleClaim))

        categoriesFlow.value = listOf(sampleCategory)
        claimsFlow.value = listOf(sampleClaim)
        totalClaimedAmountFlow.value = BigDecimal("17500.00")
        pendingCountFlow.value = 1

        viewModel = ExpenseViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization observes repository and populates state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.categories.size)
        assertEquals("MEALS", state.categories.first().code)
        assertEquals(1, state.claims.size)
        assertEquals("EXP-2026-0042", state.claims.first().claimNumber)
        assertEquals(BigDecimal("17500.00"), state.totalClaimedAmount)
        assertEquals(1, state.pendingCount)
    }

    @Test
    fun `setTab updates selectedTab`() = runTest {
        viewModel.setTab(ExpenseTab.SUBMIT_CLAIM)
        assertEquals(ExpenseTab.SUBMIT_CLAIM, viewModel.state.value.selectedTab)

        viewModel.setTab(ExpenseTab.POLICY_LIMITS)
        assertEquals(ExpenseTab.POLICY_LIMITS, viewModel.state.value.selectedTab)
    }

    @Test
    fun `addLine and removeLine manage draft lines properly`() = runTest {
        assertEquals(1, viewModel.state.value.draftLines.size)

        viewModel.addLine()
        assertEquals(2, viewModel.state.value.draftLines.size)

        val firstLineId = viewModel.state.value.draftLines.first().id
        viewModel.removeLine(firstLineId)
        assertEquals(1, viewModel.state.value.draftLines.size)

        // Cannot remove the last remaining line
        val remainingLineId = viewModel.state.value.draftLines.first().id
        viewModel.removeLine(remainingLineId)
        assertEquals(1, viewModel.state.value.draftLines.size)
    }

    @Test
    fun `updateLine updates line fields`() = runTest {
        val lineId = viewModel.state.value.draftLines.first().id
        viewModel.updateLine(lineId) {
            it.copy(
                merchantName = "Hilton Colombo",
                amount = "32500.00",
                description = "Executive Accommodation",
            )
        }

        val updated = viewModel.state.value.draftLines.first()
        assertEquals("Hilton Colombo", updated.merchantName)
        assertEquals("32500.00", updated.amount)
        assertEquals("Executive Accommodation", updated.description)
    }

    @Test
    fun `simulateReceiptScan performs OCR and autofills line fields`() = runTest {
        val lineId = viewModel.state.value.draftLines.first().id
        val ocrResult = ReceiptOcrResponse(
            merchantName = "Hilton Colombo",
            expenseDate = LocalDate.now(),
            amount = BigDecimal("32500.00"),
            currency = "LKR",
            suggestedCategoryCode = "MEALS",
            confidence = BigDecimal("0.95"),
            rawExtractedText = "Hilton invoice",
        )

        coEvery { repository.scanReceiptOcr(any(), any(), any()) } returns Result.success(ocrResult)

        viewModel.simulateReceiptScan(lineId, "HOTEL")
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = viewModel.state.value.draftLines.first()
        assertEquals("Hilton Colombo", updated.merchantName)
        assertEquals("32500.00", updated.amount)
        assertFalse(updated.isScanningOcr)
        assertNotNull(updated.receiptFileName)
    }

    @Test
    fun `submitClaim validates empty title`() = runTest {
        viewModel.setDraftTitle("")
        viewModel.submitClaim()

        assertEquals("Please provide a title for the expense claim.", viewModel.state.value.error)
        coVerify(exactly = 0) { repository.submitClaim(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `submitClaim validates zero or missing amount`() = runTest {
        viewModel.setDraftTitle("Trip to Galle")
        val lineId = viewModel.state.value.draftLines.first().id
        viewModel.updateLine(lineId) { it.copy(amount = "0.00", categoryId = sampleCategory.id) }

        viewModel.submitClaim()
        assertTrue(viewModel.state.value.error!!.contains("Enter a valid amount"))
        coVerify(exactly = 0) { repository.submitClaim(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `submitClaim succeeds when inputs are valid`() = runTest {
        viewModel.setDraftTitle("Trip to Galle")
        viewModel.setDraftRemarks("Client meeting")
        val lineId = viewModel.state.value.draftLines.first().id
        viewModel.updateLine(lineId) {
            it.copy(
                categoryId = sampleCategory.id,
                amount = "7500.00",
                description = "Lunch with client",
                merchantName = "Keells Super",
            )
        }

        val createdClaim = sampleClaim.copy(title = "Trip to Galle", totalAmount = BigDecimal("7500.00"))
        val createdDetail = ExpenseClaimDetailResponse(claim = createdClaim, lines = emptyList())
        coEvery { repository.submitClaim(any(), any(), any(), any(), any()) } returns Result.success(createdDetail)

        viewModel.submitClaim()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSubmitting)
        assertEquals(ExpenseTab.MY_CLAIMS, viewModel.state.value.selectedTab)
        assertNotNull(viewModel.state.value.actionMessage)
        assertTrue(viewModel.state.value.actionMessage!!.contains("EXP-2026-0042"))
    }

    @Test
    fun `cancel dialog workflow cancels claim`() = runTest {
        viewModel.openCancelDialog(sampleClaim)
        assertTrue(viewModel.state.value.isCancelDialogOpen)
        assertEquals(sampleClaim, viewModel.state.value.claimToCancel)

        viewModel.setCancelReason("Duplicate claim")
        val cancelled = sampleClaim.copy(status = ExpenseClaimItem.Status.CANCELLED)
        coEvery { repository.cancelClaim(sampleClaim.id.toString(), "Duplicate claim") } returns Result.success(cancelled)

        viewModel.confirmCancelClaim()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isCancelDialogOpen)
        assertNull(viewModel.state.value.claimToCancel)
        assertNotNull(viewModel.state.value.actionMessage)
        assertTrue(viewModel.state.value.actionMessage!!.contains("successfully cancelled"))
    }

    @Test
    fun `openClaimDetails and closeClaimDetails manage detail modal state`() = runTest {
        viewModel.openClaimDetails(sampleClaim)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.isDetailModalOpen)
        coVerify { repository.fetchClaimDetails(sampleClaim.id.toString()) }

        viewModel.closeClaimDetails()
        assertFalse(viewModel.state.value.isDetailModalOpen)
        coVerify { repository.clearSelectedClaimDetail() }
    }
}
