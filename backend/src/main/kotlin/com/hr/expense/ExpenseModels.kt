package com.hr.expense

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Lifecycle status of an expense reimbursement claim.
 */
enum class ExpenseClaimStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    REIMBURSED,
    CANCELLED,
}

/**
 * Status of an individual line item in an expense claim.
 */
enum class ExpenseLineStatus {
    PENDING,
    APPROVED,
    REJECTED,
}

// ---------------------------------------------------------------------------
// DTOs matching the OpenAPI spec contract
// ---------------------------------------------------------------------------

data class ExpenseCategoryItemResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val description: String? = null,
    val glCode: String? = null,
    val requiresReceipt: Boolean,
    val maxAmountPerClaim: BigDecimal? = null,
    val ratePerUnit: BigDecimal? = null,
    val unitName: String? = null,
    val isActive: Boolean,
)

data class ExpenseCategoriesResponse(
    val categories: List<ExpenseCategoryItemResponse>,
)

data class ExpenseClaimLineItemResponse(
    val id: UUID,
    val categoryId: UUID,
    val categoryCode: String,
    val categoryName: String,
    val expenseDate: LocalDate,
    val description: String,
    val merchantName: String? = null,
    val unitQuantity: BigDecimal? = null,
    val amount: BigDecimal,
    val receiptKey: String? = null,
    val receiptUrl: String? = null,
    val status: ExpenseLineStatus,
    val approvedAmount: BigDecimal? = null,
    val rejectionReason: String? = null,
    val ocrExtractedSummary: String? = null,
)

data class ExpenseClaimLineInput(
    val categoryId: UUID,
    val expenseDate: LocalDate,
    val description: String,
    val merchantName: String? = null,
    val unitQuantity: BigDecimal? = null,
    val amount: BigDecimal,
    val receiptKey: String? = null,
    val receiptUrl: String? = null,
)

data class ExpenseClaimItemResponse(
    val id: UUID,
    val claimNumber: String,
    val title: String,
    val claimDate: LocalDate,
    val currency: String,
    val totalAmount: BigDecimal,
    val approvedAmount: BigDecimal? = null,
    val status: ExpenseClaimStatus,
    val lineCount: Int,
    val rejectionReason: String? = null,
    val approvedAt: Instant? = null,
    val reimbursedAt: Instant? = null,
    val remarks: String? = null,
)

data class ExpenseClaimsResponse(
    val totalClaimedAmount: BigDecimal,
    val totalApprovedAmount: BigDecimal,
    val totalReimbursedAmount: BigDecimal,
    val pendingCount: Int,
    val claims: List<ExpenseClaimItemResponse>,
)

data class ExpenseClaimDetailResponse(
    val claim: ExpenseClaimItemResponse,
    val lines: List<ExpenseClaimLineItemResponse>,
)

data class ExpenseClaimSubmitRequest(
    val title: String,
    val claimDate: LocalDate,
    val currency: String = "LKR",
    val remarks: String? = null,
    val lines: List<ExpenseClaimLineInput>,
)

data class CancelExpenseClaimRequest(
    val cancellationReason: String? = null,
)

data class ReceiptOcrRequest(
    val receiptImageBase64: String? = null,
    val receiptText: String? = null,
    val fileName: String? = null,
)

data class ReceiptOcrResponse(
    val merchantName: String? = null,
    val expenseDate: LocalDate? = null,
    val amount: BigDecimal? = null,
    val currency: String? = null,
    val suggestedCategoryCode: String? = null,
    val confidence: Double,
    val rawExtractedText: String? = null,
)
