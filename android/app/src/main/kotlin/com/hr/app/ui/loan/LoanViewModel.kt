package com.hr.app.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.loan.LoanRepository
import com.hr.client.model.EmployeeLoanItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LoanViewModel
    @Inject
    constructor(
        private val repository: LoanRepository,
    ) : ViewModel() {

        private val _state = MutableStateFlow(LoanState())
        val state: StateFlow<LoanState> = _state.asStateFlow()

        init {
            observeRepository()
            refresh()
        }

        private fun observeRepository() {
            viewModelScope.launch {
                repository.loanTypes.collect { types ->
                    _state.update { current ->
                        val defaultTypeId = current.applyLoanTypeId ?: types.firstOrNull()?.id
                        current.copy(
                            loanTypes = types,
                            applyLoanTypeId = defaultTypeId,
                        )
                    }
                }
            }

            viewModelScope.launch {
                repository.loans.collect { loans ->
                    _state.update { it.copy(loans = loans) }
                }
            }

            viewModelScope.launch {
                repository.selectedLoanDetail.collect { detail ->
                    _state.update { it.copy(selectedLoanDetail = detail) }
                }
            }

            viewModelScope.launch {
                repository.eligibilityResult.collect { result ->
                    _state.update { it.copy(eligibility = result, eligibilityLoading = false) }
                }
            }

            viewModelScope.launch {
                repository.pendingOutboxCount.collect { count ->
                    _state.update { it.copy(pendingOutboxCount = count) }
                }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                _state.update { it.copy(refreshing = true, error = null) }
                val typesResult = repository.refreshLoanTypes()
                val loansResult = repository.refreshLoans()

                val err = typesResult.exceptionOrNull()?.message ?: loansResult.exceptionOrNull()?.message
                _state.update { it.copy(refreshing = false, error = err) }

                // Trigger initial eligibility simulation if types are loaded
                checkEligibility()
            }
        }

        fun setTab(tab: LoanTab) {
            _state.update { it.copy(selectedTab = tab, error = null) }
            if (tab == LoanTab.APPLY_LOAN) {
                checkEligibility()
            }
        }

        fun openLoanSchedule(loan: EmployeeLoanItem) {
            _state.update {
                it.copy(
                    selectedLoan = loan,
                    isScheduleModalOpen = true,
                    scheduleLoading = true,
                )
            }
            viewModelScope.launch {
                repository.fetchLoanDetails(loan.id.toString())
                _state.update { it.copy(scheduleLoading = false) }
            }
        }

        fun closeLoanSchedule() {
            _state.update {
                it.copy(
                    isScheduleModalOpen = false,
                    selectedLoan = null,
                    selectedLoanDetail = null,
                )
            }
        }

        fun setApplyLoanType(typeId: UUID) {
            _state.update { it.copy(applyLoanTypeId = typeId) }
            checkEligibility()
        }

        fun setApplyPrincipal(principal: String) {
            _state.update { it.copy(applyPrincipal = principal) }
            checkEligibility()
        }

        fun setApplyTenureMonths(tenure: Int) {
            _state.update { it.copy(applyTenureMonths = tenure) }
            checkEligibility()
        }

        fun setApplyReason(reason: String) {
            _state.update { it.copy(applyReason = reason) }
        }

        fun checkEligibility() {
            val currentState = _state.value
            val typeId = currentState.applyLoanTypeId ?: return
            val principal = currentState.applyPrincipal.toBigDecimalOrNull() ?: return
            if (principal <= BigDecimal.ZERO) return
            val tenure = currentState.applyTenureMonths
            if (tenure <= 0) return

            _state.update { it.copy(eligibilityLoading = true) }
            viewModelScope.launch {
                repository.checkEligibility(typeId, principal, tenure)
            }
        }

        fun submitLoanApplication() {
            val currentState = _state.value
            val typeId = currentState.applyLoanTypeId ?: run {
                _state.update { it.copy(error = "Please select a loan type.") }
                return
            }
            val principal = currentState.applyPrincipal.toBigDecimalOrNull() ?: run {
                _state.update { it.copy(error = "Please enter a valid principal amount.") }
                return
            }
            if (principal <= BigDecimal.ZERO) {
                _state.update { it.copy(error = "Principal amount must be greater than zero.") }
                return
            }
            val tenure = currentState.applyTenureMonths
            val reason = currentState.applyReason.ifBlank { "Personal Financial Assistance" }

            _state.update { it.copy(isSubmittingApplication = true, error = null) }

            viewModelScope.launch {
                val result = repository.submitLoanApplication(
                    loanTypeId = typeId,
                    principalAmount = principal,
                    tenureMonths = tenure,
                    reason = reason,
                )

                result.fold(
                    onSuccess = { detail ->
                        _state.update {
                            it.copy(
                                isSubmittingApplication = false,
                                selectedTab = LoanTab.MY_LOANS,
                                actionMessage = "Loan application ${detail.loan.loanCode} submitted and active!",
                                applyReason = "",
                            )
                        }
                    },
                    onFailure = { err ->
                        _state.update {
                            it.copy(
                                isSubmittingApplication = false,
                                error = err.message ?: "Failed to submit loan application.",
                            )
                        }
                    },
                )
            }
        }

        fun openSettlementDialog(loan: EmployeeLoanItem) {
            _state.update {
                it.copy(
                    loanToSettle = loan,
                    isSettlementDialogOpen = true,
                    settlementNotes = "",
                )
            }
        }

        fun closeSettlementDialog() {
            _state.update {
                it.copy(
                    loanToSettle = null,
                    isSettlementDialogOpen = false,
                    settlementNotes = "",
                )
            }
        }

        fun setSettlementNotes(notes: String) {
            _state.update { it.copy(settlementNotes = notes) }
        }

        fun confirmSettlement() {
            val loan = _state.value.loanToSettle ?: return
            _state.update { it.copy(isSettling = true, error = null) }

            viewModelScope.launch {
                val result = repository.requestSettlement(
                    loanId = loan.id.toString(),
                    notes = _state.value.settlementNotes.ifBlank { null },
                )

                result.fold(
                    onSuccess = { res ->
                        _state.update {
                            it.copy(
                                isSettling = false,
                                isSettlementDialogOpen = false,
                                loanToSettle = null,
                                actionMessage = "Loan settled successfully! Outstanding principal paid: LKR ${res.settlementAmountPaid.toPlainString()}",
                            )
                        }
                        // If detail modal is currently showing this loan, refresh it
                        if (_state.value.selectedLoan?.id == loan.id) {
                            closeLoanSchedule()
                        }
                    },
                    onFailure = { err ->
                        _state.update {
                            it.copy(
                                isSettling = false,
                                error = err.message ?: "Failed to settle loan early.",
                            )
                        }
                    },
                )
            }
        }

        fun clearActionMessage() {
            _state.update { it.copy(actionMessage = null) }
        }

        fun clearError() {
            _state.update { it.copy(error = null) }
        }
    }
