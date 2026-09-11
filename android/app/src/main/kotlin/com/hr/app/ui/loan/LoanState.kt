package com.hr.app.ui.loan

import com.hr.client.model.EmployeeLoanDetailResponse
import com.hr.client.model.EmployeeLoanItem
import com.hr.client.model.LoanEligibilityResponse
import com.hr.client.model.LoanTypeItem
import java.util.UUID

enum class LoanTab {
    MY_LOANS,
    APPLY_LOAN,
    LOAN_PRODUCTS,
}

data class LoanState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,
    val selectedTab: LoanTab = LoanTab.MY_LOANS,
    val pendingOutboxCount: Int = 0,

    // Loan Products
    val loanTypes: List<LoanTypeItem> = emptyList(),

    // Employee Loans
    val loans: List<EmployeeLoanItem> = emptyList(),
    val selectedLoan: EmployeeLoanItem? = null,
    val selectedLoanDetail: EmployeeLoanDetailResponse? = null,
    val isScheduleModalOpen: Boolean = false,
    val scheduleLoading: Boolean = false,

    // Application Form State
    val applyLoanTypeId: UUID? = null,
    val applyPrincipal: String = "30000",
    val applyTenureMonths: Int = 6,
    val applyReason: String = "",
    val eligibilityLoading: Boolean = false,
    val eligibility: LoanEligibilityResponse? = null,
    val isSubmittingApplication: Boolean = false,

    // Settlement Modal State
    val loanToSettle: EmployeeLoanItem? = null,
    val isSettlementDialogOpen: Boolean = false,
    val settlementNotes: String = "",
    val isSettling: Boolean = false,
)
