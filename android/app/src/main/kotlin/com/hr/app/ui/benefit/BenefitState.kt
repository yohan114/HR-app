package com.hr.app.ui.benefit

import java.time.LocalDate
import java.util.UUID

enum class BenefitTab {
    MY_BENEFITS,
    CLAIMS,
    CATALOGUE,
}

data class BenefitUiState(
    val activeTab: BenefitTab = BenefitTab.MY_BENEFITS,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val isCancelling: Boolean = false,
    val showClaimDialog: Boolean = false,
    val selectedEnrollmentId: UUID? = null,
    val selectedDependentId: UUID? = null,
    val claimDate: LocalDate = LocalDate.now(),
    val serviceProvider: String = "",
    val diagnosisOrReason: String = "",
    val invoiceNumber: String = "",
    val claimedAmount: String = "",
    val remarks: String = "",
    val receiptUrl: String? = null,
    val receiptKey: String? = null,
    val simulatedReceiptName: String? = null,
    val statusFilter: String? = null,
    val selectedCategoryFilter: String? = null,
    val searchQuery: String = "",
    val errorBanner: String? = null,
    val successBanner: String? = null,
)
