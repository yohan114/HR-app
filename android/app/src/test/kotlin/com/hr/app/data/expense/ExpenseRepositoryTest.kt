package com.hr.app.data.expense

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.ExpensesApi
import com.hr.client.model.CancelExpenseClaimRequest
import com.hr.client.model.ExpenseCategoriesResponse
import com.hr.client.model.ExpenseCategoryItem
import com.hr.client.model.ExpenseClaimDetailResponse
import com.hr.client.model.ExpenseClaimItem
import com.hr.client.model.ExpenseClaimLineInput
import com.hr.client.model.ExpenseClaimLineItem
import com.hr.client.model.ExpenseClaimSubmitRequest
import com.hr.client.model.ExpenseClaimsResponse
import com.hr.client.model.ReceiptOcrRequest
import com.hr.client.model.ReceiptOcrResponse
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
import java.util.UUID

class ExpenseRepositoryTest {

    private val expensesApi = mockk<ExpensesApi>()
    private val outbox = mockk<Outbox>(relaxed = true)
    private val clock = Clock { 1772870000000L }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val pendingCountFlow = MutableStateFlow(0)
    private lateinit var repository: ExpenseRepository

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
        claimNumber = "EXP-2026-0099",
        title = "Client Tech Summit",
        claimDate = LocalDate.now(),
        currency = "LKR",
        totalAmount = BigDecimal("17500.00"),
        status = ExpenseClaimItem.Status.SUBMITTED,
        lineCount = 2,
    )

    @Before
    fun setUp() {
        coEvery { outbox.pendingCount } returns pendingCountFlow
        repository = ExpenseRepository(expensesApi, outbox, json, clock)
    }

    @Test
    fun `refreshCategories updates stateFlow on success`() = runTest {
        coEvery { expensesApi.getExpenseCategories() } returns Response.success(
            ExpenseCategoriesResponse(listOf(sampleCategory)),
        )

        val result = repository.refreshCategories()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.categories.value.size)
        assertEquals("MEALS", repository.categories.value.first().code)
    }

    @Test
    fun `refreshCategories falls back to offline default categories on network error`() = runTest {
        coEvery { expensesApi.getExpenseCategories() } throws RuntimeException("Network down")

        val result = repository.refreshCategories()
        assertTrue(result.isFailure)
        assertTrue(repository.categories.value.isNotEmpty())
        assertTrue(repository.categories.value.any { it.code == "TRAVEL_MILEAGE" })
    }

    @Test
    fun `refreshClaims updates stateFlow on success`() = runTest {
        coEvery { expensesApi.getMyExpenseClaims(null) } returns Response.success(
            ExpenseClaimsResponse(
                totalClaimedAmount = BigDecimal("50000.00"),
                totalApprovedAmount = BigDecimal("35000.00"),
                totalReimbursedAmount = BigDecimal("35000.00"),
                pendingCount = 1,
                claims = listOf(sampleClaim),
            ),
        )

        val result = repository.refreshClaims()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.claims.value.size)
        assertEquals("EXP-2026-0099", repository.claims.value.first().claimNumber)
        assertEquals(BigDecimal("50000.00"), repository.totalClaimedAmount.value)
        assertEquals(1, repository.pendingCount.value)
    }

    @Test
    fun `refreshClaims falls back to offline defaults on failure`() = runTest {
        coEvery { expensesApi.getMyExpenseClaims(any()) } throws RuntimeException("Connection timeout")

        val result = repository.refreshClaims("SUBMITTED")
        assertTrue(result.isFailure)
        assertTrue(repository.claims.value.isNotEmpty())
        assertTrue(repository.totalClaimedAmount.value > BigDecimal.ZERO)
    }

    @Test
    fun `fetchClaimDetails returns particulars and sets selected state`() = runTest {
        val sampleLine = ExpenseClaimLineItem(
            id = UUID.randomUUID(),
            categoryId = sampleCategory.id,
            categoryCode = sampleCategory.code,
            categoryName = sampleCategory.name,
            expenseDate = LocalDate.now(),
            description = "Dinner with client",
            amount = BigDecimal("7500.00"),
            status = ExpenseClaimLineItem.Status.PENDING,
        )
        val detailResponse = ExpenseClaimDetailResponse(
            claim = sampleClaim,
            lines = listOf(sampleLine),
        )

        coEvery { expensesApi.getMyExpenseClaimDetails(sampleClaim.id.toString()) } returns Response.success(detailResponse)

        val result = repository.fetchClaimDetails(sampleClaim.id.toString())
        assertTrue(result.isSuccess)
        assertNotNull(repository.selectedClaimDetail.value)
        assertEquals(1, repository.selectedClaimDetail.value?.lines?.size)
        assertEquals("Dinner with client", repository.selectedClaimDetail.value?.lines?.first()?.description)
    }

    @Test
    fun `submitClaim succeeds with online response`() = runTest {
        val lineInput = ExpenseClaimLineInput(
            categoryId = sampleCategory.id,
            expenseDate = LocalDate.now(),
            description = "Workshop supplies",
            amount = BigDecimal("4500.00"),
        )
        val createdClaim = sampleClaim.copy(totalAmount = BigDecimal("4500.00"))
        val createdDetail = ExpenseClaimDetailResponse(
            claim = createdClaim,
            lines = listOf(
                ExpenseClaimLineItem(
                    id = UUID.randomUUID(),
                    categoryId = sampleCategory.id,
                    categoryCode = sampleCategory.code,
                    categoryName = sampleCategory.name,
                    expenseDate = LocalDate.now(),
                    description = "Workshop supplies",
                    amount = BigDecimal("4500.00"),
                    status = ExpenseClaimLineItem.Status.PENDING,
                ),
            ),
        )

        coEvery { expensesApi.submitExpenseClaim(any(), any()) } returns Response.success(createdDetail)

        val result = repository.submitClaim(
            title = "Workshop Supplies",
            claimDate = LocalDate.now(),
            currency = "LKR",
            remarks = "Office supplies",
            lines = listOf(lineInput),
        )

        assertTrue(result.isSuccess)
        assertEquals(createdClaim.claimNumber, result.getOrNull()?.claim?.claimNumber)
        assertTrue(repository.claims.value.any { it.claimNumber == createdClaim.claimNumber })
    }

    @Test
    fun `cancelClaim sends cancel request and updates status`() = runTest {
        val cancelled = sampleClaim.copy(status = ExpenseClaimItem.Status.CANCELLED)
        coEvery { expensesApi.cancelExpenseClaim(sampleClaim.id.toString(), any(), any()) } returns Response.success(cancelled)

        val result = repository.cancelClaim(sampleClaim.id.toString(), "Entered by mistake")
        assertTrue(result.isSuccess)
        assertEquals(ExpenseClaimItem.Status.CANCELLED, result.getOrNull()?.status)
    }

    @Test
    fun `scanReceiptOcr parses extracted text and returns mock response`() = runTest {
        val ocrResponse = ReceiptOcrResponse(
            merchantName = "Hilton Colombo",
            expenseDate = LocalDate.now(),
            amount = BigDecimal("32500.00"),
            currency = "LKR",
            suggestedCategoryCode = "HOTEL",
            confidence = BigDecimal("0.95"),
            rawExtractedText = "Hilton Colombo Invoice Total LKR 32500.00",
        )

        coEvery { expensesApi.scanReceiptOcr(any()) } returns Response.success(ocrResponse)

        val result = repository.scanReceiptOcr(
            receiptText = "Hilton Colombo Invoice Total LKR 32500.00",
            fileName = "hotel.jpg",
        )
        assertTrue(result.isSuccess)
        assertEquals("Hilton Colombo", result.getOrNull()?.merchantName)
        assertEquals(BigDecimal("32500.00"), result.getOrNull()?.amount)
        assertEquals("HOTEL", result.getOrNull()?.suggestedCategoryCode)
    }
}
