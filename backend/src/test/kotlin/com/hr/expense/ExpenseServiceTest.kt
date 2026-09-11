package com.hr.expense

import com.hr.expense.internal.*
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@DisplayName("Expense Service Unit Tests")
class ExpenseServiceTest {

    private val categoryRepository = mockk<ExpenseCategoryRepository>()
    private val claimRepository = mockk<ExpenseClaimRepository>()
    private val lineRepository = mockk<ExpenseClaimLineRepository>()
    private val ocrService = mockk<ExpenseOcrService>()

    private val expenseService = ExpenseService(
        categoryRepository = categoryRepository,
        claimRepository = claimRepository,
        lineRepository = lineRepository,
        ocrService = ocrService,
    )

    private val mealsCategory = ExpenseCategory(
        code = "MEALS",
        name = "Meals & Subsistence",
        description = "Meal expenses while on official business",
        requiresReceipt = true,
        maxAmountPerClaim = BigDecimal("10000.00"),
        ratePerUnit = null,
        unitName = null,
        isActive = true,
    )

    private val mileageCategory = ExpenseCategory(
        code = "TRAVEL_MILEAGE",
        name = "Personal Vehicle Mileage",
        description = "Reimbursement for private vehicle usage",
        requiresReceipt = false,
        maxAmountPerClaim = BigDecimal("25000.00"),
        ratePerUnit = BigDecimal("100.00"),
        unitName = "km",
        isActive = true,
    )

    @Test
    fun `getExpenseCategories returns active categories`() {
        every { categoryRepository.findAllByIsActiveTrueOrderByCodeAsc() } returns listOf(mealsCategory, mileageCategory)

        val response = expenseService.getExpenseCategories()
        assertThat(response.categories).hasSize(2)
        assertThat(response.categories.map { it.code }).containsExactly("MEALS", "TRAVEL_MILEAGE")
    }

    @Test
    fun `submitExpenseClaim creates claim and lines and computes total`() {
        val employeeId = UUID.randomUUID()
        every { categoryRepository.findAll() } returns listOf(mealsCategory, mileageCategory)

        val savedClaim = ExpenseClaim(
            employeeId = employeeId,
            claimNumber = "EXP-2026-TEST01",
            title = "Client visit to Colombo",
            claimDate = LocalDate.now(),
            currency = "LKR",
            totalAmount = BigDecimal("5500.00"),
            status = ExpenseClaimStatus.SUBMITTED,
        )

        every { claimRepository.save(any()) } returns savedClaim

        val savedLine1 = ExpenseClaimLine(
            claimId = savedClaim.id,
            categoryId = mealsCategory.id,
            expenseDate = LocalDate.now(),
            description = "Lunch with client",
            merchantName = "Cinnamon Grand",
            amount = BigDecimal("3500.00"),
        )

        val savedLine2 = ExpenseClaimLine(
            claimId = savedClaim.id,
            categoryId = mileageCategory.id,
            expenseDate = LocalDate.now(),
            description = "Travel to meeting",
            unitQuantity = BigDecimal("20.00"),
            amount = BigDecimal("2000.00"),
        )

        every { lineRepository.save(any()) } returnsMany listOf(savedLine1, savedLine2)

        val request = ExpenseClaimSubmitRequest(
            title = "Client visit to Colombo",
            claimDate = LocalDate.now(),
            currency = "LKR",
            lines = listOf(
                ExpenseClaimLineInput(
                    categoryId = mealsCategory.id,
                    expenseDate = LocalDate.now(),
                    description = "Lunch with client",
                    merchantName = "Cinnamon Grand",
                    amount = BigDecimal("3500.00"),
                ),
                ExpenseClaimLineInput(
                    categoryId = mileageCategory.id,
                    expenseDate = LocalDate.now(),
                    description = "Travel to meeting",
                    unitQuantity = BigDecimal("20.00"),
                    amount = BigDecimal("2000.00"),
                ),
            ),
        )

        val result = expenseService.submitExpenseClaim(employeeId, request)
        assertThat(result.claim.id).isEqualTo(savedClaim.id)
        assertThat(result.lines).hasSize(2)
        assertThat(result.lines[0].categoryCode).isEqualTo("MEALS")
        assertThat(result.lines[1].categoryCode).isEqualTo("TRAVEL_MILEAGE")
    }

    @Test
    fun `submitExpenseClaim throws exception when amount exceeds category max limit`() {
        val employeeId = UUID.randomUUID()
        every { categoryRepository.findAll() } returns listOf(mealsCategory)

        val request = ExpenseClaimSubmitRequest(
            title = "Excessive meal",
            claimDate = LocalDate.now(),
            lines = listOf(
                ExpenseClaimLineInput(
                    categoryId = mealsCategory.id,
                    expenseDate = LocalDate.now(),
                    description = "Fine dining",
                    amount = BigDecimal("15000.00"), // Max is 10,000.00
                ),
            ),
        )

        assertThatThrownBy {
            expenseService.submitExpenseClaim(employeeId, request)
        }.hasMessageContaining("exceeds maximum allowed of 10000.00")
    }

    @Test
    fun `cancelExpenseClaim updates submitted claim status to CANCELLED`() {
        val employeeId = UUID.randomUUID()

        val existingClaim = ExpenseClaim(
            employeeId = employeeId,
            claimNumber = "EXP-2026-TEST02",
            title = "Trip cancelled",
            claimDate = LocalDate.now(),
            totalAmount = BigDecimal("3000.00"),
            status = ExpenseClaimStatus.SUBMITTED,
        )

        every { claimRepository.findByIdAndEmployeeId(existingClaim.id, employeeId) } returns existingClaim
        every { claimRepository.save(existingClaim) } returns existingClaim
        every { lineRepository.countByClaimId(existingClaim.id) } returns 1

        val result = expenseService.cancelExpenseClaim(employeeId, existingClaim.id, "Meeting postponed")
        assertThat(result.status).isEqualTo(ExpenseClaimStatus.CANCELLED)
        assertThat(existingClaim.status).isEqualTo(ExpenseClaimStatus.CANCELLED)
    }

    @Test
    fun `cancelExpenseClaim rejects cancellation of APPROVED claim`() {
        val employeeId = UUID.randomUUID()

        val existingClaim = ExpenseClaim(
            employeeId = employeeId,
            claimNumber = "EXP-2026-TEST03",
            title = "Already approved",
            claimDate = LocalDate.now(),
            totalAmount = BigDecimal("3000.00"),
            status = ExpenseClaimStatus.APPROVED,
        )

        every { claimRepository.findByIdAndEmployeeId(existingClaim.id, employeeId) } returns existingClaim

        assertThatThrownBy {
            expenseService.cancelExpenseClaim(employeeId, existingClaim.id, "No longer needed")
        }.hasMessageContaining("Cannot cancel expense claim in 'APPROVED' state")
    }
}
