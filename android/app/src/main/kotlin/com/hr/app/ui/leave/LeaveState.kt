package com.hr.app.ui.leave

import com.hr.client.model.LeaveApplicationItem
import com.hr.client.model.LeaveBalanceItem
import com.hr.client.model.LeaveEligibilityResponse
import com.hr.client.model.LeaveLedgerEntryItem
import java.time.LocalDate

enum class LeaveTab {
    BALANCES_APPLY,
    MY_APPLICATIONS,
    STATEMENT_LEDGER,
}

data class LeaveState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,
    val selectedTab: LeaveTab = LeaveTab.BALANCES_APPLY,
    val pendingOutboxCount: Int = 0,

    // Balances
    val leaveYear: String = "2026",
    val balances: List<LeaveBalanceItem> = emptyList(),
    val selectedBalanceTypeId: String? = null,

    // Applications list
    val applications: List<LeaveApplicationItem> = emptyList(),
    val applicationsFilter: String? = null,
    val applicationsLoading: Boolean = false,

    // Statement Ledger
    val ledger: List<LeaveLedgerEntryItem> = emptyList(),
    val ledgerFilterTypeId: String? = null,
    val ledgerLoading: Boolean = false,

    // Apply Leave Modal State
    val isApplyDialogOpen: Boolean = false,
    val applyLeaveTypeId: String = "",
    val applyStartDate: LocalDate = LocalDate.now().plusDays(1),
    val applyEndDate: LocalDate = LocalDate.now().plusDays(1),
    val applyDayPortion: String = "FULL_DAY",
    val applyReason: String = "",
    val eligibilityLoading: Boolean = false,
    val eligibility: LeaveEligibilityResponse? = null,
    val isSubmittingApplication: Boolean = false,

    // Cancel Leave Modal State
    val applicationToCancel: LeaveApplicationItem? = null,
    val cancelReason: String = "",
    val isCancellingApplication: Boolean = false,
)
