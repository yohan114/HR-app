package com.hr.expense

import com.hr.expense.internal.ExpenseController
import com.hr.expense.internal.ExpenseService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Expense Controller Unit Tests")
class ExpenseControllerTest {

    private val expenseService = mockk<ExpenseService>(relaxed = true)
    private val controller = ExpenseController(expenseService)

    @Test
    fun `getExpenseCategories delegates to service`() {
        val sampleCategory = ExpenseCategoryItemResponse(
            id = UUID.randomUUID(),
            code = "MEALS",
            name = "Meals & Subsistence",
            requiresReceipt = true,
            isActive = true,
        )

        every { expenseService.getExpenseCategories() } returns ExpenseCategoriesResponse(listOf(sampleCategory))

        val response = controller.getExpenseCategories()
        assertThat(response.categories).hasSize(1)
        assertThat(response.categories.first().code).isEqualTo("MEALS")
    }

    @Test
    fun `getMyExpenseClaims delegates to service`() {
        val sampleClaim = ExpenseClaimItemResponse(
            id = UUID.randomUUID(),
            claimNumber = "EXP-2026-0001",
            title = "Travel expenses",
            claimDate = LocalDate.now(),
            currency = "LKR",
            totalAmount = BigDecimal("15000.00"),
            status = ExpenseClaimStatus.SUBMITTED,
            lineCount = 3,
        )

        every { expenseService.getMyExpenseClaims(any(), any()) } returns ExpenseClaimsResponse(
            totalClaimedAmount = BigDecimal("15000.00"),
            totalApprovedAmount = BigDecimal.ZERO,
            totalReimbursedAmount = BigDecimal.ZERO,
            pendingCount = 1,
            claims = listOf(sampleClaim),
        )

        val response = controller.getMyExpenseClaims(null, null)
        assertThat(response.claims).hasSize(1)
        assertThat(response.claims.first().claimNumber).isEqualTo("EXP-2026-0001")
        assertThat(response.pendingCount).isEqualTo(1)
    }

    @Test
    fun `scanReceiptOcr delegates to service`() {
        val ocrRes = ReceiptOcrResponse(
            merchantName = "Hilton Colombo",
            expenseDate = LocalDate.now(),
            amount = BigDecimal("25000.00"),
            suggestedCategoryCode = "HOTEL",
            confidence = 0.95,
        )

        every { expenseService.scanReceiptOcr(any()) } returns ocrRes

        val response = controller.scanReceiptOcr(ReceiptOcrRequest(receiptText = "Hilton bill"))
        assertThat(response.merchantName).isEqualTo("Hilton Colombo")
        assertThat(response.suggestedCategoryCode).isEqualTo("HOTEL")
        assertThat(response.confidence).isEqualTo(0.95)
    }
}
