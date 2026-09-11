package com.hr.app.ui.lifecycle

import com.hr.client.model.CareerMovementItem
import java.time.LocalDate
import java.util.UUID

enum class LifecycleTab {
    TIMELINE,
    MOVEMENTS,
}

data class LifecycleUiState(
    val activeTab: LifecycleTab = LifecycleTab.TIMELINE,
    val isLoading: Boolean = false,
    val errorBanner: String? = null,
    val successBanner: String? = null,
    val selectedMovementId: UUID? = null,
    val movementTypeFilter: CareerMovementItem.MovementType? = null,
    val movementStatusFilter: CareerMovementItem.Status? = null,

    // Movement proposal form dialog state
    val showProposalDialog: Boolean = false,
    val isSubmittingProposal: Boolean = false,
    val proposalMovementType: CareerMovementItem.MovementType = CareerMovementItem.MovementType.PROMOTION,
    val proposalEffectiveDate: LocalDate = LocalDate.now().plusMonths(1),
    val proposalJustification: String = "",
    val proposalDepartmentName: String = "Engineering",
    val proposalDesignationName: String = "",
    val proposalSalaryGradeCode: String = "M1",
    val proposalBaseSalary: String = "",
    val proposalCurrency: String = "LKR",
    val proposalRemarks: String = "",

    // Movement revert dialog state
    val showRevertDialog: Boolean = false,
    val revertingMovementId: UUID? = null,
    val revertReasonText: String = "",
    val isSubmittingRevert: Boolean = false,
)
