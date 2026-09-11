package com.hr.app.ui.expense

import com.hr.client.model.ExpenseCategoryItem
import com.hr.client.model.ExpenseClaimDetailResponse
import com.hr.client.model.ExpenseClaimItem
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class ExpenseTab {
    MY_CLAIMS,
    SUBMIT_CLAIM,
    POLICY_LIMITS,
}

data class DraftLineItem(
    val id: UUID = UUID.randomUUID(),
    val categoryId: UUID? = null,
    val expenseDate: LocalDate = LocalDate.now(),
    val description: String = "",
    val merchantName: String = "",
    val unitQuantity: String = "",
    val amount: String = "",
    val receiptFileName: String? = null,
    val receiptUrl: String? = null,
    val isScanningOcr: Boolean = false,
)

data class ExpenseState(
    val selectedTab: ExpenseTab = ExpenseTab.MY_CLAIMS,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,

    val categories: List<ExpenseCategoryItem> = emptyList(),
    val claims: List<ExpenseClaimItem> = emptyList(),
    val totalClaimedAmount: BigDecimal = BigDecimal.ZERO,
    val totalApprovedAmount: BigDecimal = BigDecimal.ZERO,
    val totalReimbursedAmount: BigDecimal = BigDecimal.ZERO,
    val pendingCount: Int = 0,

    val selectedClaimDetail: ExpenseClaimDetailResponse? = null,
    val isDetailModalOpen: Boolean = false,

    // Form submission state
    val draftTitle: String = "",
    val draftClaimDate: LocalDate = LocalDate.now(),
    val draftCurrency: String = "LKR",
    val draftRemarks: String = "",
    val draftLines: List<DraftLineItem> = listOf(DraftLineItem()),

    // Cancel claim dialog state
    val claimToCancel: ExpenseClaimItem? = null,
    val cancelReason: String = "",
    val isCancelDialogOpen: Boolean = false,

    // Filtering
    val selectedStatusFilter: String? = null,
)
